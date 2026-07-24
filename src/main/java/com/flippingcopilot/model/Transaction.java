package com.flippingcopilot.model;

import com.flippingcopilot.util.ProtoUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;


@Getter
@AllArgsConstructor
@NoArgsConstructor
@Setter
public class Transaction {

    private UUID id;
    private OfferStatus type;
    private int itemId;
    private long price;
    private int quantity;
    private int boxId;
    private long amountSpent;
    private Instant timestamp;
    private boolean copilotPriceUsed;
    private boolean wasCopilotSuggestion;
    private boolean login;
    private boolean consistent;

    public boolean equals(Transaction other) {
        return this.type == other.type &&
                this.itemId == other.itemId &&
                this.price == other.price &&
                this.quantity == other.quantity &&
                this.boxId == other.boxId &&
                this.amountSpent == other.amountSpent;
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
    public String toString() {
        return String.format("%s %d %d on slot %d", type, quantity, itemId, boxId);
    }
}
