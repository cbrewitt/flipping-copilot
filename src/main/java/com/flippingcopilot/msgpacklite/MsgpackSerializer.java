package com.flippingcopilot.msgpacklite;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal implementation to serialize msgpack messages
 */
public class MsgpackSerializer {
    // Extension type for custom int32 array
    private static final byte EXT_TYPE_INT32_ARRAY = 41;

    public static byte[] serialize(Object value) {
        if (value == null) {
            return new byte[]{(byte) 0xc0};
        } else if (value instanceof String) {
            return serializeString((String) value);
        } else if (value instanceof Integer) {
            return serializeInt((Integer) value);
        } else if (value instanceof Long) {
            return serializeLong((Long) value);
        } else if (value instanceof Boolean) {
            return new byte[]{((Boolean) value) ? (byte) 0xc3 : (byte) 0xc2};
        } else if (value instanceof Float) {
            return serializeFloat((Float) value);
        } else if (value instanceof Double) {
            return serializeDouble((Double) value);
        } else if (value instanceof byte[]) {
            return serializeBinary((byte[]) value);
        } else if (value instanceof int[]) {
            return serializeInt32Array((int[]) value);
        } else if (value instanceof List) {
            return serializeList((List<?>) value);
        } else if (value instanceof Map) {
            return serializeMap((Map<?, ?>) value);
        } else {
            return serializeObject(value);
        }
    }

    private static byte[] serializeString(String value) {
        byte[] strBytes = value.getBytes();
        int length = strBytes.length;

        ByteBuffer buffer;
        if (length < 32) {
            // fixstr format
            buffer = ByteBuffer.allocate(1 + length);
            buffer.put((byte) (0xa0 | length));
        } else if (length < 256) {
            // str8 format
            buffer = ByteBuffer.allocate(2 + length);
            buffer.put((byte) 0xd9);
            buffer.put((byte) length);
        } else if (length < 65536) {
            // str16 format
            buffer = ByteBuffer.allocate(3 + length);
            buffer.put((byte) 0xda);
            buffer.put((byte) (length >>> 8));
            buffer.put((byte) length);
        } else {
            // str32 format
            buffer = ByteBuffer.allocate(5 + length);
            buffer.put((byte) 0xdb);
            buffer.put((byte) (length >>> 24));
            buffer.put((byte) (length >>> 16));
            buffer.put((byte) (length >>> 8));
            buffer.put((byte) length);
        }

        buffer.put(strBytes);
        return buffer.array();
    }

    private static byte[] serializeInt(Integer value) {
        int v = value;
        ByteBuffer buffer;

        if (v >= -32 && v <= 127) {
            // fixint format
            buffer = ByteBuffer.allocate(1);
            buffer.put((byte) v);
        } else if (v >= Byte.MIN_VALUE && v <= Byte.MAX_VALUE) {
            // int8 format
            buffer = ByteBuffer.allocate(2);
            buffer.put((byte) 0xd0);
            buffer.put((byte) v);
        } else if (v >= Short.MIN_VALUE && v <= Short.MAX_VALUE) {
            // int16 format
            buffer = ByteBuffer.allocate(3);
            buffer.put((byte) 0xd1);
            buffer.put((byte) (v >>> 8));
            buffer.put((byte) v);
        } else {
            // int32 format
            buffer = ByteBuffer.allocate(5);
            buffer.put((byte) 0xd2);
            buffer.put((byte) (v >>> 24));
            buffer.put((byte) (v >>> 16));
            buffer.put((byte) (v >>> 8));
            buffer.put((byte) v);
        }

        return buffer.array();
    }

    private static byte[] serializeLong(Long value) {
        long v = value;
        ByteBuffer buffer;

        if (v >= -32 && v <= 127) {
            // fixint format
            buffer = ByteBuffer.allocate(1);
            buffer.put((byte) v);
        } else if (v >= Byte.MIN_VALUE && v <= Byte.MAX_VALUE) {
            // int8 format
            buffer = ByteBuffer.allocate(2);
            buffer.put((byte) 0xd0);
            buffer.put((byte) v);
        } else if (v >= Short.MIN_VALUE && v <= Short.MAX_VALUE) {
            // int16 format
            buffer = ByteBuffer.allocate(3);
            buffer.put((byte) 0xd1);
            buffer.put((byte) (v >>> 8));
            buffer.put((byte) v);
        } else if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) {
            // int32 format
            buffer = ByteBuffer.allocate(5);
            buffer.put((byte) 0xd2);
            buffer.put((byte) (v >>> 24));
            buffer.put((byte) (v >>> 16));
            buffer.put((byte) (v >>> 8));
            buffer.put((byte) v);
        } else {
            // int64 format
            buffer = ByteBuffer.allocate(9);
            buffer.put((byte) 0xd3);
            buffer.put((byte) (v >>> 56));
            buffer.put((byte) (v >>> 48));
            buffer.put((byte) (v >>> 40));
            buffer.put((byte) (v >>> 32));
            buffer.put((byte) (v >>> 24));
            buffer.put((byte) (v >>> 16));
            buffer.put((byte) (v >>> 8));
            buffer.put((byte) v);
        }

