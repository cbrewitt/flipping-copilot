package com.flippingcopilot.util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MsgPackUtil {

    private static final int EXT_INT32 = 41;

    public static Object decodePrimitive(ByteBuffer b) {
        int format = b.get() & 0xFF;
        int length;

        // Boolean cases
        if (format == 0xC2) {
            // false
            return false;
        } else if (format == 0xC3) {
            // true
            return true;
        }
        // Null case
        else if (format == 0xC0) {
            // nil (null)
            return null;
        }
        // String cases
        else if ((format & 0xE0) == 0xA0) {
            // fixstr: format stores length in lower 5 bits
            length = format & 0x1F;
            return getString(length, new byte[length], b);
        } else if (format == 0xD9) {
            // str 8: next byte is length
            length = b.get() & 0xFF;
            return getString(length, new byte[length], b);
        } else if (format == 0xDA) {
            // str 16: next 2 bytes are length
            length = b.getShort() & 0xFFFF;
            return getString(length, new byte[length], b);
        } else if (format == 0xDB) {
            // str 32: next 4 bytes are length
            length = b.getInt();
            return getString(length, new byte[length], b);
        }
        // Integer cases
        else if (format <= 0x7F) {
            // positive fixint
            return (long) format;
        } else if ((format & 0xE0) == 0xE0) {
            // negative fixint (0xE0-0xFF represents -32 to -1)
            return (long) (format - 256);
        } else if (format == 0xCC) {
            // uint 8
            return (long) (b.get() & 0xFF);
        } else if (format == 0xCD) {
            // uint 16
            return (long) (b.getShort() & 0xFFFF);
        } else if (format == 0xCE) {
            // uint 32
            return (long) (b.getInt() & 0xFFFFFFFFL);
        } else if (format == 0xCF) {
            // uint 64
            return b.getLong();
        } else if (format == 0xD0) {
            // int 8
            return (long) b.get();
        } else if (format == 0xD1) {
            // int 16
            return (long) b.getShort();
        } else if (format == 0xD2) {
            // int 32
            return (long) b.getInt();
        } else if (format == 0xD3) {
            // int 64
            return b.getLong();
        }
        // Double case
        else if (format == 0xCB) {
            // float 64
            return b.getDouble();
        } else {
            throw new IllegalArgumentException("Invalid primitive format: " + format);
        }
    }

    private static String getString(int length, byte[] length1, ByteBuffer b) {
        if (length == 0) {
            return "";
        }
        byte[] bytes = length1;
        b.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static int[] decodeInt32Array(ByteBuffer b) {
        int format = b.get() & 0xFF;

        if (format == 0xC0) {
            // nil (null)
            return null;
        } else if (format == 0xC9) {
            // ext 32 - extension with 32-bit length
            int byteLength = b.getInt();
            int extType = b.get() & 0xFF;
            if (extType != EXT_INT32) {
                throw new IllegalArgumentException("Expected extension type " + EXT_INT32 + ", got: " + extType);
            }
            int arrayLength = byteLength / 4;
            int[] result = new int[arrayLength];
            for (int i = 0; i < arrayLength; i++) {
                result[i] = (b.get() & 0xff) | ((b.get() & 0xff) << 8) |
                        ((b.get() & 0xff) << 16) | ((b.get() & 0xff) << 24);
            }
            return result;
        } else {
            throw new IllegalArgumentException("Expected extension format 0xC9 or nil 0xC0, got: " + format);
        }
    }

    public static Integer decodeMapSize(ByteBuffer b) {
        int format = b.get() & 0xFF;
        if (format == 0xC0) {
            // nil (null) - return null Data object
            return null;
        } else if ((format & 0xF0) == 0x80) {
            // fixmap: format stores size in lower 4 bits
            return format & 0x0F;
        } else if (format == 0xDE) {
            // map 16: next 2 bytes are size
            return b.getShort() & 0xFFFF;
        } else if (format == 0xDF) {
            // map 32: next 4 bytes are size (you were missing this case!)
            return b.getInt();
        } else {
            throw new IllegalArgumentException("Invalid map format: " + format);
        }
    }
}