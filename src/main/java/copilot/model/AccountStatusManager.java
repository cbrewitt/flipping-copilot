package copilot.model;

import lombok.*;
import copilot.rs.*;
import copilot.controller.*;
import lombok.extern.slf4j.*;
import net.runelite.api.*;
import net.runelite.api.gameval.InventoryID;

import javax.inject.*;
import java.util.*;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class AccountStatusManager {

    // dependencies
    private final Client client;
    private final PlayerLogin login;
    private final Uncollected geUncollected;
    private final Preferences preferences;
    private final PausedManager pausedManager;
    private final PortfolioController portfolioController;
    private final GrandExchange grandExchange;
    private final BankStateRS bank;
    private final Transactions transactionManager;
    private final HeldItemSyncStateRS heldItemSyncStateRS;
    private final Items itemController;
    private final Suggestions suggestions;

    // state
    @Setter
    private int skipSuggestion = -1;

    public synchronized AccountStatus getAccountStatus() {
        Long accountHash =  login.getAccountHash();
        var itemContainer = client.getItemContainer(InventoryID.INV);
        Inventory inventory;
        if(itemContainer == null) {
            log.warn("Item container was null!");
            inventory = new Inventory();
        } else {
            inventory = Inventory.fromRunelite(itemContainer, client);
        }
        var u = geUncollected.loadAllUncollected(accountHash);

        GrandExchangeOffer[] geOffers = client.getGrandExchangeOffers();
        var offerList = StatusOfferList.fromRunelite(geOffers);

        var status = new AccountStatus();
        status.setOffers(offerList);
        status.setInventory(inventory);
        status.setUncollected(u);
        status.setDisplayName(login.getPlayerDisplayName());
        status.setSkipSuggestion(skipSuggestion);
        status.setSellOnlyMode(preferences.isSellOnlyMode());
        status.setBuyAndHold(preferences.isBuyAndHold());
        status.setF2pOnlyMode(preferences.isF2pOnlyMode());
        status.setWorldMember(login.isMembersWorld());
        status.setAccountMember(login.isAccountMember());
        status.setSuggestionsPaused(pausedManager.isPaused());
        status.setBlockedItems(preferences.blockedItems());
        status.setTimeframe(preferences.getTimeframe());
        status.setRiskLevel(preferences.getRiskLevel());
        status.setReservedSlots(preferences.getEffectiveReservedSlots());
        status.setMinPredictedProfit(preferences.getMinPredictedProfit());
        status.setDumpMinPredictedProfit(preferences.getEffectiveDumpMinPredictedProfit());
        status.setBankAvailable(bank.get().loaded);
        status.setBankInventory(bank.get().items);
        status.setBagInventory(extractBagInventory());
        status.setSyncExcluded(computeSyncExcludedItems(status.getDisplayName(), u));
        status.setAllowedSync(client.getTickCount() > heldItemSyncStateRS.get().delayUntilTick);

        var inLimboItems = geUncollected.getLastClearedUncollected();
        var clearedSlots = geUncollected.getLastClearedSlots();
        if (geUncollected.getLastClearedTick() == client.getTickCount()) {
            log.debug("tick {} in limbo items {}, cleared slots {}", client.getTickCount(), inLimboItems, clearedSlots);
            if(inventory.missingJustCollected(inLimboItems)) {
                inLimboItems.forEach((itemId, qty) -> {
                    if (qty > 0) {
                        log.debug("tick {} move in limbo item {}, qty {} to inventory", client.getTickCount(), itemId, qty);
                        inventory.mergeItem(new RSItem(itemId, qty));
                    }
                });
            }
            for (Integer slot : clearedSlots) {
                Offer o = offerList.get(slot);
                GrandExchangeOffer geOffer = geOffers[slot];
                if (!isActive(geOffer.getState()) && geOffer.getState() != GrandExchangeOfferState.EMPTY) {
                    log.debug("tick {} in-activate slot {} just collected setting to EMPTY", client.getTickCount(), slot);
                    o.setStatus(OfferStatus.EMPTY);
                }
            }
        }

        return status;
    }

    private boolean isActive(GrandExchangeOfferState state) {
        switch (state){
            case EMPTY:
            case CANCELLED_BUY:
            case CANCELLED_SELL:
            case BOUGHT:
            case SOLD:
                return false;
            default:
                return true;
        }
    }

    private Map<Integer, Integer> extractBagInventory() {
        var bagInventory = itemController.getRunliteInventory();
        return bagInventory == null ? new HashMap<>() : bagInventory;
    }

    private List<Integer> computeSyncExcludedItems(String displayName, Map<Integer, Long> uncollectedItems) {
        Set<Integer> excluded = new LinkedHashSet<>(portfolioController.getActiveGrandExchangeItemIdsForSync());

        if (uncollectedItems != null) { excluded.addAll(uncollectedItems.keySet()); }

        if (displayName != null && !displayName.isEmpty()) {
            var unAckedTransactions = transactionManager.getUnAckedTransactions(displayName);
            if (unAckedTransactions != null) {
                for (Transaction transaction : unAckedTransactions) {
                    if (transaction != null && transaction.itemId > 0) { excluded.add(transaction.itemId); }
                }
            }
        }
        return new ArrayList<>(excluded);
    }

    public void resetSkipSuggestion() { skipSuggestion = -1; }

    public synchronized boolean skipCurrentSuggestion() {
        Suggestion suggestion = suggestions.getSuggestion();
        if (suggestion == null) { return false; }

        suggestion.actionedTick = client.getTickCount();
        skipSuggestion = suggestion.id;
        log.info("skipping suggestion {}", skipSuggestion);
        suggestions.setSuggestionRefreshPending(true);
        suggestions.setSuggestionNeeded(true);
        return true;
    }

    public void reset() { skipSuggestion = -1; }
}
