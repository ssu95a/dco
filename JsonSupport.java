package ru.inversion.utils.dco;

import javax.json.*;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonGeneratorFactory;

import org.w3c.dom.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

class JsonSupport {

    private static final String ATTR_PREFIX = "@";
    private static final String VALUE_KEY   = "#value";

    static void write( IDco dco, Writer writer, boolean pretty )
    {
        Objects.requireNonNull( dco, "'dco' is null");
        Objects.requireNonNull( writer, "'writer' is null");

        JsonGeneratorFactory factory = Json.createGeneratorFactory (
                pretty ? Collections.<String, Object>singletonMap(JsonGenerator.PRETTY_PRINTING, Boolean.TRUE)
                        : Collections.emptyMap()
        );

        try( JsonGenerator gen = factory.createGenerator(writer) ) {
            gen.writeStartObject();
            writeProperty(gen, dco.getName(), dco);
            gen.writeEnd();
            gen.flush();
        }
        catch( Throwable th ) {
            throw new DcoException("Error on write DCO as JSON", th);
        }
    }

    static void write(IDco dco, OutputStream stream, boolean pretty) {
        Objects.requireNonNull(dco, "'dco' is null");
        Objects.requireNonNull(stream, "'stream' is null");

        JsonGeneratorFactory factory = Json.createGeneratorFactory(
                pretty ? Collections.<String, Object>singletonMap(JsonGenerator.PRETTY_PRINTING, Boolean.TRUE)
                        : Collections.emptyMap()
        );

        try( JsonGenerator gen = factory.createGenerator(stream) ) {
            gen.writeStartObject();
            writeProperty(gen, dco.getName(), dco);
            gen.writeEnd();
            gen.flush();
        }
        catch( Throwable th ) {
            throw new DcoException("Error on write DCO as JSON", th);
        }
    }


    static IDco read(Reader reader) {
        Objects.requireNonNull(reader, "'reader' is null");

        try( JsonReader jsonReader = Json.createReader(reader) ) {
            JsonValue value = jsonReader.readValue();
            return readRoot(value);
        }
        catch( Throwable th ) {
            throw new DcoException("Error on read DCO from JSON", th);
        }
    }

    static IDco read(InputStream stream) {
        Objects.requireNonNull(stream, "'stream' is null");

        try( JsonReader jsonReader = Json.createReader(stream) ) {
            JsonValue value = jsonReader.readValue();
            return readRoot(value);
        }
        catch( Throwable th ) {
            throw new DcoException("Error on read DCO from JSON", th);
        }
    }

    static IDco read(String rootName, Reader reader) {
        Objects.requireNonNull(rootName, "'rootName' is null");
        Objects.requireNonNull(reader, "'reader' is null");

        try( JsonReader jsonReader = Json.createReader(reader) ) {
            IDco root = new Dco(rootName);
            readValueInto(root, jsonReader.readValue());
            return root;
        }
        catch( Throwable th ) {
            throw new DcoException("Error on read DCO from JSON", th);
        }
    }

    static IDco read(String rootName, InputStream stream) {
        Objects.requireNonNull(rootName, "'rootName' is null");
        Objects.requireNonNull(stream, "'stream' is null");

        try( JsonReader jsonReader = Json.createReader(stream) ) {
            IDco root = new Dco(rootName);
            readValueInto(root, jsonReader.readValue());
            return root;
        }
        catch( Throwable th ) {
            throw new DcoException("Error on read DCO from JSON", th);
        }
    }

    private static void writeProperty( JsonGenerator gen, String name, IDco node )
    {
        boolean hasAttributes = node.hasAttributes();
        boolean hasChildren   = node.hasItems();

        if( !hasAttributes && !hasChildren ) {
            writeScalar( gen, name, node.get() );
            return;
        }

        gen.writeStartObject(name);
        writeNodeContent(gen, node);
        gen.writeEnd();
    }


