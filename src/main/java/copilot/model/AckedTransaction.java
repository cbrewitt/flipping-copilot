package copilot.model;
import lombok.*;
import lombok.extern.slf4j.*;

import java.nio.*;
import java.io.*;
import com.google.protobuf.*;
import java.util.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@ToString
@Slf4j
public class AckedTransaction {

    public static final int RAW_SIZE = 64;

    public UUID id, clientFlipId;
    public int accountId, time;
    public int itemId, quantity;
    public long price, amountSpent;

    public static AckedTransaction fromRaw(byte[] raw) {
        if (raw.length != RAW_SIZE) {
            throw new IllegalArgumentException("Raw data must be exactly " + RAW_SIZE + " bytes");
        }

        ByteBuffer b = ByteBuffer.wrap(raw);
        var transaction = new AckedTransaction();

        transaction.id = readUuid(b);
        transaction.clientFlipId = readUuid(b);

        // Read primitive fields
        transaction.accountId = b.getInt(); transaction.time = b.getInt(); transaction.itemId = b.getInt();
        transaction.quantity = b.getInt(); transaction.price = b.getLong(); transaction.amountSpent = b.getLong();
        return transaction;
    }

    public byte[] toRaw() {
        ByteBuffer b = ByteBuffer.allocate(RAW_SIZE);
        b.putLong(id.getMostSignificantBits());
        b.putLong(id.getLeastSignificantBits());
        b.putLong(clientFlipId.getMostSignificantBits());
        b.putLong(clientFlipId.getLeastSignificantBits());
        b.putInt(accountId);
        b.putInt(time);
        b.putInt(itemId);
        b.putInt(quantity);
        b.putLong(price);
        b.putLong(amountSpent);
        return b.array();
    }

    public static byte[] listDecodeProto(byte[] bytes) {
        var rawTransactions = new ByteArrayOutputStream();
        if (bytes == null || bytes.length == 0) { return rawTransactions.toByteArray(); }
        try {
            var input = CodedInputStream.newInstance(bytes);
            for (int tag; (tag = input.readTag()) != 0;) {
                if (WireFormat.getTagFieldNumber(tag) == 1) {
                    var transaction = decodeProto(input.readByteArray());
                    if (transaction != null) {
                        byte[] raw = transaction.toRaw();
                        rawTransactions.write(raw, 0, raw.length);
                    }
                } else {
                    input.skipField(tag);
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            log.warn("failed decoding client transactions proto", e);
        }
        return rawTransactions.toByteArray();
    }

    private static AckedTransaction decodeProto(byte[] bytes) throws IOException {
        var transaction = new AckedTransaction();
        transaction.clientFlipId = new UUID(0L, 0L);
        var input = CodedInputStream.newInstance(bytes);
        for (int tag; (tag = input.readTag()) != 0;) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1: transaction.id = decodeUuid(input.readByteArray()); break;
                case 2: transaction.clientFlipId = decodeUuid(input.readByteArray()); break;
                case 3: transaction.accountId = input.readInt32(); break;
                case 4: transaction.time = input.readInt32(); break;
                case 5: transaction.itemId = input.readInt32(); break;
                case 6: transaction.quantity = input.readInt32(); break;
                case 11: transaction.price = input.readInt64(); break;
                case 12: transaction.amountSpent = input.readInt64(); break;
                default:
                    input.skipField(tag);
            }
        }
        return transaction;
    }

    private static UUID decodeUuid(byte[] bytes) throws IOException {
        if (bytes.length != 16) { throw new IOException("UUID data must be exactly 16 bytes"); }
        return readUuid(ByteBuffer.wrap(bytes));
    }
    // Newly allocated/wrapped ByteBuffers use network (big-endian) byte order.
    private static UUID readUuid(ByteBuffer buffer) { return new UUID(buffer.getLong(), buffer.getLong()); }
}
