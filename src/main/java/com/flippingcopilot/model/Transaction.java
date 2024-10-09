package com.flippingcopilot.model;

import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;


@Getter
@AllArgsConstructor
public class Transaction {

    @Setter private UUID id;
    private OfferStatus type;
    private int itemId;
    private int price;
    private int quantity;
    private int boxId;
    private int amountSpent;
    private Instant timestamp;
    private boolean copilotPriceUsed;

    public boolean equals(Transaction other) {
        return this.type == other.type &&
                this.itemId == other.itemId &&
                this.price == other.price &&
                this.quantity == other.quantity &&
                this.boxId == other.boxId &&
                this.amountSpent == other.amountSpent;
    }

    public JsonObject toJsonObject() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("id", id.toString());
        jsonObject.addProperty("item_id", itemId);
        jsonObject.addProperty("price", price);
        jsonObject.addProperty("quantity", type.equals(OfferStatus.BUY) ? quantity : -quantity);
        jsonObject.addProperty("box_id", boxId);
        jsonObject.addProperty("amount_spent", amountSpent);
        jsonObject.addProperty("time", timestamp.getEpochSecond());
        jsonObject.addProperty("copilot_price_used", copilotPriceUsed);
        return jsonObject;
    }
}


