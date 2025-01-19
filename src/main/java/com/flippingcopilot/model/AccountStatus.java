package com.flippingcopilot.model;
import com.flippingcopilot.util.Constants;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


// note: we synchronize all public methods of this class as they read/modify its state and may
// be called by multiple threads at the same time

@Slf4j
@Data
public class AccountStatus {

    private StatusOfferList offers;
    private Inventory inventory;
    private Map<Integer, Long> uncollected;
    private boolean isMember = false;
    private int skipSuggestion = -1;
    private String displayName;
    private Long rsAccountHash;
    private Boolean suggestionsPaused;
    private boolean sellOnlyMode = false;
    private boolean f2pOnlyMode = false;
    private List<Integer> blockedItems;

    public AccountStatus() {
        offers = new StatusOfferList();
        inventory = new Inventory();
    }

    public synchronized boolean isCollectNeeded(Suggestion suggestion) {
        if (offers.isEmptySlotNeeded(suggestion)) {
            log.debug("collected needed isEmptySlotNeeded");
            return true;
        }
        if (!inventory.hasSufficientGp(suggestion)) {
            log.debug("collected needed hasSufficientGp");
            return true;
        }
        if (!inventory.hasSufficientItems(suggestion)) {
            log.debug("collected needed hasSufficientItems");
            return true;
        }
        return false;
    }

    public synchronized JsonObject toJson(Gson gson, boolean geOpen) {
        JsonObject statusJson = new JsonObject();
        statusJson.addProperty("display_name", displayName);
        statusJson.addProperty("sell_only", sellOnlyMode);
        statusJson.addProperty("f2p_only", f2pOnlyMode);
        statusJson.addProperty("is_member", isMember);
        statusJson.addProperty("skip_suggestion", skipSuggestion);
        if (suggestionsPaused != null) {
            statusJson.addProperty("suggestions_paused", suggestionsPaused);
        }
        JsonArray offersJsonArray = offers.toJson(gson);
        JsonArray itemsJsonArray = getItemsJson();
        statusJson.add("offers", offersJsonArray);
        statusJson.add("items", itemsJsonArray);
        JsonArray blockItemsArray = new JsonArray();
        if(blockedItems != null) {
            blockedItems.forEach(blockItemsArray::add);
        }
        statusJson.add("blocked_items", blockItemsArray);

        Set<String> requestedSuggestionTypes = new HashSet<>();
        if (!geOpen) {
            requestedSuggestionTypes.add("abort");
            requestedSuggestionTypes.add("sell");
        }
        if(sellOnlyMode) {
            requestedSuggestionTypes.clear();
            requestedSuggestionTypes.add("abort");
        }
        if(!requestedSuggestionTypes.isEmpty()) {
           JsonArray rstArray = new JsonArray();
           requestedSuggestionTypes.forEach(rstArray::add);
           statusJson.add("requestedSuggestionTypes", rstArray);
        }
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
        uncollected.forEach((key, value) -> itemsAmount.merge(key, value, Long::sum));
        itemsAmount.entrySet().removeIf(entry -> entry.getValue() == 0);
        return itemsAmount;
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
}
