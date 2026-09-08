package copilot.model;

import copilot.util.*;
import lombok.*;

import java.time.*;
import java.util.*;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Setter
public class Transaction {

    public UUID id;
    public OfferStatus type;
    public int itemId;
    private long price;
    public int quantity, boxId;
    public long amountSpent;
    private Instant timestamp;
    private boolean copilotPriceUsed, wasCopilotSuggestion;
    private boolean login, consistent;

    public boolean equals(Transaction other) {
        return type == other.type &&
                itemId == other.itemId &&
                price == other.price &&
                quantity == other.quantity &&
                boxId == other.boxId &&
                amountSpent == other.amountSpent;
    }

    public byte[] encodeProto() {
        return ProtoUtils.encodeMessage(out -> {
            out.writeByteArray(1, ProtoUtils.uuidToBytes(id));
            out.writeInt32(4, Math.toIntExact(timestamp.getEpochSecond()));
            out.writeInt32(5, itemId);
            out.writeInt32(6, type.equals(OfferStatus.BUY) ? quantity : -quantity);
            out.writeBool(9, copilotPriceUsed);
            out.writeBool(10, wasCopilotSuggestion);
            out.writeInt64(11, price);
            out.writeInt64(12, amountSpent);
        });
    }

    @Override
    public String toString() { return String.format("%s %d %d on slot %d", type, quantity, itemId, boxId); }
}
