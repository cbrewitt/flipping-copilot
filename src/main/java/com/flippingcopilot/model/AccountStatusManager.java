package com.flippingcopilot.model;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.gameval.InventoryID;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class AccountStatusManager {

    // dependencies
    private final Client client;
    private final OsrsLoginManager osrsLoginManager;
    private final GrandExchangeUncollectedManager geUncollected;
    private final SuggestionPreferencesManager suggestionPreferencesManager;
    private final PausedManager pausedManager;

    // state
    @Setter
    private int skipSuggestion = -1;

    public synchronized AccountStatus getAccountStatus() {
        Long accountHash =  osrsLoginManager.getAccountHash();
        ItemContainer itemContainer = client.getItemContainer(InventoryID.INV);
        Inventory inventory;
        if(itemContainer == null) {
            log.warn("Item container was null!");
            inventory = new Inventory();
        } else {
            inventory = Inventory.fromRunelite(itemContainer, client);
        }
        Map<Integer, Long> u = geUncollected.loadAllUncollected(accountHash);

        GrandExchangeOffer[] geOffers = client.getGrandExchangeOffers();
        StatusOfferList offerList = StatusOfferList.fromRunelite(geOffers);

        AccountStatus status = new AccountStatus();
        status.setOffers(offerList);
        status.setInventory(inventory);
        status.setUncollected(u);
        status.setDisplayName(osrsLoginManager.getPlayerDisplayName());
        status.setRsAccountHash(accountHash);
        status.setSkipSuggestion(skipSuggestion);
        status.setSellOnlyMode(suggestionPreferencesManager.isSellOnlyMode());
        status.setF2pOnlyMode(suggestionPreferencesManager.isF2pOnlyMode());
        status.setWorldMember(osrsLoginManager.isMembersWorld());
        status.setAccountMember(osrsLoginManager.isAccountMember());
        status.setSuggestionsPaused(pausedManager.isPaused());
        status.setBlockedItems(suggestionPreferencesManager.blockedItems());
        status.setTimeframe(suggestionPreferencesManager.getTimeframe());
        status.setRiskLevel(suggestionPreferencesManager.getRiskLevel());
        status.setReservedSlots(suggestionPreferencesManager.getReservedSlots());

        Map<Integer, Long> inLimboItems = geUncollected.getLastClearedUncollected();
        List<Integer> clearedSlots = geUncollected.getLastClearedSlots();
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

    public boolean isSuggestionSkipped() {
        return skipSuggestion != -1;
    }

    public void resetSkipSuggestion() {
        skipSuggestion = -1;
    }

    public void reset() {
        skipSuggestion = -1;
    }
}
