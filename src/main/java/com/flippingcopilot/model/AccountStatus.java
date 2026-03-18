package com.flippingcopilot.model;
import com.flippingcopilot.util.Constants;
import com.flippingcopilot.util.ProtoUtils;
import com.google.protobuf.CodedOutputStream;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

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
    private boolean isWorldMember = false;
    private boolean isAccountMember = false;
    private int skipSuggestion = -1;
    private String displayName;
    private Long rsAccountHash;
    private Boolean suggestionsPaused;
    private boolean sellOnlyMode = false;
    private boolean buyAndHold = true;
    private boolean f2pOnlyMode = false;
    private List<Integer> blockedItems;
    private int timeframe = 5; // Default to 5 minutes
    private RiskLevel riskLevel = RiskLevel.MEDIUM;
    private Integer reservedSlots;
    private Integer minPredictedProfit;
    private Integer dumpMinPredictedProfit;

    private Map<Integer, Long> bankInventory;
    private boolean bankAvailable;

    public AccountStatus() {
        offers = new StatusOfferList();
        inventory = new Inventory();
    }

    public synchronized boolean isCollectNeeded(Suggestion suggestion, boolean setUpOfferOpen) {
        if (!suggestion.isDumpAlert() && !setUpOfferOpen
                && SuggestionType.WAIT.equals(suggestion.getType()) && offers.reservedSlotNeeded(isWorldMember || isAccountMember, resolveReservedSlots(), suggestion))  {
            log.debug("collected needed reservedSlotNeeded");
            return true;
        }
        if (offers.isEmptySlotNeeded(suggestion, isWorldMember || isAccountMember)) {
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

    public int findEmptySlot() {
        return getOffers().findEmptySlot(isWorldMember || isAccountMember);
    }

    public synchronized JsonObject toJson(Gson gson, boolean geOpen, boolean sendGraphData) {
        JsonObject statusJson = new JsonObject();
        statusJson.addProperty("display_name", displayName);
        statusJson.addProperty("sell_only", sellOnlyMode);
        statusJson.addProperty("buy_and_hold", buyAndHold);
        statusJson.addProperty("f2p_only", f2pOnlyMode);
        statusJson.addProperty("is_member", isWorldMember);
        statusJson.addProperty("is_account_member", isAccountMember);
        statusJson.addProperty("skip_suggestion", skipSuggestion);
        statusJson.addProperty("send_graph_data", sendGraphData);
        statusJson.addProperty("timeframe", timeframe);
        RiskLevel effectiveRiskLevel = riskLevel != null ? riskLevel : RiskLevel.MEDIUM;
        statusJson.addProperty("risk_level", effectiveRiskLevel.toApiValue());
        if (minPredictedProfit != null) {
            statusJson.addProperty("min_predicted_profit", minPredictedProfit);
        }
        if (dumpMinPredictedProfit != null) {
            statusJson.addProperty("min_dump_profit", dumpMinPredictedProfit);
        }
        if (suggestionsPaused != null) {
            statusJson.addProperty("suggestions_paused", suggestionsPaused);
        }
        JsonArray offersJsonArray = offers.toJson(gson);
        JsonArray itemsJsonArray = getItemsJson();
        statusJson.add("offers", offersJsonArray);
        statusJson.add("items", itemsJsonArray);
        if (bankInventory != null) {
            JsonObject bankInventoryJson = new JsonObject();
            bankInventory.forEach((itemId, amount) -> bankInventoryJson.addProperty(itemId.toString(), amount));
            statusJson.add("bank_inventory", bankInventoryJson);
            statusJson.addProperty("bank_loaded", bankAvailable);
        }
        JsonArray blockItemsArray = new JsonArray();
        if(blockedItems != null) {
            blockedItems.forEach(blockItemsArray::add);
        }
        statusJson.add("blocked_items", blockItemsArray);

        Set<SuggestionType> requestedSuggestionTypes = resolveRequestedSuggestionTypes(geOpen);
        JsonArray rstArray = new JsonArray();
        requestedSuggestionTypes.forEach(type -> rstArray.add(type.apiValue()));
        statusJson.add("requested_suggestion_types", rstArray);
        log.debug("requested suggestion types for {} (geOpen={}, sellOnly={}): {}",
                displayName, geOpen, sellOnlyMode, requestedSuggestionTypes);
        if (reservedSlots != null && reservedSlots > 0) {
            statusJson.addProperty("reserved_slots", reservedSlots);
        }
        return statusJson;
    }

    private JsonArray getItemsJson() {
        Map<Integer, Long> itemAmounts = computeInventory();
        JsonArray itemsJsonArray = new JsonArray();
        for (Map.Entry<Integer, Long> entry : itemAmounts.entrySet()) {
            JsonObject itemJson = new JsonObject();
            itemJson.addProperty("item_id", entry.getKey());
            itemJson.addProperty("amount", entry.getValue());
            itemsJsonArray.add(itemJson);
        }
        return itemsJsonArray;
    }

    private Map<Integer, Long> computeInventory() {
        Map<Integer, Long> itemAmounts = inventory.getItemAmounts();
        uncollected.forEach((key, value) -> itemAmounts.merge(key, value, Long::sum));
        itemAmounts.entrySet().removeIf(entry -> entry.getValue() == 0);
        return itemAmounts;
    }

    public synchronized boolean moreGpNeeded() {
        return emptySlotExists() && getTotalGp() < Constants.MIN_GP_NEEDED_TO_FLIP;
    }

    public synchronized boolean emptySlotExists() {
        return offers.emptySlotExists(isWorldMember || isAccountMember);
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

    private Set<SuggestionType> resolveRequestedSuggestionTypes(boolean geOpen) {
        Set<SuggestionType> requestedSuggestionTypes = SuggestionType.abortAndModifyTypes();
        if (geOpen) {
            if (sellOnlyMode) {
                requestedSuggestionTypes.add(SuggestionType.SELL);
            } else {
                requestedSuggestionTypes.add(SuggestionType.BUY);
                requestedSuggestionTypes.add(SuggestionType.SELL);
            }
        }
        return requestedSuggestionTypes;
    }

    private int resolveReservedSlots() {
        return reservedSlots == null ? 0 : reservedSlots;
    }

    public synchronized byte[] encodeProto( boolean geOpen, boolean sendGraphData) {
        return ProtoUtils.encodeMessage(out -> {
            if (displayName != null && !displayName.isEmpty()) {
                out.writeString(1, displayName);
            }
            out.writeBool(2, sellOnlyMode);
            out.writeBool(3, isWorldMember);
            out.writeBool(4, isAccountMember);
            out.writeInt32(5, skipSuggestion);

            if (offers != null) {
                for (Offer offer : offers) {
                    if (offer == null) {
                        continue;
                    }
                    byte[] offerBytes = offer.encodeProto();
                    if (offerBytes.length == 0) {
                        continue;
                    }
                    ProtoUtils.writeDelimitedMessageField(out, 6, offerBytes);
                }
            }

            Map<Integer, Long> protoInventory = computeInventory();
            ProtoUtils.writeMap(out, 7, protoInventory, CodedOutputStream::writeInt32, CodedOutputStream::writeInt64);

            out.writeBool(11, f2pOnlyMode);
            ProtoUtils.writePacked(out, 12, blockedItems, CodedOutputStream::writeInt32NoTag);

            Set<SuggestionType> requestedSuggestionTypes = resolveRequestedSuggestionTypes(geOpen);
            List<Integer> requestedSuggestionTypeInts = new java.util.ArrayList<>(requestedSuggestionTypes.size());
            for (SuggestionType suggestionType : requestedSuggestionTypes) {
                requestedSuggestionTypeInts.add(suggestionType.protoInt());
            }
            ProtoUtils.writePacked(out, 13, requestedSuggestionTypeInts, CodedOutputStream::writeInt32NoTag);

            out.writeBool(14, sendGraphData);

            out.writeDouble(15, timeframe);
            RiskLevel effectiveRiskLevel = riskLevel == null ? RiskLevel.MEDIUM : riskLevel;
            out.writeInt32(16, effectiveRiskLevel.protoInt());
            if (reservedSlots != null) {
                out.writeInt32(17, reservedSlots);
            }
            if (minPredictedProfit != null) {
                out.writeInt32(18, minPredictedProfit);
            }
            if (dumpMinPredictedProfit != null) {
                out.writeInt32(19, dumpMinPredictedProfit);
            }

            ProtoUtils.writeMap(
                    out,
                    20,
                    bankInventory,
                    CodedOutputStream::writeInt32,
                    CodedOutputStream::writeInt64
            );
            out.writeBool(21, bankAvailable);

            if (suggestionsPaused != null) {
                out.writeBool(28, suggestionsPaused);
            }
            out.writeBool(30, buyAndHold);
        });
    }
}
