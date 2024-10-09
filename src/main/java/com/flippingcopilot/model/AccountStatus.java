package com.flippingcopilot.model;

import com.flippingcopilot.controller.Persistance;
import com.flippingcopilot.util.Constants;
import com.google.gson.Gson;
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


// note: we synchronize all public methods of this class as they read/modify its state and may
// be called by multiple threads at the same time
@Getter
public class AccountStatus {
    private OfferList offers;
    private Inventory inventory;
    @Setter private boolean sellOnlyMode = false;
    @Setter private boolean isMember = false;
    @Setter private int skipSuggestion = -1;
    @Setter private String displayName;
    @Setter private Boolean suggestionsPaused;
    public AccountStatus() {
        offers = new OfferList();
        inventory = new Inventory();
    }

    public synchronized void resetSkipSuggestion() {
        skipSuggestion = -1;
    }

    public synchronized boolean isSuggestionSkipped() {
        return skipSuggestion != -1;
    }

    public synchronized Transaction updateOffers(GrandExchangeOfferChanged event, int lastViewedSlotItemId, int lastViewedSlotItemPrice, int lastViewSlotTime) {
        return offers.update(event, lastViewedSlotItemId, lastViewedSlotItemPrice, lastViewSlotTime);
    }

    public synchronized void setOffers(GrandExchangeOffer[] runeliteOffers) {
        offers = OfferList.fromRunelite(runeliteOffers);
    }

    public synchronized void handleInventoryChanged(ItemContainerChanged event, Client client) {
        if (event.getContainerId() == InventoryID.INVENTORY.getId()) {
            inventory = Inventory.fromRunelite(event.getItemContainer(), client);
        }
    }

    public synchronized boolean isCollectNeeded(Suggestion suggestion) {
        return offers.isEmptySlotNeeded(suggestion)
                || !inventory.hasSufficientGp(suggestion)
                || !inventory.hasSufficientItems(suggestion)
                || offers.missingUncollectedItems();
    }

    public synchronized void moveAllCollectablesToInventory() {
        Map<Integer, Long> uncollectedItemAmounts = offers.getUncollectedItemAmounts();
        List<RSItem> uncollectedItems = Inventory.fromItemAmounts(uncollectedItemAmounts);
        inventory.addAll(uncollectedItems);
        removeCollectables();
    }

    public synchronized void removeCollectables() {
        offers.removeCollectables();
    }

    public synchronized JsonObject toJson(Gson gson) {
        JsonObject statusJson = new JsonObject();
        statusJson.addProperty("display_name", displayName);
        statusJson.addProperty("sell_only", sellOnlyMode);
        statusJson.addProperty("is_member", isMember);
        statusJson.addProperty("skip_suggestion", skipSuggestion);
        if (suggestionsPaused != null) {
            statusJson.addProperty("suggestions_paused", suggestionsPaused);
        }
        JsonArray offersJsonArray = offers.toJson(gson);
        JsonArray itemsJsonArray = getItemsJson();
        statusJson.add("offers", offersJsonArray);
        statusJson.add("items", itemsJsonArray);
        return statusJson;
    }

    private JsonArray getItemsJson() {
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

    private Map<Integer, Long> getItemAmounts() {
        Map<Integer, Long> itemsAmount = inventory.getItemAmounts();
        Map<Integer, Long> uncollectedItemAmounts = offers.getUncollectedItemAmounts();
        uncollectedItemAmounts.forEach((key, value) -> itemsAmount.merge(key, value, Long::sum));
        itemsAmount.entrySet().removeIf(entry -> entry.getValue() == 0);
        return itemsAmount;
    }

    public synchronized void moveCollectedItemToInventory(int slot, int itemId) {
        RSItem collectedItem = offers.get(slot).removeCollectedItem(itemId);
        inventory.add(collectedItem);
    }

    public synchronized void removeCollectedItem(int slot, int itemId) {
        offers.get(slot).removeCollectedItem(itemId);
    }

    public synchronized boolean moreGpNeeded() {
        return offers.emptySlotExists() && getTotalGp() < Constants.MIN_GP_NEEDED_TO_FLIP;
    }

    private long getTotalGp() {
        return inventory.getTotalGp() + offers.getTotalGpToCollect();
    }

    public synchronized boolean currentlyFlipping() {
        return offers.stream().anyMatch(Offer::isActive);
    }

    public synchronized long currentCashStack() {
        // the cash stack is the gp in their inventory + the value on the market
        // todo: when a buy offer has fully finished its value will not count towards the cash stack
        //  size until they start selling it. We should probably track items that where recently bought
        //  and they should still count towards the cash stack size for some period of time
        return offers.getGpOnMarket() + inventory.getTotalGp();
    }

    public synchronized void onLogout(String currentDisplayName) {
        Persistance.storeGeOfferEvents(offers, currentDisplayName);
    }

    public synchronized void loadPreviousOffers(String currentDisplayName) {
        OfferList previousOffers = Persistance.loadPreviousGeOfferEvents(currentDisplayName);
        if (previousOffers != null && previousOffers.size() == OfferList.NUM_SLOTS) {
            this.offers = previousOffers;
        }
    }
}
