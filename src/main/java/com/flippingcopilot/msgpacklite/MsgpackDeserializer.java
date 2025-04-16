package com.flippingcopilot.msgpacklite;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal implementation to deserialize msgpack messages
 */
public class MsgpackDeserializer {
    private static final byte EXT_TYPE_INT32_ARRAY = 41;

    public static <T> T deserialize(byte[] data, Class<T> classOfT) {
        Object result = deserialize(ByteBuffer.wrap(data));
        if (result instanceof Map) {
            return convertMapToObject((Map<Object, Object>) result, classOfT);
        }
        return (T) result;
    }

    private static Object deserialize(ByteBuffer buffer) {
        byte typeByte = buffer.get();
        int type = typeByte & 0xff;  // Convert to unsigned int (0-255)

        // Positive fixint (0x00 - 0x7f)
        if ((type & 0x80) == 0x00) {
            return (int) typeByte;
        }

        // Negative fixint (0xe0 - 0xff)
        if ((type & 0xe0) == 0xe0) {
            return (int) typeByte;
        }

        // Fixmap (0x80 - 0x8f)
        if ((type & 0xf0) == 0x80) {
            int size = type & 0x0f;
            return deserializeMap(buffer, size);
        }

        // Fixarray (0x90 - 0x9f)
        if ((type & 0xf0) == 0x90) {
            int size = type & 0x0f;
            return deserializeArray(buffer, size);
        }

        // Fixstr (0xa0 - 0xbf)
        if ((type & 0xe0) == 0xa0) {
            int length = type & 0x1f;
            return deserializeString(buffer, length);
        }

        // Use type (the integer value) instead of typeByte (the signed byte)
        switch (type) {
            case 0xc0:  // NIL
                return null;
            case 0xc2:  // FALSE
                return false;
            case 0xc3:  // TRUE
                return true;
            case 0xc4:  // BIN8
                return deserializeBinary(buffer, buffer.get() & 0xff);
            case 0xc5:  // BIN16
                return deserializeBinary(buffer, ((buffer.get() & 0xff) << 8) | (buffer.get() & 0xff));
            case 0xc6:  // BIN32
                return deserializeBinary(buffer, ((buffer.get() & 0xff) << 24) | ((buffer.get() & 0xff) << 16) |
                        ((buffer.get() & 0xff) << 8) | (buffer.get() & 0xff));
            case 0xc9:  // EXT32
                int length = ((buffer.get() & 0xff) << 24) | ((buffer.get() & 0xff) << 16) |
                        ((buffer.get() & 0xff) << 8) | (buffer.get() & 0xff);
                byte extType2 = buffer.get();
                if (extType2 == EXT_TYPE_INT32_ARRAY) {
                    return deserializeInt32Array(buffer, length);
                }
                throw new IllegalArgumentException("Unsupported MessagePack ext: " + extType2);
            case 0xca:  // FLOAT32
                return buffer.getFloat();
            case 0xcb:  // FLOAT64
                return buffer.getDouble();
            case 0xcc:  // UINT8
                return buffer.get() & 0xff;
            case 0xcd:  // UINT16
                return ((buffer.get() & 0xff) << 8) | (buffer.get() & 0xff);
            case 0xce:  // UINT32
                long uint32 = ((buffer.get() & 0xffL) << 24) | ((buffer.get() & 0xffL) << 16) |
                        ((buffer.get() & 0xffL) << 8) | (buffer.get() & 0xffL);
                return uint32;
            case 0xcf:  // UINT64
                long high = ((buffer.get() & 0xffL) << 24) | ((buffer.get() & 0xffL) << 16) |
                        ((buffer.get() & 0xffL) << 8) | (buffer.get() & 0xffL);
                long low = ((buffer.get() & 0xffL) << 24) | ((buffer.get() & 0xffL) << 16) |
                        ((buffer.get() & 0xffL) << 8) | (buffer.get() & 0xffL);
                return (high << 32) | low;
            case 0xd0:  // INT8
                return (int) buffer.get();
            case 0xd1:  // INT16
                return (short) (((buffer.get() & 0xff) << 8) | (buffer.get() & 0xff));
            case 0xd2:  // INT32
                return ((buffer.get() & 0xff) << 24) | ((buffer.get() & 0xff) << 16) |
                        ((buffer.get() & 0xff) << 8) | (buffer.get() & 0xff);
            case 0xd3:  // INT64
                long high64 = ((buffer.get() & 0xffL) << 24) | ((buffer.get() & 0xffL) << 16) |
                        ((buffer.get() & 0xffL) << 8) | (buffer.get() & 0xffL);
                long low64 = ((buffer.get() & 0xffL) << 24) | ((buffer.get() & 0xffL) << 16) |
                        ((buffer.get() & 0xffL) << 8) | (buffer.get() & 0xffL);
                return (high64 << 32) | low64;
            case 0xd7:  // EXT8
                int extDataLength = buffer.get() & 0xff;
                byte extType1 = buffer.get();
                if (extType1 == EXT_TYPE_INT32_ARRAY) {
                    return deserializeInt32Array(buffer, extDataLength);
                }
                throw new IllegalArgumentException("Unsupported MessagePack ext: " + extType1);
            case 0xd8:  // EXT16
                int ext16Length = ((buffer.get() & 0xff) << 8) | (buffer.get() & 0xff);
                byte extType = buffer.get();
                if (extType == EXT_TYPE_INT32_ARRAY) {
                    return deserializeInt32Array(buffer, ext16Length);
                }
                throw new IllegalArgumentException("Unsupported MessagePack ext: " + extType);
            case 0xd9:  // STR8
                return deserializeString(buffer, buffer.get() & 0xff);
            case 0xda:  // STR16
                return deserializeString(buffer, ((buffer.get() & 0xff) << 8) | (buffer.get() & 0xff));
            case 0xdb:  // STR32
                return deserializeString(buffer, ((buffer.get() & 0xff) << 24) | ((buffer.get() & 0xff) << 16) |
                        ((buffer.get() & 0xff) << 8) | (buffer.get() & 0xff));
            case 0xdc:  // ARRAY16
                return deserializeArray(buffer, ((buffer.get() & 0xff) << 8) | (buffer.get() & 0xff));
            case 0xdd:  // ARRAY32
                return deserializeArray(buffer, ((buffer.get() & 0xff) << 24) | ((buffer.get() & 0xff) << 16) |
                        ((buffer.get() & 0xff) << 8) | (buffer.get() & 0xff));
            case 0xde:  // MAP16
                return deserializeMap(buffer, ((buffer.get() & 0xff) << 8) | (buffer.get() & 0xff));
            case 0xdf:  // MAP32
                return deserializeMap(buffer, ((buffer.get() & 0xff) << 24) | ((buffer.get() & 0xff) << 16) |
                        ((buffer.get() & 0xff) << 8) | (buffer.get() & 0xff));
            default:
                throw new IllegalArgumentException("Unknown MessagePack type: 0x" +
                        Integer.toHexString(type) + " (dec: " + type + ")");
        }
    }

