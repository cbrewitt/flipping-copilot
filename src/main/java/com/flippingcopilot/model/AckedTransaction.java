package com.flippingcopilot.model;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@ToString
@Slf4j
public class AckedTransaction {

    public static final int RAW_SIZE = 64;

    private UUID id;
    private UUID clientFlipId;
    private int accountId;
    private int time;
    private int itemId;
    private int quantity;
    private long price;
    private long amountSpent;

    public static AckedTransaction fromRaw(byte[] raw) {
        if (raw.length != RAW_SIZE) {
            throw new IllegalArgumentException("Raw data must be exactly " + RAW_SIZE + " bytes");
        }

        ByteBuffer b = ByteBuffer.wrap(raw);
        b.order(ByteOrder.BIG_ENDIAN);
        AckedTransaction transaction = new AckedTransaction();

        // Read UUID (16 bytes)
        long idMostSig = b.getLong();
        long idLeastSig = b.getLong();
        transaction.id = new UUID(idMostSig, idLeastSig);
        long clientFlipIdMostSig = b.getLong();
        long clientFlipIdLeastSig = b.getLong();
        transaction.clientFlipId = new UUID(clientFlipIdMostSig, clientFlipIdLeastSig);

        // Read primitive fields
        transaction.accountId = b.getInt();
        transaction.time = b.getInt();
        transaction.itemId = b.getInt();
        transaction.quantity = b.getInt();
        transaction.price = b.getLong();
        transaction.amountSpent = b.getLong();
        return transaction;
    }

    public byte[] toRaw() {
        ByteBuffer b = ByteBuffer.allocate(RAW_SIZE);
        b.order(ByteOrder.BIG_ENDIAN);
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
        ByteArrayOutputStream rawTransactions = new ByteArrayOutputStream();
        if (bytes == null || bytes.length == 0) {
            return rawTransactions.toByteArray();
        }
        try {
            CodedInputStream input = CodedInputStream.newInstance(bytes);
            while (!input.isAtEnd()) {
                int tag = input.readTag();
                if (tag == 0) {
                    break;
                }
                if (WireFormat.getTagFieldNumber(tag) == 1) {
                    AckedTransaction transaction = decodeProto(input.readByteArray());
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
        AckedTransaction transaction = new AckedTransaction();
        transaction.clientFlipId = new UUID(0L, 0L);
        CodedInputStream input = CodedInputStream.newInstance(bytes);
        while (!input.isAtEnd()) {
            int tag = input.readTag();
            if (tag == 0) {
                break;
            }
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    transaction.id = decodeUuid(input.readByteArray());
                    break;
                case 2:
                    transaction.clientFlipId = decodeUuid(input.readByteArray());
                    break;
                case 3:
                    transaction.accountId = input.readInt32();
                    break;
                case 4:
                    transaction.time = input.readInt32();
                    break;
                case 5:
                    transaction.itemId = input.readInt32();
                    break;
                case 6:
                    transaction.quantity = input.readInt32();
                    break;
                case 11:
                    transaction.price = input.readInt64();
                    break;
                case 12:
                    transaction.amountSpent = input.readInt64();
                    break;
                default:
                    input.skipField(tag);
            }
        }
        return transaction;
    }

    private static UUID decodeUuid(byte[] bytes) throws IOException {
        if (bytes.length != 16) {
            throw new IOException("UUID data must be exactly 16 bytes");
        }
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        return new UUID(b.getLong(), b.getLong());
    }
}