        return buffer.array();
    }

    private static byte[] serializeFloat(Float value) {
        ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.put((byte) 0xca);
        buffer.putFloat(1, value);
        return buffer.array();
    }

    private static byte[] serializeDouble(Double value) {
        ByteBuffer buffer = ByteBuffer.allocate(9);
        buffer.put((byte) 0xcb);
        buffer.putDouble(1, value);
        return buffer.array();
    }

    private static byte[] serializeBinary(byte[] value) {
        int length = value.length;
        ByteBuffer buffer;

        if (length < 256) {
            // bin8 format
            buffer = ByteBuffer.allocate(2 + length);
            buffer.put((byte) 0xc4);
            buffer.put((byte) length);
        } else if (length < 65536) {
            // bin16 format
            buffer = ByteBuffer.allocate(3 + length);
            buffer.put((byte) 0xc5);
            buffer.put((byte) (length >>> 8));
            buffer.put((byte) length);
        } else {
            // bin32 format
            buffer = ByteBuffer.allocate(5 + length);
            buffer.put((byte) 0xc6);
            buffer.put((byte) (length >>> 24));
            buffer.put((byte) (length >>> 16));
            buffer.put((byte) (length >>> 8));
            buffer.put((byte) length);
        }

        buffer.put(value);
        return buffer.array();
    }

    private static byte[] serializeInt32Array(int[] array) {
        int dataLength = array.length * 4;
        ByteBuffer buffer;

        // Always use ext32 format to match the expected format
        buffer = ByteBuffer.allocate(6 + dataLength);
        buffer.put((byte) 0xc9);
        buffer.put((byte) (dataLength >>> 24));
        buffer.put((byte) (dataLength >>> 16));
        buffer.put((byte) (dataLength >>> 8));
        buffer.put((byte) dataLength);
        buffer.put(EXT_TYPE_INT32_ARRAY);

        // Convert int[] to bytes in little-endian order
        for (int value : array) {
            buffer.put((byte) value);
            buffer.put((byte) (value >>> 8));
            buffer.put((byte) (value >>> 16));
            buffer.put((byte) (value >>> 24));
        }

        return buffer.array();
    }

    private static byte[] serializeList(List<?> list) {
        int size = list.size();
        byte[][] elements = new byte[size][];
        int totalLength = 0;

        // Serialize each element
        for (int i = 0; i < size; i++) {
            elements[i] = serialize(list.get(i));
            totalLength += elements[i].length;
        }

        ByteBuffer buffer;
        if (size < 16) {
            // fixarray format
            buffer = ByteBuffer.allocate(1 + totalLength);
            buffer.put((byte) (0x90 | size));
        } else if (size < 65536) {
            // array16 format
            buffer = ByteBuffer.allocate(3 + totalLength);
            buffer.put((byte) 0xdc);
            buffer.put((byte) (size >>> 8));
            buffer.put((byte) size);
        } else {
            // array32 format
            buffer = ByteBuffer.allocate(5 + totalLength);
            buffer.put((byte) 0xdd);
            buffer.put((byte) (size >>> 24));
            buffer.put((byte) (size >>> 16));
            buffer.put((byte) (size >>> 8));
            buffer.put((byte) size);
        }

        // Add serialized elements
        for (byte[] element : elements) {
            buffer.put(element);
        }

        return buffer.array();
    }

    private static byte[] serializeMap(Map<?, ?> map) {
        int size = map.size();
        List<byte[]> keyValues = new ArrayList<>(size * 2);
        int totalLength = 0;

        // Serialize each key-value pair
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            byte[] key = serialize(entry.getKey());
            byte[] value = serialize(entry.getValue());
            keyValues.add(key);
            keyValues.add(value);
            totalLength += key.length + value.length;
        }

        ByteBuffer buffer;
        if (size < 16) {
            // fixmap format
            buffer = ByteBuffer.allocate(1 + totalLength);
            buffer.put((byte) (0x80 | size));
        } else if (size < 65536) {
            // map16 format
            buffer = ByteBuffer.allocate(3 + totalLength);
            buffer.put((byte) 0xde);
            buffer.put((byte) (size >>> 8));
            buffer.put((byte) size);
        } else {
            // map32 format
            buffer = ByteBuffer.allocate(5 + totalLength);
            buffer.put((byte) 0xdf);
            buffer.put((byte) (size >>> 24));
            buffer.put((byte) (size >>> 16));
            buffer.put((byte) (size >>> 8));
            buffer.put((byte) size);
        }

        // Add serialized key-value pairs
        for (byte[] bytes : keyValues) {
            buffer.put(bytes);
        }

        return buffer.array();
    }

    public static byte[] serializeObject(Object object) {
        if (object == null) {
            return new byte[]{(byte) 0xc0};
        }
        Map<String, Object> map = new HashMap<>();
        Class<?> clazz = object.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            try {
                field.setAccessible(true);
                String fieldName = field.getName();
                MsgpackName annotation = field.getAnnotation(MsgpackName.class);
                if (annotation != null) {
                    fieldName = annotation.value();
                }
                Object value = field.get(object);
                map.put(fieldName, value);
            } catch (IllegalAccessException e) {
                // Skip fields that can't be accessed
            }
        }
        return serializeMap(map);
    }
}