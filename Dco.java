package ru.inversion.utils.dco;

import org.apache.commons.io.input.CharSequenceReader;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import ru.inversion.utils.*;
import ru.inversion.utils.converter.IConverter;
import ru.inversion.utils.ini.IniFileEvent;
import ru.inversion.utils.ini.IniFileEventReader;
import ru.inversion.utils.io.RawBAOS;
import ru.inversion.utils.io.SegmentedBAOS;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.w3c.dom.Node.*;

public class Dco implements IDco {

    /** */
    static private final IConverter<Object,String> STRING_CONVERTER = new IConverter< Object, String >() {
        @Override
        public String to( Object value ) {

            if( value == null )
                return null;

            if( value instanceof String )
                return (String)value ;


            if( value instanceof CharSequence )
                return value.toString();


            if( value instanceof char[] )
                return String.valueOf((char[])value);

            return value.toString();
        }

        @Override
        public Object from( String s ) {
            return s;
        }
    };

    private static final DocumentBuilderFactory FACTORY;

    static {
        try {
            FACTORY = createFactory();
        } catch (ParserConfigurationException e) {
            throw new ExceptionInInitializerError( "Failed to initialize secure XML parser: " + e.getMessage() );
        }
    }

    private static DocumentBuilderFactory createFactory() throws ParserConfigurationException {

        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature( XMLConstants.FEATURE_SECURE_PROCESSING, true );
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (IllegalArgumentException ignored) {
        }

        factory.setNamespaceAware(false);
        factory.setValidating    (false);
        factory.setIgnoringComments(true);

        return factory;
    }

    /** */
    private static DocumentBuilder documentBuilder()
    {
        try {
            return FACTORY.newDocumentBuilder();
        }
        catch( Exception ex ) {
            throw new DcoException( Tags.PRODUCT_LABEL + "Error on create 'DocumentBuilder'", ex );
        }
    }

    // XPath
    static final private Map<String, XPathExpression > cacheXExpr = new ConcurrentHashMap<>();

    /** */
    private static final ThreadLocal<XPath> thXpath = ThreadLocal.withInitial(() -> XPathFactory.newInstance().newXPath() );

    /** */
    private static XPath xpath() {
        return thXpath.get();
    }

    /** */
    public static void cleanupTh() {
        thXpath.remove();
    }

    /** */
    private static class ElementListIterator implements Iterator<Element> {

        final private NodeList nodeList;
        private int index = -1;

        private ElementListIterator( NodeList nodeList ) {
            this.nodeList = Objects.requireNonNull( nodeList, "nodeList' is null");
            _next();
        }

        private void _next()
        {
            do {
                index++;
            }
            while( index < nodeList.getLength() && nodeList.item(index).getNodeType() != ELEMENT_NODE  );
        }

        @Override
        public boolean hasNext() {
            return index < nodeList.getLength();
        }

        @Override
        public Element next() {
            if( hasNext() ) {
                Element ret = (Element)nodeList.item(index);
                _next();
                return ret;
            }
            throw new NoSuchElementException("No more element nodes");
        }
    }

    /** */
    private static class AttrImpl implements IDco {

        final private Attr attr;
        /** */
        private AttrImpl( org.w3c.dom.Attr a ) {
            this.attr = a;
        }

        @Override
        public void set( Object value ) {
            attr.setValue( STRING_CONVERTER.to(value) );
        }

        @Override
        public String getName() {
            return attr.getName();
        }

        @Override
        public boolean isNull() {
            return get() == null;
        }

        @Override
        public IDco getParent( ) {
            IDco dco = (IDco)attr.getOwnerElement().getUserData("dco");
            if( dco == null )
                return new Dco( attr.getOwnerElement() );
            else
                return dco;
        }

        @Override
        public void remove() {
            attr.getOwnerElement().removeAttributeNode( attr );
        }

        @Override
        public Object get() {
            return attr.getValue();
        }

        // public Iterator<IDco> iterator() { return Collections.<IDco>singletonList(this).iterator(); }
        @Override
        public Iterator<IDco> iterator() {
            return Collections.emptyIterator();
        }
    }

    //
    final private Element element;

    //значение элемента узла, не строка
    private volatile Object dcoValue;

    Dco( Element element )
    {
        this.element = element;

        if( this.element.getUserData("dco") != null )
            throw new IllegalStateException("'dco' in node is not null");

        this.element.setUserData( "dco", this, null );
        this.dcoValue = U.callIfNotNull( firstTextNode(), Node::getNodeValue );
    }

