package com.flippingcopilot.model;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;

@Slf4j
@Singleton
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

    @Inject
    public AccountStatusManager(Client client, OsrsLoginManager osrsLoginManager, GrandExchangeUncollectedManager geUncollected, SuggestionPreferencesManager suggestionPreferencesManager, PausedManager pausedManager) {
        this.client = client;
        this.osrsLoginManager = osrsLoginManager;
        this.geUncollected = geUncollected;
        this.suggestionPreferencesManager = suggestionPreferencesManager;
        this.pausedManager = pausedManager;
    }

    public AccountStatus getAccountStatus() {
        Long accountHash =  osrsLoginManager.getAccountHash();
        ItemContainer itemContainer = client.getItemContainer(InventoryID.INVENTORY);
        if(itemContainer == null) {
            log.warn("unable to fetch inventory item container");
            return null;
        }
        Inventory inventory = Inventory.fromRunelite(itemContainer, client);
        Map<Integer, Long> u = geUncollected.loadAllUncollected(accountHash);

        StatusOfferList offerList = StatusOfferList.fromRunelite(client.getGrandExchangeOffers());

        AccountStatus status = new AccountStatus();
        status.setOffers(offerList);
        status.setInventory(inventory);
        status.setUncollected(u);
        status.setDisplayName(osrsLoginManager.getPlayerDisplayName());
        status.setRsAccountHash(accountHash);
        status.setSkipSuggestion(skipSuggestion);
        status.setSellOnlyMode(suggestionPreferencesManager.getPreferences().isSellOnlyMode());
        status.setMember(osrsLoginManager.isMembersWorld());
        status.setSuggestionsPaused(pausedManager.isPaused());
        status.setBlockedItems(suggestionPreferencesManager.blockedItems());

        // if it was collected on this tick the items won't actually have appeared in the inventory yet
        // so temporarily add them in
        if (geUncollected.getLastClearedTick() == client.getTickCount()) {
            Map<Integer, Long> inLimboItems = geUncollected.getLastClearedUncollected();
            for(RSItem item : status.getInventory()) {
                item.amount += inLimboItems.getOrDefault(item.id, 0L);
            }
        }

        return status;
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
