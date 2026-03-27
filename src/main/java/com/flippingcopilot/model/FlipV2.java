package com.flippingcopilot.model;

import com.flippingcopilot.util.ProfitCalculator;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Data
public class FlipV2 {

    public static final int RAW_SIZE = 84;

    private UUID id;
    private int accountId;
    private int itemId;
    private int openedTime;
    private int openedQuantity;
    private long spent;
    private int closedTime;
    private int closedQuantity;
    private long receivedPostTax;
    private long profit;
    private long taxPaid;
    private FlipStatus status;
    private int updatedTime;
    private boolean deleted;
    private int portfolioId;

    private String cachedItemName;

    public FlipV2 setCachedItemName(String cachedItemName) {
        this.cachedItemName = cachedItemName;
        return this;
    }

    public long calculateProfit(Transaction transaction) {
        long amountToClose = Math.min(openedQuantity - closedQuantity, transaction.getQuantity());
        if(amountToClose <= 0 ){
            return 0;
        }
        long gpOut = (spent * amountToClose) / openedQuantity;
        int sellPrice  = transaction.getAmountSpent() / transaction.getQuantity();
        int sellPricePostTax = ProfitCalculator.getPostTaxPrice(transaction.getItemId(), sellPrice);
        long gpIn = amountToClose * sellPricePostTax;
        return gpIn - gpOut;
    }

    public long getAvgBuyPrice() {
        if (spent == 0) {
            return 0;
        }
        return spent / openedQuantity ;
    }

    public long getAvgSellPrice() {
        if (receivedPostTax == 0) {
            return 0;
        }
        return (receivedPostTax  + taxPaid) / closedQuantity;
    }

    public static List<FlipV2> listFromRaw(byte[] raw) {
        if (raw.length < 4) {
            throw new IllegalArgumentException("Raw data must be at least 4 bytes to contain record count");
        }

        ByteBuffer b = ByteBuffer.wrap(raw);
        b.order(ByteOrder.BIG_ENDIAN);

        // Read the number of records (int32)
        int recordCount = b.getInt();
        int expectedSize = 4 + (recordCount * RAW_SIZE);
        if (raw.length != expectedSize) {
            throw new IllegalArgumentException("Raw data size " + raw.length + " doesn't match expected size " + expectedSize + " for " + recordCount + " records");
        }
        List<FlipV2> flips = new ArrayList<>(recordCount);
        for (int i = 0; i < recordCount; i++) {
            byte[] recordBytes = new byte[RAW_SIZE];
            b.get(recordBytes);
            flips.add(fromRaw(recordBytes));
        }

        return flips;
    }

    public static FlipV2 fromRaw(byte[] raw) {
        if (raw.length != RAW_SIZE) {
            throw new IllegalArgumentException("Raw data must be exactly " + RAW_SIZE + " bytes");
        }

        ByteBuffer b = ByteBuffer.wrap(raw);
        b.order(ByteOrder.BIG_ENDIAN);
        FlipV2 flip = new FlipV2();

        // Read UUID (16 bytes)
        long idMostSig = b.getLong();
        long idLeastSig = b.getLong();
        flip.id = new UUID(idMostSig, idLeastSig);

        // Read primitive fields
        flip.accountId = b.getInt();           // 4 bytes
        flip.itemId = b.getInt();              // 4 bytes
        flip.openedTime = b.getInt();          // 4 bytes
        flip.openedQuantity = b.getInt();      // 4 bytes
        flip.spent = b.getLong();              // 8 bytes
        flip.closedTime = b.getInt();          // 4 bytes
        flip.closedQuantity = b.getInt();      // 4 bytes
        flip.receivedPostTax = b.getLong();    // 8 bytes
        flip.profit = b.getLong();             // 8 bytes
        flip.taxPaid = b.getLong();            // 8 bytes

        // Read FlipStatus as int32 ordinal
        int statusOrdinal = b.getInt();        // 4 bytes
        flip.status = FlipStatus.values()[statusOrdinal];

        flip.updatedTime = b.getInt();         // 4 bytes
        flip.deleted = b.getInt() > 0;

        return flip;
    }

    public static FlipV2 decodeProto(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        FlipV2 flip = new FlipV2();
        boolean isClosed = false;

        try {
            CodedInputStream input = CodedInputStream.newInstance(bytes);
            while (!input.isAtEnd()) {
                int tag = input.readTag();
                if (tag == 0) {
                    break;
                }

                int fieldNumber = WireFormat.getTagFieldNumber(tag);
                switch (fieldNumber) {
                    case 1:
                        flip.id = decodeUuid(input.readByteArray());
                        break;
                    case 2:
                        flip.accountId = input.readInt32();
                        break;
                    case 3:
                        flip.itemId = input.readInt32();
                        break;
                    case 4:
                        flip.cachedItemName = input.readString();
                        break;
                    case 5:
                        flip.openedTime = input.readInt32();
                        break;
                    case 6:
                        flip.openedQuantity = input.readInt32();
                        break;
                    case 7:
                        flip.spent = input.readInt64();
                        break;
                    case 8:
                        flip.closedTime = input.readInt32();
                        break;
                    case 9:
                        flip.closedQuantity = input.readInt32();
                        break;
                    case 10:
                        flip.receivedPostTax = input.readInt64();
                        break;
                    case 11:
                        flip.taxPaid = input.readInt64();
                        break;
                    case 12:
                        flip.profit = input.readInt64();
                        break;
                    case 13:
                        isClosed = input.readBool();
                        break;
                    case 14:
                        flip.status = FlipStatus.fromValue(input.readString());
                        break;
                    case 16:
                        flip.updatedTime = input.readInt32();
                        break;
                    case 17:
                        flip.deleted = input.readBool();
                        break;
                    case 19:
                        flip.portfolioId = input.readSInt32();
                        break;
                    default:
                        input.skipField(tag);
                }
            }
        } catch (IOException e) {
            log.warn("failed decoding flip proto", e);
            return null;
        }

        if (flip.status == null) {
            flip.status = isClosed ? FlipStatus.FINISHED : FlipStatus.BUYING;
        }
        return flip;
    }

    private static UUID decodeUuid(byte[] raw) {
        if (raw == null || raw.length != 16) {
            return null;
        }
        ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);
        return new UUID(b.getLong(), b.getLong());
    }

    public boolean isClosed() {
        return Objects.equals(status, FlipStatus.FINISHED);
    }

    public int lastTransactionTime() {
        return closedTime == 0 ? openedTime : closedTime;
    }

    public boolean isNewer(FlipV2 o) {
        if (updatedTime == o.updatedTime) {
            return closedQuantity > o.closedQuantity || (closedQuantity == o.closedQuantity && openedQuantity >= o.openedQuantity);
        }
        return updatedTime > o.updatedTime;
    }
}
