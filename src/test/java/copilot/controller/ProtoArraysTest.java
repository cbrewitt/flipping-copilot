package copilot.controller;

import copilot.util.ProtoUtils;
import com.google.protobuf.CodedInputStream;
import org.junit.Test;
import java.io.IOException;
import java.util.Arrays;
import static org.junit.Assert.*;

public class ProtoArraysTest {
    private static byte[] delimited(byte[] message) {
        return ProtoUtils.encodeMessage(out -> {
            out.writeUInt32NoTag(message.length);
            out.writeRawBytes(message);
        });
    }

    @Test public void packedArraysPreserveSignedExtremesAndRestoreOuterLimit() throws Exception {
        int[] ints = {0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE};
        long[] longs = {0, -1, Long.MIN_VALUE, Long.MAX_VALUE};
        byte[] encoded = ProtoUtils.encodeMessage(out -> {
            out.writeRawBytes(delimited(ProtoUtils.encodeMessage(values -> {
                for (int value : ints) values.writeInt32NoTag(value);
            })));
            out.writeRawBytes(delimited(ProtoUtils.encodeMessage(values -> {
                for (long value : longs) values.writeInt64NoTag(value);
            })));
            out.writeInt32NoTag(37);
        });
        CodedInputStream input = CodedInputStream.newInstance(encoded);
        assertArrayEquals(ints, ProtoUtils.readPackedInt32Array(input));
        assertArrayEquals(longs, ProtoUtils.readPackedInt64Array(input));
        assertEquals(37, input.readInt32());
        assertTrue(input.isAtEnd());
    }

    @Test public void deltasAllowBaseAfterDeltasRepeatedPacksAndUnknownFields() throws Exception {
        byte[] encoded = delimited(ProtoUtils.encodeMessage(out -> {
            ProtoUtils.writePacked(out, 2, Arrays.asList(1, 1), (values, value) -> values.writeSInt32NoTag(value));
            out.writeString(10, "future field");
            out.writeSInt32(1, 100);
            out.writeSInt32(1, Integer.MAX_VALUE);
            ProtoUtils.writePacked(out, 2, Arrays.asList(-2, Integer.MIN_VALUE), (values, value) -> values.writeSInt32NoTag(value));
        }));
        assertArrayEquals(new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE + 1,
                Integer.MAX_VALUE, -1}, ProtoUtils.readDeltaInt32Array(CodedInputStream.newInstance(encoded)));
        byte[] wide = delimited(ProtoUtils.encodeMessage(out -> {
            out.writeSInt64(1, Long.MAX_VALUE);
            ProtoUtils.writePacked(out, 2, Arrays.asList(1L, -1L, Long.MIN_VALUE), (values, value) -> values.writeSInt64NoTag(value));
        }));
        assertArrayEquals(new long[]{Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, -1},
                ProtoUtils.readDeltaInt64Array(CodedInputStream.newInstance(wide)));
    }

    @Test public void emptyArraysAndBaseOnlyMessagesKeepTheirDefaults() throws Exception {
        byte[] empty = {0};
        assertArrayEquals(new int[0], ProtoUtils.readPackedInt32Array(CodedInputStream.newInstance(empty)));
        assertArrayEquals(new long[0], ProtoUtils.readPackedInt64Array(CodedInputStream.newInstance(empty)));
        assertArrayEquals(new int[]{0}, ProtoUtils.readDeltaInt32Array(CodedInputStream.newInstance(empty)));
        assertArrayEquals(new long[]{0}, ProtoUtils.readDeltaInt64Array(CodedInputStream.newInstance(empty)));
        byte[] base = delimited(ProtoUtils.encodeMessage(out -> out.writeSInt64(1, -71)));
        assertArrayEquals(new long[]{-71}, ProtoUtils.readDeltaInt64Array(CodedInputStream.newInstance(base)));
    }

    @Test public void truncatedLengthsAndVarintsAreRejected() {
        for (byte[] malformed : new byte[][]{{2, 1}, {1, (byte) 0x80}}) {
            assertThrows(IOException.class, () -> ProtoUtils.readPackedInt32Array(CodedInputStream.newInstance(malformed)));
            assertThrows(IOException.class, () -> ProtoUtils.readPackedInt64Array(CodedInputStream.newInstance(malformed)));
        }
        byte[] badDelta = {2, 8, (byte) 0x80};
        assertThrows(IOException.class, () -> ProtoUtils.readDeltaInt32Array(CodedInputStream.newInstance(badDelta)));
        assertThrows(IOException.class, () -> ProtoUtils.readDeltaInt64Array(CodedInputStream.newInstance(badDelta)));
    }
}
