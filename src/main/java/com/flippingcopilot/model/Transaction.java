package com.flippingcopilot.model;

import com.google.gson.JsonObject;
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
    private int price;
    private int quantity;
    private int boxId;
    private int amountSpent;
    private Instant timestamp;
    private boolean copilotPriceUsed;
    private boolean wasCopilotSuggestion;
    private int offerTotalQuantity;
    private boolean login;
    private boolean consistent;
    private boolean geTransactionAlreadyAdded;

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
        jsonObject.addProperty("was_copilot_suggestion", wasCopilotSuggestion);
        jsonObject.addProperty("consistent_previous_offer", consistent);
        jsonObject.addProperty("login", login);
        return jsonObject;
    }

    @Override
    public String toString() {
        return String.format("%s %d %d on slot %d", type, quantity, itemId, boxId);
    }
}