    /** */
    public Dco( String rootName )
    {
        this (
            documentBuilder()
                .newDocument()
                    .createElement( Objects.requireNonNull( rootName, "'rootName' is null") )
        );
    }

    @Override
    public Iterable< IDco > attributes( ) {

        final NamedNodeMap attrMap = element.getAttributes();
        if( attrMap.getLength() == 0 )
            return U.iterable( Collections.emptyIterator() );

        Iterator< IDco > iter = new Iterator< IDco >() {
            int index = 0;
            @Override
            public boolean hasNext() {
                return index < attrMap.getLength();
            }
            @Override
            public IDco next() {
                final AttrImpl a = new AttrImpl((Attr)attrMap.item(index));
                index++;
                return a;
            }
        };

        return U.iterable( iter );
    }

    /** */
    @Override
    public boolean hasAttributes() {
        return element.hasAttributes();
    }

    /** */
    @Override
    public boolean hasAttribute( String name ) {
        return element.hasAttribute(name);
    }

    /** */
    @Override
    public synchronized IDco a( String name ) {

        org.w3c.dom.Attr attr = element.getAttributeNode(name);

        if( attr == null ) {
            attr = element.getOwnerDocument().createAttribute(name);
            element.setAttributeNode(attr);
        }

        return new AttrImpl(attr);
    }

    /** */
    public IDco a( String name, Object value ) {
        a(name).set(value);
        return this;
    }

    @Override
    public IDco removeAttribute( String... names ) {
        if( names == null || names.length == 0 )
            return this;
        if( names.length == 1 )
            element.removeAttribute(names[0]);
        else
            Arrays.asList(names).forEach ( element::removeAttribute );

        return this;
    }

    @Override
    public IDco removeAttributes() {
        final NamedNodeMap attrs = element.getAttributes();
        final List< org.w3c.dom.Attr > names = new ArrayList<>( attrs.getLength() );
        IntStream.range( 0, attrs.getLength() ).forEach( i->names.add( (org.w3c.dom.Attr)attrs.item(i) ) );
        names.forEach  ( element::removeAttributeNode );
        return this;
    }

    /** */
    private IDco dco( Element node )
    {
        IDco dco = (IDco)node.getUserData("dco");
        if( dco == null )
            dco = new Dco(node);
        return dco;
    }

    /** */
    private Element getElementByName( String name )
    {
        if( S.isNullOrEmpty(name) )
            return null;

        for( Element e : getElementIter()) {
             if( name.equals( e.getNodeName() ) )
                 return e;
        }

        return null;
    }

    /** */
    private Element getItem( String name, boolean createIfNotExists )
    {
        Element child = null;

        if( element.hasChildNodes() )
        {
            child = getElementByName(name);
            /*
            NodeList list = element.getElementsByTagName(name);
            if( list.getLength() > 0 )
                child = (Element)list.item(0);
             */
        }

        if( child == null && createIfNotExists )
        {
            child = element.getOwnerDocument().createElement(name);
            element.appendChild(child);
        }
        return child;
    }

    /** */
    @Override
    public IDco e( String name ) {
        Element e = getItem(name, true );
        return dco(e);
    }

    /** */
    private Iterable<Element> getElementIter()
    {
        if( !element.hasChildNodes() )
            return U.iterable( Collections.emptyIterator() );
        return U.iterable( new ElementListIterator( element.getChildNodes() ) );
    }

    /** */
    public boolean hasItems(  ) {

        if( !element.hasChildNodes() )
             return false;

        for( Element e : getElementIter() )
             return true;

        return false;
    }

    @Override
    public boolean hasItem( String name ) {
        return element.hasChildNodes() && getItem( name, false ) != null;
    }

    @Override
    public IDco append( String name ) {
        Element e = element.getOwnerDocument().createElement(name);
        element.appendChild(e);
        return new Dco(e);
    }

    @Override
    public IDco removeItem( String... names ) {

        if( names == null || names.length == 0 )
            return this;

        if( names.length == 1 )
        {
            Element ch = getItem(names[0], false);
            if (ch != null) element.removeChild(ch);
        }
        else
            Stream.of(names).map(s->getItem( s, false )).filter(Objects::nonNull).forEach(element::removeChild);

        return this;
    }

    @Override
    public IDco removeAll( ) {

        while(element.hasChildNodes() )
              element.removeChild( element.getFirstChild() );

        // удалить все атрибуты (если нужно)
        removeAttributes();
        dcoValue = null;
        return this;
    }

