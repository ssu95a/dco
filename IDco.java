package ru.inversion.utils.dco;

import ru.inversion.utils.U;
import ru.inversion.utils.converter.TypeConverter;

//import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 *
 */
public interface IDco extends Iterable<IDco>, Supplier<Object>, Consumer<Object> {

    // Attrs
    /** Аттрибуты узла */
    default Iterable< IDco > attributes() {
        return U.iterable( Collections.emptyIterator() );
    }

    /** */
    default boolean hasAttributes() { return false; }

    /** */
    default boolean hasAttribute( String name ) { return false; }

    /** */
    default IDco a( String attr ) { throw new UnsupportedOperationException("'a' not supported"); }

    /** */
    default IDco removeAttribute( String... names ) { throw new UnsupportedOperationException("'removeAttribute' not supported"); }

    /** */
    default IDco removeAttributes()  { throw new UnsupportedOperationException("'removeAttributes' not supported"); }

    // Items
    /** */
    default IDco e( String name ) { throw new UnsupportedOperationException("'e' not supported"); }

    /** */
    default boolean hasItem( String name ) { return false; }

    /** */
    default IDco append( String name ){ throw new UnsupportedOperationException("'e' not supported"); }

    /** */
    default IDco removeItem( String... names ){ throw new UnsupportedOperationException("'removeItem' not supported"); }

    /** */
    default IDco removeAll(){ throw new UnsupportedOperationException("'removeAll' not supported"); }

    /** */
    default Iterable< String > getNamesList() { return U.iterable( Collections.emptyIterator() ); }

    /** */
    boolean isNull();

    /** */
    IDco getParent();

    /** */
    default IDco cdata( String val ){ throw new UnsupportedOperationException("'cdata' not supported"); }

    /** */
    default IDco cdata( char[] val ){ throw new UnsupportedOperationException("'cdata' not supported"); }

    /** */
    default IDco cdata( Reader val ){ throw new UnsupportedOperationException("'cdata' not supported"); }

    /** */
    default IDco base64( InputStream is ) { throw new UnsupportedOperationException("'base64' not supported"); }

    /** */
    default IDco base64( byte[] bytes ) { throw new UnsupportedOperationException("'base64' not supported"); }

    /** */
    default Iterable<IDco> select( String xpathQuery ){ return U.iterable( Collections.emptyIterator() );}
    /** */
    default <T> Iterable<T> select( String xpathQuery, Function<IDco,T> mapper ){ return U.iterable( Collections.emptyIterator() );}

    /** */
    default Optional<IDco> single( String xpathQuery ) { return Optional.empty();}
    /** */
    default <T> Optional<T> single( String xpathQuery, Function<IDco,T> mapper ) { return Optional.empty();}

    /** */
    default IDco value( Object v )
    {
        set(v); return this;
    }

    /** */
    default void saveXml( File fileTo ){ saveXml( fileTo, null, true ); }
    default void saveXml( File fileTo, Charset charset, boolean declaration ) { throw new UnsupportedOperationException("'saveXml' not supported"); }

    /** */
    default void saveXml( Writer wTo, Charset charset, boolean declaration ){ throw new UnsupportedOperationException("'saveXml' not supported"); }
    default void saveXml( Writer wTo ){ saveXml( wTo, null, false ); }

    /** */
    default void saveXml( OutputStream sTo ) { saveXml(sTo, null, false);  }
    default void saveXml( OutputStream stream, Charset charset, boolean declaration ) { throw new UnsupportedOperationException("'saveXml' not supported"); }

    /** */
    default String asXml( ){ throw new UnsupportedOperationException("'asXml' not supported"); }

    /** */
    default byte[] asXmlBytes(Charset charset ){ throw new UnsupportedOperationException("'asBytes' not supported"); }

    /*** Сохранение в JSON */
    default void saveJson(File fileTo) { throw new UnsupportedOperationException("'saveJson' not supported"); }

    /** Сохранение в Writer */
    default void saveJson(Writer wTo ) { throw new UnsupportedOperationException("'saveJson' not supported"); }

    /** Сохранение в OutputStream */
    default void saveJson(OutputStream stream) { throw new UnsupportedOperationException("'saveJson' not supported"); }

    /** Получение JSON строки */
    default String asJson() { throw new UnsupportedOperationException("'asJson' not supported"); }

    /** Получение JSON в виде байтов */
    default byte[] asJsonBytes() { throw new UnsupportedOperationException("'asJsonBytes' not supported"); }

    /** */
    void set( Object value );

    /** */
    @Override
    default void accept( Object value ) {
        set(value);
    }

    /** */
    String getName();

    /** */
    void remove();

    /** */
    default <T> T value() {
        return (T)get();
    }

    /** */
    default <T> T value( Class<T> vClass ) {
        return TypeConverter.convert( get(), vClass );
    }

    /** */
    default <T> T getOrDefault( T defaultValue ) {
        return U.nvl( value(), defaultValue );
    }

    default <T> T getOrDefault( Class<T> vClass, T defaultValue ) {
        return U.nvl( value(vClass), defaultValue );
    }

    /** */
    default boolean setIfPresent( String name, Object value ){ return false; }

    /** */
    default boolean setIfExists ( String xpath,Object value ){ return false; }

    /** */
    default int count( String xpath ) { throw new UnsupportedOperationException("'count' not supported"); };
}