    /** */
    private static void writeArrayItem(JsonGenerator gen, IDco node) {

        boolean hasAttributes = node.hasAttributes();
        boolean hasChildren   = node.hasItems();

        if( !hasAttributes && !hasChildren ) {
            writeScalar(gen, node.get());
            return;
        }

        gen.writeStartObject();
        writeNodeContent(gen, node);
        gen.writeEnd();
    }


    /** */
    private static void writeNodeContent(JsonGenerator gen, IDco node) {

        writeAttributes(gen, node);

        Object value = node.get();

        if( value != null )
            writeScalar(gen, VALUE_KEY, value);

        Map<String, List<IDco>> groups = groupChildren(node);

        for( Map.Entry<String, List<IDco>> entry : groups.entrySet() ) {

            String childName = entry.getKey();
            List<IDco> list = entry.getValue();

            if( list.size() == 1 ) {
                writeProperty(gen, childName, list.get(0));
            }
            else {
                gen.writeStartArray(childName);

                for( IDco child : list )
                    writeArrayItem(gen, child);

                gen.writeEnd();
            }
        }
    }

    /** */
    private static void writeAttributes(JsonGenerator gen, IDco node) {
        for( IDco attr : node.attributes() )
            writeScalar(gen, ATTR_PREFIX + attr.getName(), attr.get());
    }


    /** */
    private static Map<String, List<IDco>> groupChildren(IDco node) {

        Map<String, List<IDco>> groups = new LinkedHashMap<>();

        for( IDco child : node ) {
            List<IDco> list = groups.get(child.getName());

            if( list == null ) {
                list = new ArrayList<>();
                groups.put(child.getName(), list);
            }

            list.add(child);
        }

        return groups;
    }

    /** */
    private static void writeScalar( JsonGenerator gen, String name, Object value) {

        if( value == null ) {
            gen.writeNull(name);
            return;
        }

        if( value instanceof Boolean ) {
            gen.write(name, (Boolean)value);
            return;
        }

        if( value instanceof BigDecimal ) {
            gen.write(name, (BigDecimal)value);
            return;
        }

        if( value instanceof BigInteger ) {
            gen.write(name, (BigInteger)value);
            return;
        }

        if( value instanceof Integer ) {
            gen.write(name, ((Integer)value).intValue());
            return;
        }

        if( value instanceof Long ) {
            gen.write(name, ((Long)value).longValue());
            return;
        }

        if( value instanceof Short ) {
            gen.write(name, ((Short)value).intValue());
            return;
        }

        if( value instanceof Byte ) {
            gen.write(name, ((Byte)value).intValue());
            return;
        }

        if( value instanceof Float || value instanceof Double ) {
            gen.write(name, new BigDecimal(value.toString()));
            return;
        }

        gen.write(name, value.toString());
    }


    /** */
    private static void writeScalar( JsonGenerator gen, Object value) {

        if( value == null ) {
            gen.writeNull();
            return;
        }

        if( value instanceof Boolean ) {
            gen.write((Boolean)value);
            return;
        }

        if( value instanceof BigDecimal ) {
            gen.write((BigDecimal)value);
            return;
        }

        if( value instanceof BigInteger ) {
            gen.write((BigInteger)value);
            return;
        }

        if( value instanceof Integer ) {
            gen.write(((Integer)value).intValue());
            return;
        }

        if( value instanceof Long ) {
            gen.write(((Long)value).longValue());
            return;
        }

        if( value instanceof Short ) {
            gen.write(((Short)value).intValue());
            return;
        }

        if( value instanceof Byte ) {
            gen.write(((Byte)value).intValue());
            return;
        }

        if( value instanceof Float || value instanceof Double ) {
            gen.write(new BigDecimal(value.toString()));
            return;
        }

        gen.write(value.toString());
    }


    /** */
    private static IDco readRoot(JsonValue value) {

        if( value == null || value.getValueType() != JsonValue.ValueType.OBJECT )
            throw new DcoException("Root JSON value must be object with single root field");

        JsonObject object = value.asJsonObject();

        if( object.size() != 1 )
            throw new DcoException("Root JSON object must contain exactly one root field");

        Map.Entry<String, JsonValue> rootEntry = object.entrySet().iterator().next();

        String rootName = rootEntry.getKey();

        validateNodeName(rootName);

        IDco root = new Dco(rootName);
        readValueInto(root, rootEntry.getValue());

        return root;
    }

