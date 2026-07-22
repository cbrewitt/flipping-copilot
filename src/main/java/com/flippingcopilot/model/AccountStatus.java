package com.flippingcopilot.model;
import com.flippingcopilot.util.Constants;
import com.flippingcopilot.util.ProtoUtils;
import com.google.protobuf.CodedOutputStream;
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

    private final int version = 100;

    private StatusOfferList offers;
    private Inventory inventory;
    private Map<Integer, Long> uncollected;
    private boolean isWorldMember = false;
    private boolean isAccountMember = false;
    private int skipSuggestion = -1;
    private String displayName;
    private Boolean suggestionsPaused;
    private boolean sellOnlyMode = false;
    private boolean buyAndHold = true;
    private boolean f2pOnlyMode = false;
    private List<Integer> blockedItems;
    private int timeframe = 5; // Default to 5 minutes
    private RiskLevel riskLevel = RiskLevel.MEDIUM;
    private Integer reservedSlots;
    private Long minPredictedProfit;
    private Long dumpMinPredictedProfit;

    private Map<Integer, Integer> bankInventory;
    private boolean bankAvailable;
    private List<Integer> syncExcluded;
    private boolean allowedSync;
    private Map<Integer, Integer> bagInventory;

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
        if (suggestion.isModifySuggestion() || suggestion.isAbortSuggestion()) {
            return false;
        }
        if (!inventory.hasSufficientGp(suggestion)) {
            log.debug("collected needed hasSufficientGp");
            return true;
        }
        if (!hasSufficientItemsForSuggestion(suggestion)) {
            if (hasSufficientCollectibleItemsForSuggestion(suggestion)) {
                log.debug("collected needed hasSufficientItems");
                return true;
            }
        }
        return false;
    }

    public synchronized boolean hasSufficientInventoryForSellSuggestion(Suggestion suggestion) {
        return suggestion != null
                && suggestion.isSellSuggestion()
                && inventory != null
                && inventory.getTotalAmount(suggestion.getItemId()) >= suggestion.getQuantity();
    }

    public synchronized boolean shouldSellFromBank(Suggestion suggestion) {
        if (suggestion == null || !suggestion.isSellSuggestion() || suggestion.isModifySuggestion()) {
            return false;
        }
        if (inventory == null || hasSufficientInventoryForSellSuggestion(suggestion)) {
            return false;
        }
        return getBankQuantity(suggestion) > 0;
    }

    private long getBankQuantity(Suggestion suggestion) {
        if (bankAvailable && bankInventory != null) {
            return Math.max(0, bankInventory.getOrDefault(suggestion.getItemId(), 0));
        }
        if (suggestion.getBankItems() == null) {
            return 0;
        }
        return Math.max(0, suggestion.getBankItems().getOrDefault(suggestion.getItemId(), 0));
    }

    private boolean hasSufficientItemsForSuggestion(Suggestion suggestion) {
        if (!suggestion.isSellSuggestion()) {
            return true;
        }

        if (hasSufficientInventoryForSellSuggestion(suggestion)) {
            return true;
        }

        if (!bankAvailable || bankInventory == null) {
            return false;
        }

        long inventoryQty = inventory.getTotalAmount(suggestion.getItemId());
        long bankQty = Math.max(0, bankInventory.getOrDefault(suggestion.getItemId(), 0));
        return inventoryQty + bankQty >= suggestion.getQuantity();
    }

    private boolean hasSufficientCollectibleItemsForSuggestion(Suggestion suggestion) {
        if (suggestion == null || !suggestion.isSellSuggestion() || uncollected == null) {
            return false;
        }

        long inventoryQty = inventory == null ? 0 : inventory.getTotalAmount(suggestion.getItemId());
        long bankQty = 0;
        if (bankAvailable && bankInventory != null) {
            bankQty = Math.max(0, bankInventory.getOrDefault(suggestion.getItemId(), 0));
        }

        long collectibleQty = Math.max(0, uncollected.getOrDefault(suggestion.getItemId(), 0L));
        long missingQty = suggestion.getQuantity() - inventoryQty - bankQty;
        return missingQty > 0 && collectibleQty >= missingQty;
    }

    public int findEmptySlot() {
        return getOffers().findEmptySlot(isWorldMember || isAccountMember);
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
        return inventory.getTotalGp();
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
        requestedSuggestionTypes.add(SuggestionType.SELL);
        if (geOpen && !sellOnlyMode) {
            requestedSuggestionTypes.add(SuggestionType.BUY);
        }
        return requestedSuggestionTypes;
    }

    private int resolveReservedSlots() {
        return reservedSlots == null ? 0 : reservedSlots;
    }

    public synchronized byte[] encodeProto( boolean geOpen, boolean sendGraphData) {
        return ProtoUtils.encodeMessage(out -> {
            out.writeInt32(8, version);
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
                out.writeInt64(34, minPredictedProfit);
            }
            if (dumpMinPredictedProfit != null) {
                out.writeInt64(35, dumpMinPredictedProfit);
            }

            ProtoUtils.writeMap(
                    out,
                    20,
                    bankInventory,
                    CodedOutputStream::writeInt32,
                    CodedOutputStream::writeInt32
            );
            out.writeBool(21, bankAvailable);

            if (suggestionsPaused != null) {
                out.writeBool(28, suggestionsPaused);
            }
            out.writeBool(30, buyAndHold);
            ProtoUtils.writePacked(out, 31, syncExcluded, CodedOutputStream::writeInt32NoTag);
            out.writeBool(32, allowedSync);
            ProtoUtils.writeMap(
                    out,
                    33,
                    bagInventory,
                    CodedOutputStream::writeInt32,
                    CodedOutputStream::writeInt32
            );
        });
    }
}