    private static String deserializeString(ByteBuffer buffer, int length) {
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return new String(bytes);
    }

    private static byte[] deserializeBinary(ByteBuffer buffer, int length) {
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return bytes;
    }

    private static List<Object> deserializeArray(ByteBuffer buffer, int size) {
        List<Object> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(deserialize(buffer));
        }
        return result;
    }

    private static Map<Object, Object> deserializeMap(ByteBuffer buffer, int size) {
        Map<Object, Object> result = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            Object key = deserialize(buffer);
            Object value = deserialize(buffer);
            result.put(key, value);
        }
        return result;
    }

    private static int[] deserializeInt32Array(ByteBuffer buffer, int byteLength) {
        int arrayLength = byteLength / 4;
        int[] result = new int[arrayLength];
        for (int i = 0; i < arrayLength; i++) {
            result[i] = (buffer.get() & 0xff) | ((buffer.get() & 0xff) << 8) |
                    ((buffer.get() & 0xff) << 16) | ((buffer.get() & 0xff) << 24);
        }
        return result;
    }



    private static <T> T convertMapToObject(Map<Object, Object> map, Class<T> classOfT) {
        try {
            T instance = classOfT.getDeclaredConstructor().newInstance();

            Field[] fields = classOfT.getDeclaredFields();

            for (Field field : fields) {
                field.setAccessible(true);

                MsgpackName annotation = field.getAnnotation(MsgpackName.class);
                String fieldName = field.getName();

                if (annotation != null) {
                    fieldName = annotation.value();
                }
                if (map.containsKey(fieldName)) {
                    Object value = map.get(fieldName);
                    setFieldValue(field, instance, value);
                }
            }

            return instance;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance of " + classOfT, e);
        }
    }

    private static void setFieldValue(Field field, Object instance, Object value) throws IllegalAccessException {
        Class<?> fieldType = field.getType();

        if (value == null) {
            field.set(instance, null);
            return;
        }

        // Handle primitive types
        if (fieldType == int.class || fieldType == Integer.class) {
            field.set(instance, ((Number) value).intValue());
        } else if (fieldType == long.class || fieldType == Long.class) {
            field.set(instance, ((Number) value).longValue());
        } else if (fieldType == float.class || fieldType == Float.class) {
            field.set(instance, ((Number) value).floatValue());
        } else if (fieldType == double.class || fieldType == Double.class) {
            field.set(instance, ((Number) value).doubleValue());
        } else if (fieldType == boolean.class || fieldType == Boolean.class) {
            field.set(instance, value);
        } else if (fieldType == byte.class || fieldType == Byte.class) {
            field.set(instance, ((Number) value).byteValue());
        } else if (fieldType == short.class || fieldType == Short.class) {
            field.set(instance, ((Number) value).shortValue());
        } else if (fieldType == String.class) {
            field.set(instance, value.toString());
        } else if (fieldType == byte[].class) {
            field.set(instance, value);
        } else if (List.class.isAssignableFrom(fieldType) && value instanceof List) {
            field.set(instance, value);
        } else if (Map.class.isAssignableFrom(fieldType) && value instanceof Map) {
            field.set(instance, value);
        } else if (fieldType.isArray() && value instanceof List) {
            setArrayField(field, instance, (List<?>) value, fieldType.getComponentType());
        } else if (value instanceof Map) {
            // Nested object
            Object nestedObject = convertMapToObject((Map<Object, Object>) value, fieldType);
            field.set(instance, nestedObject);
        } else {
            field.set(instance, value);
        }
    }

    private static void setArrayField(Field field, Object instance, List<?> list, Class<?> componentType)
            throws IllegalAccessException {
        int size = list.size();
        Object array = Array.newInstance(componentType, size);

        for (int i = 0; i < size; i++) {
            Object item = list.get(i);
            if (componentType.isPrimitive()) {
                if (componentType == int.class) {
                    Array.setInt(array, i, ((Number) item).intValue());
                } else if (componentType == long.class) {
                    Array.setLong(array, i, ((Number) item).longValue());
                } else if (componentType == float.class) {
                    Array.setFloat(array, i, ((Number) item).floatValue());
                } else if (componentType == double.class) {
                    Array.setDouble(array, i, ((Number) item).doubleValue());
                } else if (componentType == boolean.class) {
                    Array.setBoolean(array, i, (Boolean) item);
                } else if (componentType == byte.class) {
                    Array.setByte(array, i, ((Number) item).byteValue());
                } else if (componentType == short.class) {
                    Array.setShort(array, i, ((Number) item).shortValue());
                } else if (componentType == char.class) {
                    Array.setChar(array, i, (Character) item);
                }
            } else if (item instanceof Map && !componentType.equals(Map.class)) {
                Array.set(array, i, convertMapToObject((Map<Object, Object>) item, componentType));
            } else {
                Array.set(array, i, item);
            }
        }

        field.set(instance, array);
    }
}