    private static void readValueInto(IDco node, JsonValue value) {

        if( value == null ) {
            node.set(null);
            return;
        }

        switch( value.getValueType() ) {

            case OBJECT:
                readObjectInto(node, value.asJsonObject());
                return;

            case ARRAY:
                throw new DcoException("Unexpected JSON array for node: " + node.getName());

            case STRING:
            case NUMBER:
            case TRUE:
            case FALSE:
            case NULL:
                node.set(toJavaScalar(value));
                return;

            default:
                throw new DcoException("Unsupported JSON value type: " + value.getValueType());
        }
    }

    private static void readObjectInto(IDco node, JsonObject object) {

        for( Map.Entry<String, JsonValue> entry : object.entrySet() ) {

            String name = entry.getKey();
            JsonValue value = entry.getValue();

            if( VALUE_KEY.equals(name) ) {
                node.set(toNullableScalar(value, VALUE_KEY));
                continue;
            }

            if( name.startsWith(ATTR_PREFIX) ) {
                String attrName = name.substring(ATTR_PREFIX.length());

                validateAttributeName(attrName);

                Object attrValue = toNonNullScalar(value, name);
                node.a(attrName).set(attrValue);
                continue;
            }

            if( name.startsWith("#") )
                throw new DcoException("Unsupported reserved JSON field: " + name);

            validateNodeName(name);

            if( value != null && value.getValueType() == JsonValue.ValueType.ARRAY ) {
                readArrayInto(node, name, value.asJsonArray());
            }
            else {
                IDco child = node.e(name);
                readValueInto(child, value);
            }
        }
    }

    private static void readArrayInto(IDco parent, String name, JsonArray array) {

        for( JsonValue item : array ) {

            if( item != null && item.getValueType() == JsonValue.ValueType.ARRAY )
                throw new DcoException("Nested JSON arrays are not supported for field: " + name);

            IDco child = parent.append(name);
            readValueInto(child, item);
        }
    }

    private static Object toNullableScalar(JsonValue value, String fieldName) {

        if( value == null )
            return null;

        switch( value.getValueType() ) {

            case STRING:
            case NUMBER:
            case TRUE:
            case FALSE:
            case NULL:
                return toJavaScalar(value);

            case OBJECT:
            case ARRAY:
                throw new DcoException("JSON field '" + fieldName + "' must be scalar");

            default:
                throw new DcoException("Unsupported JSON value type: " + value.getValueType());
        }
    }

    private static Object toNonNullScalar(JsonValue value, String fieldName) {

        Object scalar = toNullableScalar(value, fieldName);

        if( scalar == null )
            throw new DcoException("JSON field '" + fieldName + "' must not be null");

        return scalar;
    }

    private static Object toJavaScalar(JsonValue value) {

        if( value == null )
            return null;

        switch( value.getValueType() ) {

            case STRING:
                return ((JsonString)value).getString();

            case NUMBER:
                return ((JsonNumber)value).bigDecimalValue();

            case TRUE:
                return Boolean.TRUE;

            case FALSE:
                return Boolean.FALSE;

            case NULL:
                return null;

            default:
                throw new DcoException("JSON value must be scalar: " + value.getValueType());
        }
    }


    private static void validateNodeName(String name) {

        if( name == null || name.length() == 0 )
            throw new DcoException("JSON field name is empty");

        if( name.startsWith(ATTR_PREFIX) )
            throw new DcoException("Node name must not start with '@': " + name);

        if( name.startsWith("#") )
            throw new DcoException("Node name uses reserved prefix '#': " + name);
    }

    private static void validateAttributeName(String name) {

        if( name == null || name.length() == 0 )
            throw new DcoException("JSON attribute name is empty");

        if( name.startsWith(ATTR_PREFIX) )
            throw new DcoException("Attribute name must not start with '@': " + name);

        if( name.startsWith("#") )
            throw new DcoException("Attribute name uses reserved prefix '#': " + name);
    }
}




