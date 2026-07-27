package com.flippingcopilot.util;

import com.google.protobuf.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.*;

public final class ProtoUtils {

    private ProtoUtils() {
    }

    @FunctionalInterface
    public interface TaggedFieldWriter<T> {
        void write(CodedOutputStream out, int fieldNumber, T value) throws IOException;
    }

    @FunctionalInterface
    public interface ValueWriter<T> {
        void write(CodedOutputStream out, T value) throws IOException;
    }

    @FunctionalInterface
    public interface MessageWriter {
        void write(CodedOutputStream out) throws IOException;
    }

    public static byte[] uuidToBytes(UUID uuid) {
        ByteBuffer b = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN);
        b.putLong(uuid.getMostSignificantBits());
        b.putLong(uuid.getLeastSignificantBits());
        return b.array();
    }

    public static byte[] encodeMessage(MessageWriter messageWriter) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CodedOutputStream out = CodedOutputStream.newInstance(baos);
        try {
            messageWriter.write(out);
            out.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void writeDelimitedMessageField(CodedOutputStream out, int fieldNumber, byte[] messageBytes) {
        try {
            out.writeTag(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
            out.writeUInt32NoTag(messageBytes.length);
            out.writeRawBytes(messageBytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static <K, V> void writeMap(
            CodedOutputStream out,
            int fieldNumber,
            Map<K, V> map,
            TaggedFieldWriter<K> keyWriter,
            TaggedFieldWriter<V> valueWriter) {
        if (map == null || map.isEmpty()) {
            return;
        }
        for (Map.Entry<K, V> entry : map.entrySet()) {
            byte[] entryBytes = encodeMessage(entryOut -> {
                keyWriter.write(entryOut, 1, entry.getKey());
                valueWriter.write(entryOut, 2, entry.getValue());
            });
            writeDelimitedMessageField(out, fieldNumber, entryBytes);
        }
    }

    public static Instant decodeTimestamp(CodedInputStream input) throws IOException {
        int length = input.readRawVarint32();
        int limit = input.pushLimit(length);
        long seconds = 0L;
        int nanos = 0;
        while (!input.isAtEnd()) {
            int tag = input.readTag();
            if (tag == 0) {
                break;
            }
            int fieldNumber = WireFormat.getTagFieldNumber(tag);
            switch (fieldNumber) {
                case 1:
                    seconds = input.readInt64();
                    break;
                case 2:
                    nanos = input.readInt32();
                    break;
                default:
                    input.skipField(tag);
            }
        }
        input.popLimit(limit);
        return Instant.ofEpochSecond(seconds, nanos);
    }

    // the server always packs repeated scalars, so the unpacked encoding is not supported
    public static int[] readPackedInt32Array(CodedInputStream input) throws IOException {
        int limit = input.pushLimit(input.readRawVarint32());
        List<Integer> values = new ArrayList<>();
        while (input.getBytesUntilLimit() > 0) {
            values.add(input.readInt32());
        }
        input.popLimit(limit);
        int[] array = new int[values.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = values.get(i);
        }
        return array;
    }

    public static long[] readPackedInt64Array(CodedInputStream input) throws IOException {
        int limit = input.pushLimit(input.readRawVarint32());
        List<Long> values = new ArrayList<>();
        while (input.getBytesUntilLimit() > 0) {
            values.add(input.readInt64());
        }
        input.popLimit(limit);
        long[] array = new long[values.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = values.get(i);
        }
        return array;
    }

    // a PackedInt32Array is a base value plus successive zigzag deltas: vals[0] = base, vals[i] = vals[i-1] + deltas[i-1]
    public static int[] readDeltaInt32Array(CodedInputStream input) throws IOException {
        int limit = input.pushLimit(input.readRawVarint32());
        int base = 0;
        List<Integer> deltas = new ArrayList<>();
        while (input.getBytesUntilLimit() > 0) {
            int tag = input.readTag();
            int field = WireFormat.getTagFieldNumber(tag);
            if (field == 1) {
                base = input.readSInt32();
            } else if (field == 2) {
                int inner = input.pushLimit(input.readRawVarint32());
                while (input.getBytesUntilLimit() > 0) {
                    deltas.add(input.readSInt32());
                }
                input.popLimit(inner);
            } else {
                input.skipField(tag);
            }
        }
        input.popLimit(limit);
        int[] vals = new int[deltas.size() + 1];
        vals[0] = base;
        for (int i = 0; i < deltas.size(); i++) {
            vals[i + 1] = vals[i] + deltas.get(i);
        }
        return vals;
    }

    public static long[] readDeltaInt64Array(CodedInputStream input) throws IOException {
        int limit = input.pushLimit(input.readRawVarint32());
        long base = 0L;
        List<Long> deltas = new ArrayList<>();
        while (input.getBytesUntilLimit() > 0) {
            int tag = input.readTag();
            int field = WireFormat.getTagFieldNumber(tag);
            if (field == 1) {
                base = input.readSInt64();
            } else if (field == 2) {
                int inner = input.pushLimit(input.readRawVarint32());
                while (input.getBytesUntilLimit() > 0) {
                    deltas.add(input.readSInt64());
                }
                input.popLimit(inner);
            } else {
                input.skipField(tag);
            }
        }
        input.popLimit(limit);
        long[] vals = new long[deltas.size() + 1];
        vals[0] = base;
        for (int i = 0; i < deltas.size(); i++) {
            vals[i + 1] = vals[i] + deltas.get(i);
        }
        return vals;
    }

    public static <T> void writePacked(CodedOutputStream out, int fieldNumber, Collection<T> values, ValueWriter<T> valueWriter) {
        if (values == null || values.isEmpty()) {
            return;
        }

        byte[] packedBytes = encodeMessage(packedOut -> {
            for (T value : values) {
                if (value != null) {
                    valueWriter.write(packedOut, value);
                }
            }
        });
        if (packedBytes.length == 0) {
            return;
        }
        writeDelimitedMessageField(out, fieldNumber, packedBytes);
    }

}
