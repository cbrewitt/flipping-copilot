package copilot.util;

import com.google.protobuf.*;
import org.junit.Test;
import java.time.Instant;
import static org.junit.Assert.*;

public class ProtoTimestampTest {
    @Test public void timestampsPreserveDefaultsRepeatedFieldsAndOuterStream() throws Exception {
        for (long seconds : new long[]{0, -1, 1, 1700000000, -62135596800L, 253402300799L}) {
            for (int nanos : new int[]{0, 1, 999999999, -1, 1000000001}) {
                byte[] timestamp = ProtoUtils.encodeMessage(out -> {
                    out.writeInt64(1, 7);
                    out.writeInt32(2, 3);
                    out.writeString(25, "unknown timestamp field");
                    out.writeInt64(1, seconds);
                    out.writeInt32(2, nanos);
                });
                byte[] envelope = ProtoUtils.encodeMessage(out -> {
                    ProtoUtils.writeDelimitedMessageField(out, 4, timestamp);
                    out.writeInt32(9, 123);
                });
                CodedInputStream input = CodedInputStream.newInstance(envelope);
                assertEquals(34, input.readTag());
                assertEquals(Instant.ofEpochSecond(seconds, nanos), ProtoUtils.decodeTimestamp(input));
                assertEquals(72, input.readTag());
                assertEquals(123, input.readInt32());
                assertEquals(0, input.readTag());
            }
        }
        assertEquals(Instant.EPOCH, ProtoUtils.decodeTimestamp(CodedInputStream.newInstance(new byte[]{0})));
    }
}
