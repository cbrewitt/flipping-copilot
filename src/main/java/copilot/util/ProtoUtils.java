package copilot.util;

import java.nio.*;
import com.google.protobuf.*;

import java.io.*;
import java.time.*;
import java.util.*;
import java.util.stream.*;

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
        return ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits()).array();
    }

    public static byte[] encodeMessage(MessageWriter messageWriter) {
        var baos = new ByteArrayOutputStream();
        var out = CodedOutputStream.newInstance(baos);
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
            out.writeByteArray(fieldNumber, messageBytes);
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
        if (map == null || map.isEmpty()) { return; }
        for (Map.Entry<K, V> entry : map.entrySet()) {
            byte[] entryBytes = encodeMessage(entryOut -> {
                keyWriter.write(entryOut, 1, entry.getKey());
                valueWriter.write(entryOut, 2, entry.getValue());
            });
            writeDelimitedMessageField(out, fieldNumber, entryBytes);
        }
    }

    public static Instant decodeTimestamp(CodedInputStream input) throws IOException {
        int length = input.readRawVarint32(), limit = input.pushLimit(length);
        Timestamp timestamp = Timestamp.parseFrom(input);
        input.popLimit(limit);
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    @FunctionalInterface
    private interface LongReader {
        long read() throws IOException;
    }

    // The server always packs repeated scalars; unpacked encoding is not supported.
    public static int[] readPackedInt32Array(CodedInputStream input) throws IOException {
        return toInts(readPacked(input, input::readInt32));
    }

    public static long[] readPackedInt64Array(CodedInputStream input) throws IOException {
        return readPacked(input, input::readInt64);
    }

    private static long[] readPacked(CodedInputStream input, LongReader reader) throws IOException {
        int limit = input.pushLimit(input.readRawVarint32());
        LongStream.Builder values = LongStream.builder();
        while (input.getBytesUntilLimit() > 0) { values.add(reader.read()); }
        input.popLimit(limit);
        return values.build().toArray();
    }

    public static int[] readDeltaInt32Array(CodedInputStream input) throws IOException {
        return toInts(readDelta(input, input::readSInt32));
    }

    public static long[] readDeltaInt64Array(CodedInputStream input) throws IOException {
        return readDelta(input, input::readSInt64);
    }

    // A base value followed by zigzag deltas: values[i] = values[i-1] + deltas[i-1].
    private static long[] readDelta(CodedInputStream input, LongReader reader) throws IOException {
        int limit = input.pushLimit(input.readRawVarint32());
        long base = 0;
        LongStream.Builder values = LongStream.builder().add(0);
        while (input.getBytesUntilLimit() > 0) {
            int tag = input.readTag(), field = WireFormat.getTagFieldNumber(tag);
            if (field == 1) { base = reader.read(); } else if (field == 2) {
                int inner = input.pushLimit(input.readRawVarint32());
                while (input.getBytesUntilLimit() > 0) { values.add(reader.read()); }
                input.popLimit(inner);
            } else {
                input.skipField(tag);
            }
        }
        input.popLimit(limit);
        long[] result = values.build().toArray();
        result[0] = base;
        for (int i = 1; i < result.length; i++) {
            result[i] += result[i - 1];
        }
        return result;
    }

    private static int[] toInts(long[] values) {
        // Narrowing after accumulation preserves the same modulo-2^32 overflow as int addition.
        return Arrays.stream(values).mapToInt(value -> (int) value).toArray();
    }

    public static <T> void writePacked(CodedOutputStream out, int fieldNumber, Collection<T> values, ValueWriter<T> valueWriter) {
        if (values == null || values.isEmpty()) { return; }

        byte[] packedBytes = encodeMessage(packedOut -> {
            for (T value : values) {
                if (value != null) { valueWriter.write(packedOut, value); }
            }
        });
        if (packedBytes.length == 0) { return; }
        writeDelimitedMessageField(out, fieldNumber, packedBytes);
    }

}
