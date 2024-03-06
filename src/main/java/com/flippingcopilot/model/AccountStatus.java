package com.flippingcopilot.model;

import com.flippingcopilot.util.Constants;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.InventoryID;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ItemContainerChanged;

import java.util.List;
import java.util.Map;

@Getter
public class AccountStatus {
    private OfferList offers;
    private Inventory inventory;
    @Setter private boolean sellOnlyMode = false;
    @Setter private boolean isMember = false;
    @Setter private int skipSuggestion = -1;
    @Setter private String displayName;

    public AccountStatus() {
        offers = new OfferList();
        inventory = new Inventory();
    }

    public void resetSkipSuggestion() {
        skipSuggestion = -1;
    }

    public Transaction updateOffers(GrandExchangeOfferChanged event) {
        return offers.update(event);
    }

    public void setOffers(GrandExchangeOffer[] runeliteOffers) {
        offers = OfferList.fromRunelite(runeliteOffers);
    }

    public void handleInventoryChanged(ItemContainerChanged event, Client client) {
        if (event.getContainerId() == InventoryID.INVENTORY.getId()) {
            inventory = Inventory.fromRunelite(event.getItemContainer(), client);
        }
    }

    public boolean isCollectNeeded(Suggestion suggestion) {
        return offers.isEmptySlotNeeded(suggestion)
                || !inventory.hasSufficientGp(suggestion)
                || !inventory.hasSufficientItems(suggestion)
                || offers.missingUncollectedItems();
    }

    public void moveAllCollectablesToInventory() {
        Map<Integer, Long> uncollectedItemAmounts = offers.getUncollectedItemAmounts();
        List<RSItem> uncollectedItems = Inventory.fromItemAmounts(uncollectedItemAmounts);
        inventory.addAll(uncollectedItems);
        removeCollectables();
    }

    public void removeCollectables() {
        offers.removeCollectables();
    }

    public JsonObject toJson() {
        JsonObject statusJson = new JsonObject();
        statusJson.addProperty("display_name", displayName);
        statusJson.addProperty("sell_only", sellOnlyMode);
        statusJson.addProperty("is_member", isMember);
        statusJson.addProperty("skip_suggestion", skipSuggestion);
        JsonArray offersJsonArray = offers.toJson();
        JsonArray itemsJsonArray = getItemsJson();
        statusJson.add("offers", offersJsonArray);
        statusJson.add("items", itemsJsonArray);
        return statusJson;
    }

    JsonArray getItemsJson() {
        Map<Integer, Long> itemsAmount = getItemAmounts();
        JsonArray itemsJsonArray = new JsonArray();
        for(Map.Entry<Integer, Long> entry : itemsAmount.entrySet()) {
            JsonObject itemJson = new JsonObject();
            itemJson.addProperty("item_id", entry.getKey());
            itemJson.addProperty("amount", entry.getValue());
            itemsJsonArray.add(itemJson);
        }
        return itemsJsonArray;
    }

    Map<Integer, Long> getItemAmounts() {
        Map<Integer, Long> itemsAmount = inventory.getItemAmounts();
        Map<Integer, Long> uncollectedItemAmounts = offers.getUncollectedItemAmounts();
        uncollectedItemAmounts.forEach((key, value) -> itemsAmount.merge(key, value, Long::sum));
        itemsAmount.entrySet().removeIf(entry -> entry.getValue() == 0);
        return itemsAmount;
    }

    public void moveCollectedItemToInventory(int slot, int itemId) {
        RSItem collectedItem = offers.get(slot).removeCollectedItem(itemId);
        inventory.add(collectedItem);
    }

    public void removeCollectedItem(int slot, int itemId) {
        offers.get(slot).removeCollectedItem(itemId);
    }

    public boolean moreGpNeeded() {
        return offers.areAllEmpty() && getTotalGp() < Constants.MIN_GP_NEEDED_TO_FLIP;
    }

    private long getTotalGp() {
        return inventory.getTotalGp() + offers.getTotalGpToCollect();
    }
}
