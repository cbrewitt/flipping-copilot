package copilot.model;

import copilot.util.ProtoUtils;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

public class TransactionCodecTest {
    @Test public void rawCodecMatchesFixedNetworkByteLayout() {
        AckedTransaction transaction = new AckedTransaction(
                UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"),
                UUID.fromString("ffeeddcc-bbaa-9988-7766-554433221100"),
                0x10203040, 0x50607080, 0x11223344, -7, 0x0102030405060708L, -123);
        byte[] expected = hex("00112233445566778899aabbccddeeff"
                + "ffeeddccbbaa99887766554433221100"
                + "102030405060708011223344fffffff9"
                + "0102030405060708ffffffffffffff85");
        assertEquals(64, expected.length);
        assertArrayEquals(expected, transaction.toRaw());
        assertEquals(transaction, AckedTransaction.fromRaw(expected));
    }

    @Test public void rawCodecRejectsWrongRecordLengths() {
        for (int size : new int[]{0, 63, 65}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> AckedTransaction.fromRaw(new byte[size]));
            assertEquals("Raw data must be exactly 64 bytes", error.getMessage());
        }
    }

    @Test public void malformedProtoUuidStillProducesNoRawRecord() {
        byte[] transaction = ProtoUtils.encodeMessage(out -> out.writeByteArray(1, new byte[15]));
        byte[] list = ProtoUtils.encodeMessage(out -> out.writeByteArray(1, transaction));
        assertArrayEquals(new byte[0], AckedTransaction.listDecodeProto(list));
    }

    private static byte[] hex(String value) {
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        return bytes;
    }
}
