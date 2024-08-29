package com.flippingcopilot.model;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.GrandExchangeOfferChanged;

import java.time.Instant;

import static net.runelite.api.ItemID.COINS_995;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class Offer {
    private OfferStatus status;

    @SerializedName("item_id")
    private int itemId;

    private int price;

    @SerializedName("amount_total")
    private int amountTotal;

    @SerializedName("amount_spent")
    private int amountSpent;

    @SerializedName("amount_traded")
    private int amountTraded;

    @SerializedName("items_to_collect")
    private int itemsToCollect;

    @SerializedName("gp_to_collect")
    private int gpToCollect;

    @SerializedName("box_id")
    private int boxId;

    private boolean active;

    @SerializedName("copilot_price_used")
    private boolean copilotPriceUsed;

    public static Offer getEmptyOffer(int slotId) {
        return new Offer(OfferStatus.EMPTY, 0, 0, 0, 0, 0, 0, 0, slotId, false, false);
    }

    void removeCollectables() {
        itemsToCollect = 0;
        gpToCollect = 0;
    }

    RSItem removeCollectedItem(int itemId) {
        int amountCollected;
        if (itemId == COINS_995) {
            amountCollected = gpToCollect;
            gpToCollect = 0;
        } else {
            amountCollected = itemsToCollect;
            itemsToCollect = 0;
        }
        return new RSItem(itemId, amountCollected);
    }

    Offer getUpdatedOffer(GrandExchangeOfferChanged event) {
        Offer newOffer = Offer.fromRuneliteEvent(event);
        if (isSameOffer(newOffer)) {
            newOffer.addUncollectedItems(this);
            if (active) {
                newOffer.addUncollectedItemsOnAbort(event);
            }
        }
        return newOffer;
    }

    private void addUncollectedItems(Offer oldOffer) {
        itemsToCollect = oldOffer.itemsToCollect;
        gpToCollect = oldOffer.gpToCollect;
        if (status == OfferStatus.BUY) {
            itemsToCollect += amountTraded - oldOffer.amountTraded;
        } else if (status == OfferStatus.SELL) {
            gpToCollect += amountSpent - oldOffer.amountSpent;
        }
    }

    public void addUncollectedItemsOnAbort(GrandExchangeOfferChanged event) {
        GrandExchangeOffer runeliteOffer = event.getOffer();
        if (runeliteOffer.getState().equals(GrandExchangeOfferState.CANCELLED_BUY)) {
            gpToCollect += (amountTotal - amountTraded) * price;
        } else if (runeliteOffer.getState().equals(GrandExchangeOfferState.CANCELLED_SELL)) {
            itemsToCollect += amountTotal - amountTraded;
        }
    }
    public static Offer fromRuneliteEvent(GrandExchangeOfferChanged event) {
        GrandExchangeOffer runeliteOffer = event.getOffer();
        return fromRunelite(runeliteOffer, event.getSlot());
    }

    public static Offer fromRunelite(GrandExchangeOffer runeliteOffer, int slotId) {
        OfferStatus status = OfferStatus.fromRunelite(runeliteOffer.getState());
        boolean active = runeliteOffer.getState().equals(GrandExchangeOfferState.BUYING)
                || runeliteOffer.getState().equals(GrandExchangeOfferState.SELLING);
        return new Offer(status,
                runeliteOffer.getItemId(),
                runeliteOffer.getPrice(),
                runeliteOffer.getTotalQuantity(),
                runeliteOffer.getSpent(),
                runeliteOffer.getQuantitySold(),
                0,
                0,
                slotId,
                active,
                false);
    }

    public Transaction getTransaction(Offer oldOffer) {
        boolean isNewOffer = status != oldOffer.status
                || itemId != oldOffer.itemId
                || price != oldOffer.price
                || amountTotal != oldOffer.amountTotal
                || boxId != oldOffer.boxId;
        int quantityDiff = isNewOffer ? amountTraded : amountTraded - oldOffer.amountTraded;
        int amountSpentDiff = isNewOffer ? amountSpent : amountSpent - oldOffer.amountSpent;
        if (quantityDiff > 0 && amountSpentDiff > 0) {
            return new Transaction(status, itemId, price, quantityDiff, boxId, amountSpentDiff, Instant.now());
        }
        return null;
    }

    public boolean missingUncollectedItems() {
        return !active && status != OfferStatus.EMPTY && (gpToCollect == 0 && itemsToCollect == 0);
    }

    JsonObject toJson(Gson gson) {
        JsonParser jsonParser = new JsonParser();
        return jsonParser.parse(gson.toJson(this)).getAsJsonObject();
    }

    private boolean isSameOffer(Offer newOffer) {
        return status == newOffer.status
                && itemId == newOffer.itemId
                && price == newOffer.price
                && amountTotal == newOffer.amountTotal
                && amountSpent <= newOffer.amountSpent
                && amountTraded <= newOffer.amountTraded
                && boxId == newOffer.boxId;
    }
}
