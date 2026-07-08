package ru.inversion.utils.dco;

import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonGeneratorFactory;

import org.w3c.dom.*;
import ru.inversion.utils.U;

import java.io.OutputStream;
import java.io.Writer;
import java.util.*;

class JsonHelper {

    /** */
    public static void saveXml( Element element, Writer w, boolean pretty )
    {
        JsonGeneratorFactory factory = Json.createGeneratorFactory( pretty ? U.toMap(JsonGenerator.PRETTY_PRINTING, true ) : Collections.emptyMap() );

        try( JsonGenerator gen = factory.createGenerator(w) )
        {
            if( element.getNodeType() == Node.DOCUMENT_NODE )
                element = ((Document) element).getDocumentElement();

            writeElement( gen, element );

            gen.flush();
        }
        catch ( Throwable th ) {
            throw new DcoException("Error on save", th );
        }
    }

    /** */
    public static void saveXml( Element element, OutputStream os, boolean pretty )
    {
        JsonGeneratorFactory factory = Json.createGeneratorFactory( pretty ? U.toMap( JsonGenerator.PRETTY_PRINTING, true) : Collections.emptyMap() );

        try( JsonGenerator gen = factory.createGenerator(os) )
        {
            if( element.getNodeType() == Node.DOCUMENT_NODE)
                element = ((Document) element).getDocumentElement();

            writeElement(gen, element );
            gen.flush();
        }
    }

    /** */
    private static void writeElement( JsonGenerator gen, Element element ) {

        gen.writeStartObject();
        gen.writeStartObject( element.getNodeName() );

        writeAttributes( gen, element );

        processChildren( gen, element );

        gen.writeEnd( );
        gen.writeEnd( );
    }

    /** */
    private static void writeAttributes( JsonGenerator gen, Element element) {

        final NamedNodeMap attrs = element.getAttributes();

        for( int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            gen.write("@" + attr.getNodeName(), attr.getNodeValue());
        }
    }


    /** */
    private static void processChildren( JsonGenerator gen, Element element ) {

        NodeList children = element.getChildNodes();

        boolean hasText     = false;
        boolean hasElements = false;
        final StringBuilder textContent = new StringBuilder();

        // Сначала собираем текст
        for( int i = 0; i < children.getLength(); i++ )
        {
            Node child = children.item(i);
            if( child.getNodeType() == Node.TEXT_NODE )
            {
                String text = child.getTextContent().trim();
                if(!text.isEmpty()) {
                    hasText = true;
                    textContent.append(text).append(" ");
                }
            }
            else
                if( child.getNodeType() == Node.ELEMENT_NODE )
                    hasElements = true;
        }

        if( hasText )
            gen.write("#text", textContent.toString().trim() );

        if( hasElements )
        {
            final Map<String, List<Element>> elementGroups = new HashMap<>();

            // Группируем элементы по имени
            for( int i = 0; i < children.getLength(); i++ )
            {
                Node child = children.item(i);

                if( child.getNodeType() == Node.ELEMENT_NODE )
                {
                    Element childElement = (Element) child;
                    String name = childElement.getNodeName();
                    elementGroups.computeIfAbsent(name, k -> new ArrayList<>()).add(childElement);
                }
            }

            // Пишем сгруппированные элементы
            for( Map.Entry<String, List<Element>> entry : elementGroups.entrySet())
            {
                String elementName = entry.getKey();
                List<Element> elements = entry.getValue();

                if( elements.size() == 1 )
                {
                    gen.writeStartObject(elementName);
                    writeElementContent( gen, elements.get(0) );
                    gen.writeEnd();
                }
                else
                {
                    // Несколько элементов - пишем как массив
                    gen.writeStartArray(elementName);
                    for( Element childElement : elements )
                    {
                        gen.writeStartObject();
                        writeElementContent(gen, childElement);
                        gen.writeEnd();
                    }
                    gen.writeEnd();
                }
            }
        }
    }

    private static void writeElementContent(JsonGenerator gen, Element element) {
        writeAttributes(gen, element);
        processChildren(gen, element);
    }
}