    /** */
    @Override
    public Iterable<String> getNamesList() {
        throw new UnsupportedOperationException("getNamesList");
    }

    /** */
    @Override
    public boolean isNull( ) {
        return dcoValue == null;
    }

    /** */
    private Node firstTextNode(  )
    {
        Node tn = element.getFirstChild();

        while (tn != null && U.notIn(tn.getNodeType(), TEXT_NODE, Node.CDATA_SECTION_NODE)) {
            tn = tn.getNextSibling();
        }

        return tn;
    }

    /** */
    @Override
    public void set( Object value ) {

        clearTextNodes( );

        if( value != null )
        {
            final String text = STRING_CONVERTER.to(value);

            if( text != null )
            {
                Node tn = element.getOwnerDocument().createTextNode(text);
                element.appendChild(tn);
            }
        }

        this.dcoValue = value;
    }

    @Override
    public String getName() {
        return element.getNodeName();
    }

    /** */
    @Override
    public IDco getParent( ) {
        Node p = element.getParentNode();
        return (p instanceof Element) ? dco((Element) p) : null;
    }

    /** */
    @Override
    public void remove( ) {

        final Node parent = element.getParentNode();

        if( parent instanceof Element )
        {
            element.setUserData( "dco", null, null );
            dcoValue = null;
            parent.removeChild(element);

            return;
        }

        throw new DcoException( Tags.PRODUCT_LABEL + "'" + getName() + "' is a root node. And cannot be removed!" );
    }

    /** */
    @Override
    public Optional<IDco> single( String xpathQuery ) {

        if( S.isNullOrEmpty(xpathQuery) )
            return Optional.empty();

        try {

            final XPathExpression xe = cacheXExpr.computeIfAbsent(xpathQuery, new Function< String, XPathExpression >() {
                @Override
                public XPathExpression apply( String query ) {
                    try {
                        return xpath().compile(query );
                    }
                    catch( Throwable th ) {
                        throw new DcoException( Tags.PRODUCT_LABEL + "Error on compile xpath expression", th );
                    }
                }
            });

            final Node node = (Node)xe.evaluate( element, XPathConstants.NODE );

            if( node == null )
                return Optional.empty();

            switch( node.getNodeType() ) {
                case ELEMENT_NODE:
                    return Optional.of( dco((Element)node) );
                case ATTRIBUTE_NODE:
                    return Optional.of( new AttrImpl((Attr)node) );
                case TEXT_NODE:
                case CDATA_SECTION_NODE:
                    return Optional.of( dco((Element)node.getParentNode() ) );
            }
            return Optional.empty();
        }
        catch( Throwable th ) {
            throw new DcoException( Tags.PRODUCT_LABEL + "Error on execute xpath expression: '" + xpathQuery + "'.", th );
        }
    }

    /** */
    @Override
    public <T> Optional<T> single( String xpathQuery, Function<IDco,T> mapper ) {

        if( mapper == null )
            return (Optional<T>)single(xpathQuery);

        return single(xpathQuery).map( mapper );
    }

    /** */
    @Override
    public Iterable<IDco> select( String xpathQuery ) {

        if( S.isNullOrEmpty(xpathQuery) )
            return U.iterable( Collections.emptyIterator() );

        try {

            final XPathExpression xe = cacheXExpr.computeIfAbsent(xpathQuery, new Function< String, XPathExpression >() {
                @Override
                public XPathExpression apply( String query ) {
                    try {
                        return xpath().compile(query);
                    }
                    catch( Throwable th ) {
                        throw new DcoException(Tags.PRODUCT_LABEL + "Error on compile xpath expression", th );
                    }
                }
            });

            final Iterator<Element> iter = new ElementListIterator( (NodeList)xe.evaluate( element, XPathConstants.NODESET ) );

            return
                U.iterable (
                    new Iterator< IDco >() {
                        @Override
                        public boolean hasNext() {
                            return iter.hasNext();
                        }

                        @Override
                        public IDco next() {
                            return dco( iter.next() );
                        }
                    }
                );

        }
        catch( Throwable th ) {
            throw new DcoException( Tags.PRODUCT_LABEL + "Error on execute xpath expression: '" + xpathQuery + "'.", th );
        }
    }

    /** */
    public <T> Iterable<T> select( String xpathQuery, Function<IDco,T> mapper )
    {
        if( mapper == null )
            return (Iterable<T>) select(xpathQuery);

        return U.iterable( U.mapIter( select(xpathQuery).iterator(), mapper ) );

    }

