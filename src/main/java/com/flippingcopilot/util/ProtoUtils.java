package com.flippingcopilot.util;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Map;

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
