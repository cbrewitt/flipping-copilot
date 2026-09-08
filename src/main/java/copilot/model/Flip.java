package copilot.model;

import com.google.protobuf.*;
import copilot.util.*;
import lombok.*;
import lombok.extern.slf4j.*;

import java.io.*;
import java.nio.*;
import java.util.*;

@Slf4j
@Data
public class Flip {

    public UUID id;
    public int accountId, itemId;
    public int openedTime, openedQuantity;
    public long spent;
    public int closedTime, closedQuantity;
    public long receivedPostTax, profit;
    public long taxPaid;
    public FlipStatus status;
    public int updatedTime;
    public boolean deleted;
    public int portfolioId;
    private long seqNo;
    private int userId;

    public String cachedItemName;

    public Flip setCachedItemName(String cachedItemName) {
        this.cachedItemName = cachedItemName;
        return this;
    }

    public long calculateProfit(Transaction transaction) {
        long amountToClose = Math.min(openedQuantity - closedQuantity, transaction.quantity);
        if (amountToClose <= 0 ) { return 0; }
        long gpOut = (spent * amountToClose) / openedQuantity;
        long sellPrice = transaction.amountSpent / transaction.quantity;
        long sellPricePostTax = ProfitCalculator.getPostTaxPrice(transaction.itemId, sellPrice);
        long gpIn = amountToClose * sellPricePostTax;
        return gpIn - gpOut;
    }

    public long getAvgBuyPrice() {
        if (spent == 0) { return 0; }
        return spent / openedQuantity ;
    }

    public long getAvgSellPrice() {
        if (receivedPostTax == 0) { return 0; }
        return (receivedPostTax  + taxPaid) / closedQuantity;
    }

    public static Flip decodeProto(byte[] bytes) {
        if (bytes == null || bytes.length == 0) { return null; }
        try {
            return decodeProto(CodedInputStream.newInstance(bytes));
        } catch (IOException e) {
            log.warn("failed decoding flip proto", e);
            return null;
        }
    }

    public static List<Flip> listDecodeProto(byte[] bytes) {
        List<Flip> flips = new ArrayList<>();
        if (bytes == null || bytes.length == 0) { return flips; }
        try {
            var input = CodedInputStream.newInstance(bytes);
            for (int tag; (tag = input.readTag()) != 0;) {
                int fieldNumber = WireFormat.getTagFieldNumber(tag);
                if (fieldNumber == 1) {
                    int length = input.readRawVarint32(), limit = input.pushLimit(length);
                    Flip f = decodeProto(input);
                    input.popLimit(limit);
                    if (f != null) { flips.add(f); }
                } else {
                    input.skipField(tag);
                }
            }
        } catch (IOException e) {
            log.warn("failed decoding flip list proto", e);
        }
        return flips;
    }

    public static Flip decodeProto(CodedInputStream input) throws IOException {
        var flip = new Flip();
        boolean isClosed = false;
        for (int tag; (tag = input.readTag()) != 0;) {
            int fieldNumber = WireFormat.getTagFieldNumber(tag);
            switch (fieldNumber) {
                case 1: flip.id = decodeUuid(input.readByteArray()); break;
                case 2: flip.accountId = input.readInt32(); break;
                case 3: flip.itemId = input.readInt32(); break;
                case 4: flip.cachedItemName = input.readString(); break;
                case 5: flip.openedTime = input.readInt32(); break;
                case 6: flip.openedQuantity = input.readInt32(); break;
                case 7: flip.spent = input.readInt64(); break;
                case 8: flip.closedTime = input.readInt32(); break;
                case 9: flip.closedQuantity = input.readInt32(); break;
                case 10: flip.receivedPostTax = input.readInt64(); break;
                case 11: flip.taxPaid = input.readInt64(); break;
                case 12: flip.profit = input.readInt64(); break;
                case 13: isClosed = input.readBool(); break;
                case 14: flip.status = FlipStatus.fromValue(input.readString()); break;
                case 16: flip.updatedTime = input.readInt32(); break;
                case 17: flip.deleted = input.readBool(); break;
                case 19: flip.portfolioId = input.readSInt32(); break;
                case 20: flip.seqNo = input.readInt64(); break;
                case 21: flip.userId = input.readInt32(); break;
                default:
                    input.skipField(tag);
            }
        }
        if (flip.status == null) { flip.status = isClosed ? FlipStatus.FINISHED : FlipStatus.BUYING; }
        return flip;
    }

    private static UUID decodeUuid(byte[] raw) {
        if (raw == null || raw.length != 16) { return null; }
        // Read UUID (16 bytes)
        ByteBuffer b = ByteBuffer.wrap(raw);
        return new UUID(b.getLong(), b.getLong());
    }

    public boolean isClosed() { return Objects.equals(status, FlipStatus.FINISHED); }

    public int lastTransactionTime() { return closedTime == 0 ? openedTime : closedTime; }

    public boolean isNewer(Flip o) {
        if (updatedTime == o.updatedTime) {
            return closedQuantity > o.closedQuantity || (closedQuantity == o.closedQuantity && openedQuantity >= o.openedQuantity);
        }
        return updatedTime > o.updatedTime;
    }
}