    @Override
    public Iterator< IDco > iterator( ) {

        final Iterator<Element> iter = getElementIter().iterator();

        return new Iterator< IDco >() {
            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public IDco next() {
                return dco( iter.next() );
            }
        };
    }

    /** */
    @Override
    public Object get( ) {
        return dcoValue;
    }


    /** */
    @Override
    public <T> T value()
    {
        return (T)get();
    }


    /** */
    @Override
    public Object getIfPresent(String name) {

        if( S.isNullOrEmpty(name) )
            return null;

        if( name.charAt(0) == '@' )
        {
            org.w3c.dom.Attr attr = element.getAttributeNode(name.substring(1));
            return attr == null ? null : attr.getValue();
        }

        final Element item = getItem(name, false);
        return item == null ? null : dco(item).get();
    }


    /** */
    @Override
    public boolean setIfPresent( String name, Object value )
    {
        if( S.isNullOrEmpty(name) )
            return false;

        if( name.charAt(0) == '@' )
        {
            org.w3c.dom.Attr attr = element.getAttributeNode(name.substring(1));

            if( attr != null ) {
                attr.setValue( STRING_CONVERTER.to(value) );
                return true;
            }
        }
        else
        {
            final Element item = getItem( name, false );

            if( item != null ) {
                dco(item).set(value);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean setIfExists( String xpath, Object value ) {

        if( S.isNullOrEmpty(xpath) )
            return false;

        final Holder<Boolean> ret = Holder.of(false);

        single(xpath).ifPresent( (d)->{ d.value(value); ret.set(Boolean.TRUE);} );

        return ret.get();
    }

    /** */
    @Override
    public int count( String xpath ) {

        if( S.isNullOrEmpty(xpath) )
            return 0;

        try {

            xpath = xpath.trim();

            if( !xpath.startsWith("count") )
                 xpath = String.format("count(%s)", xpath);

            final Holder<String> x = new Holder<>(xpath);

            final XPathExpression xe = cacheXExpr.computeIfAbsent( xpath, new Function< String, XPathExpression >() {
                @Override
                public XPathExpression apply( String key ) {
                    try {
                        return xpath().compile( x.get() );
                    }
                    catch( Throwable th ) {
                        throw new DcoException( Tags.PRODUCT_LABEL + "Error on compile xpath expression", th );
                    }
                }
            });

            final Double number = (Double)xe.evaluate( element, XPathConstants.NUMBER );

            return number.intValue();
        }
        catch( Throwable th ) {
            throw new DcoException( Tags.PRODUCT_LABEL + "Error on execute xpath expression: '" + xpath + "'.", th );
        }
    }

    private void clearTextNodes( ) {

        Node node = element.getFirstChild();

        while( node != null )
        {
            Node next = node.getNextSibling();
            short t = node.getNodeType();
            if (t == Node.TEXT_NODE || t == Node.CDATA_SECTION_NODE) {
                element.removeChild(node);
            }
            node = next;
        }
    }

    /** */
    @Override
    public IDco cdata( String cdata )
    {
        clearTextNodes();

        if( cdata != null )
        {
            Node node = element.getOwnerDocument().createCDATASection(cdata);
            element.appendChild(node);
        }

        dcoValue = cdata;
        return this;
    }

    /** */
    public IDco base64( byte[] bytes ) {
        return cdata( (bytes == null) ? null : Base64.getEncoder().encodeToString(bytes) );
    }

    /** */
    private void saveXml( StreamResult result, Charset charset, boolean outputDeclaration )
    {
        try {

            final DOMSource dom = new DOMSource(element);
            final TransformerFactory tf = TransformerFactory.newInstance();
            try {
                tf.setFeature  (XMLConstants.FEATURE_SECURE_PROCESSING, true);
                tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            } catch (Exception ignored) {}

            final Transformer transformer = tf.newTransformer();

            // transformer.setOutputProperty(OutputKeys.INDENT, "yes");

            if(!outputDeclaration )
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

            final DocumentType docType = element.getOwnerDocument().getDoctype();

            if( docType != null )
                transformer.setOutputProperty( OutputKeys.DOCTYPE_SYSTEM, docType.getSystemId() );

            if( charset != null )
                transformer.setOutputProperty( OutputKeys.ENCODING, charset.name() );

            transformer.transform( dom, result );
        }
        catch( Throwable th ) {
            throw new DcoException(Tags.PRODUCT_LABEL + "Error on save XML Dom", th );
        }

    }

    /** */
    public void saveXml( OutputStream stream, Charset charset, boolean declaration ) { saveXml( new StreamResult(stream), charset, declaration ); }

    /** */
    public void saveXml( File fileTo, Charset charset, boolean declaration ) { saveXml( new StreamResult(fileTo), charset, declaration );}

    /** */
    public void saveXml( Writer writer, Charset charset, boolean declaration ) { saveXml( new StreamResult(writer), charset, declaration ); }

    @Override
    public String asXml() {
        final StringWriter sw = new StringWriter();
        saveXml(sw);
        return sw.toString();
    }

    @Override
    public byte[] asXmlBytes(Charset charset ) {
        final RawBAOS rb = new RawBAOS();
        saveXml( new StreamResult(rb), charset, false );
        return rb.buf();
    }

    // JSON zone
    @Override
    public void saveJson( OutputStream stream )
    {
        try {
            JsonHelper.saveXml( element, stream, true );
        }
        catch (Throwable th) {
            throw new DcoException( Tags.PRODUCT_LABEL + "Error on save JSON", th );
        }
    }

    @Override
    public void saveJson( File fileTo)
    {
        try( OutputStream os = Files.newOutputStream( fileTo.toPath()) )
        {
            saveJson(os);
        }
        catch (Throwable th) {
            throw new DcoException(Tags.PRODUCT_LABEL + "Error on save JSON to file: " + fileTo, th);
        }
    }

    @Override
    public void saveJson( Writer writer ) {
        try {
            JsonHelper.saveXml( element, writer, true );
        }
        catch (Throwable th) {
            throw new DcoException( Tags.PRODUCT_LABEL + "Error on save JSON", th );
        }
    }

    @Override
    public String asJson() {
        try {
            //final SegmentedCAW caw = new SegmentedCAW();
            StringWriter caw = new StringWriter();
            saveJson(caw);
            return caw.toString();
        } catch (Throwable th) {
            throw new DcoException(Tags.PRODUCT_LABEL + "Error on convert to JSON string", th);
        }
    }

    @Override
    public byte[] asJsonBytes() {
        try {
            final SegmentedBAOS baos = new SegmentedBAOS();
            saveJson(baos);
            return baos.toByteArray();

        } catch (Throwable th) {
            throw new DcoException(Tags.PRODUCT_LABEL + "Error on convert to JSON bytes", th);
        }
    }

    // static zone //

    /** */
    private static IDco parseXml( InputSource isrc ) throws IOException, SAXException {

        final Document doc = documentBuilder().parse(isrc);

        if( doc != null ) {
            doc.normalize();
            return new Dco( doc.getDocumentElement() );
        }

        throw new IllegalArgumentException("'doc' value is null");
    }

    /** */
    public static IDco parseXml( Reader r, Charset charset ) {

        try {

            final InputSource isrc = new InputSource(r);
            if(charset != null)
                isrc.setEncoding( charset.name() );

            return parseXml(isrc);
        } catch ( Throwable th ) {
            throw new DcoException( Tags.PRODUCT_LABEL + "Error on load xml from Reader", th );
        }
    }

    /** */
    public static IDco parseXml( CharSequence xml ) {
        return parseXml( new CharSequenceReader(xml), null );
    }

    /** */
    public static IDco parseXml( String xml ) {
        return parseXml( new StringReader(xml), null );
    }

    /** */
    public static IDco parseXml( InputStream is ) {

        try {
            return parseXml(new InputSource(is));
        } catch ( Throwable th ) {
            throw new DcoException( Tags.PRODUCT_LABEL + "Error on load xml from InputStream", th );
        }
    }

    /** */
    public static IDco parseXml( InputStream is, Charset charset ) {

        try {

            InputSource s = new InputSource(is);

            if( charset != null )
                s.setEncoding(charset.name());

            return parseXml(s);

        } catch ( Throwable th ) {
            throw new DcoException( Tags.PRODUCT_LABEL + "Error on load xml from InputStream", th );
        }
    }


    /** */
    public static IDco parseXml( File file ) {
        try( InputStream is =  Files.newInputStream( Objects.requireNonNull(file, "'file' is null").toPath() ) ) {
            return parseXml( is );//, StandardCharsets.UTF_8.name() );
        } catch( Exception e) {
            throw new DcoException( Tags.PRODUCT_LABEL + "Error on load xml from file " + file, e );
        }
    }

    public static IDco parseXml( URL url ) {

        Objects.requireNonNull( url, "'url' is null");

        try {

            //stub for memory protocol
            if( "memory".equals( url.getProtocol() ) )
            {
                try( InputStream is = url.openStream() ) {
                    return parseXml( is );
                }
            }

            final String eurl = url.toExternalForm();
            final InputSource isrc = new InputSource( eurl );
            //isrc.setEncoding( encoding );
            return parseXml(isrc);

        } catch ( Throwable th ) {
            throw new DcoException( Tags.PRODUCT_LABEL + "Error on load xml from url " + url.toExternalForm(), th );
        }


        /*

        try( InputStream is = url.openStream() ) {
            return parseXml( is );//, StandardCharsets.UTF_8.name() );
        } catch( Exception e) {
            throw new DcoException( Tags.PRODUCT_LABEL + "Error on load xml from url " + url, e );
        }
        */
    }

    /** */
    public static IDco of( Node node )
    {
        if( node == null )
            return null;

        if( node instanceof Element )
            return new Dco( (Element)node );

        return of( node.getParentNode() );
    }

    /** */
    public static IDco of( String rootName, Map<String,Object> map )
    {
        if( map == null )
            return null;

        final IDco dco = new Dco(Objects.requireNonNull(rootName,"'rootName' is null"));

        map.forEach( (k,v)->dco.e(k).value(v) );

        return dco;
    }

    /** */
    public static IDco parseIniFile( File iniFile, boolean v )
    {
        try( IniFileEventReader r = IniFileEventReader.newBuilder().iniFile(iniFile).semicolonPartOfValue(v).build() )
        {
            final IDco dco = new Dco( iniFile.getName() );

            final Holder<IDco> currentSection = new Holder<>();

            r.stream()
                .filter( t->t.type() == IniFileEvent.Type.Section || t.type() == IniFileEvent.Type.Parameter )
                    .forEach(new Consumer< IniFileEvent >() {
                        @Override
                        public void accept( IniFileEvent e ) {
                            if( e.type() == IniFileEvent.Type.Section )
                                currentSection.set( dco.e( e.value() ));
                            else {

                                if(!currentSection.isPresent() )
                                    currentSection.set( dco.e( IniFileEvent.DEFAULT_SECTION ) );

                                currentSection.get().e(e.key()).set(e.value());
                            }
                        }
                });

            return dco;
        }
        catch( Throwable th ) {
            throw new DcoException( Tags.PRODUCT_LABEL + "Error on load .INI file " + iniFile, th );
        }
    }

    /** */
    public static IDco parseIniFile( String iniFile, boolean v )
    {
        return parseIniFile(new File(iniFile), v);
    }

    /** Парсинг JSON и преобразование в XML
    private static IDco parseJsonImpl(Object source)
    {
        try {

            ObjectMapper jsonMapper = getJsonMapper();
            ObjectMapper xmlMapper  = getXmlMapper();

            JsonNode jsonNode;
            if (source instanceof String) {
                jsonNode = jsonMapper.readTree((String) source);
            } else if (source instanceof Reader) {
                jsonNode = jsonMapper.readTree((Reader) source);
            } else if (source instanceof InputStream) {
                jsonNode = jsonMapper.readTree((InputStream) source);
            } else if (source instanceof File) {
                jsonNode = jsonMapper.readTree((File) source);
            } else if (source instanceof byte[]) {
                jsonNode = jsonMapper.readTree((byte[]) source);
            } else if (source instanceof URL) {
                jsonNode = jsonMapper.readTree((URL) source);
            } else {
                throw new IllegalArgumentException("Load JSON from " + source.getClass() + " not supported");
            }

            // Конвертируем JSON в XML
            try( SegmentedBAOS w = new SegmentedBAOS(1024) )
            {
                xmlMapper.writeValue( w, jsonNode );

                try(InputStream is = w.inputStream() )
                {
                    return parseXml(is);
                }
            }

        } catch (Throwable th) {
            throw new DcoException(Tags.PRODUCT_LABEL + "Error on load JSON from " + source, th);
        }
    }

    public static IDco parseJson(String json) {
        return parseJsonImpl(json);
    }

    public static IDco parseJson(Reader reader) {
        return parseJsonImpl(reader);
    }

    public static IDco parseJson(InputStream inputStream) {
        return parseJsonImpl(inputStream);
    }

    public static IDco parseJson(File file) {
        return parseJsonImpl(file);
    }

    public static IDco parseJson(URL url) {
        return parseJsonImpl(url);
    }

    public static IDco parseJson(byte[] jsonBytes) {
        return parseJsonImpl(jsonBytes);
    }
    */
}
