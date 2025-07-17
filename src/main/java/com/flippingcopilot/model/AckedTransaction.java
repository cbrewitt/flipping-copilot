package com.flippingcopilot.model;
import lombok.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public class AckedTransaction {

    public static final int RAW_SIZE = 56;

    private UUID id;
    private UUID clientFlipId;
    private int accountId;
    private int time;
    private int itemId;
    private int quantity;
    private int price;
    private int amountSpent;

    public static AckedTransaction fromRaw(byte[] raw) {
        if (raw.length != RAW_SIZE) {
            throw new IllegalArgumentException("Raw data must be exactly " + RAW_SIZE + " bytes");
        }

        ByteBuffer b = ByteBuffer.wrap(raw);
        b.order(ByteOrder.BIG_ENDIAN);
        AckedTransaction transaction = new AckedTransaction();

        long idMostSig = b.getLong();
        long idLeastSig = b.getLong();
        transaction.id = new UUID(idMostSig, idLeastSig);
        long clientFlipIdMostSig = b.getLong();
        long clientFlipIdLeastSig = b.getLong();
        transaction.clientFlipId = new UUID(clientFlipIdMostSig, clientFlipIdLeastSig);
        transaction.accountId = b.getInt();
        transaction.time = b.getInt();
        transaction.itemId = b.getInt();
        transaction.quantity = b.getInt();
        transaction.price = b.getInt();
        transaction.amountSpent = b.getInt();
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
        b.putInt(price);
        b.putInt(amountSpent);
        return b.array();
    }
}