package com.flippingcopilot.controller;

import com.flippingcopilot.model.PortfolioItemCardData;
import com.flippingcopilot.model.PortfolioChangeEvent;
import com.flippingcopilot.model.PortfolioSummaryData;
import com.flippingcopilot.model.PortfolioState;
import com.flippingcopilot.model.Suggestion;
import com.flippingcopilot.model.SuggestionManager;
import com.flippingcopilot.model.SyncPortfolioItem;
import com.flippingcopilot.model.SyncPortfolioRequest;
import com.flippingcopilot.rs.BankStateRS;
import com.flippingcopilot.rs.OsrsLoginRS;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Slf4j
@Singleton
public class PortfolioController {

    // dependencies
    private final Client client;
    private final OsrsLoginRS osrsLoginRS;
    private final InventoryPortfolioService inventoryPortfolioService;
    private final SuggestionManager suggestionManager;
    private final ItemController itemController;
    private final BankStateRS bankStateRS;
    private final ItemManager itemManager;

    // state (only interact from within client thread!)
    private PortfolioState state;
    private final List<PortfolioChangeEvent> pendingChanges = new ArrayList<>();

    @Inject
    public PortfolioController(Client client,
                               OsrsLoginRS osrsLoginRS,
                               InventoryPortfolioService inventoryPortfolioService,
                               SuggestionManager suggestionManager,
                               ItemController itemController,
                               BankStateRS bankStateRS,
                               ItemManager itemManager) {
        this.client = client;
        this.osrsLoginRS = osrsLoginRS;
        this.inventoryPortfolioService = inventoryPortfolioService;
        this.suggestionManager = suggestionManager;
        this.itemController = itemController;
        this.bankStateRS = bankStateRS;
        this.itemManager = itemManager;
    }

    public void onTick() {
        if(!osrsLoginRS.get().loggedIn) {
            return;
        }
        PortfolioState next = PortfolioState.fromRunelite(client);
        if(state == null || !state.accountHash.equals(next.accountHash)){
            state = next;
            return;
        }
        if(!next.bankLoaded && state.bankLoaded) {
            next.bankLoaded = true;
            next.bankItems = state.bankItems;
        }
        state = next;
    }

    public PortfolioItemCardData getInventoryTooltipData(int itemId, int fallbackQuantity) {
        int unnotedItemId = inventoryPortfolioService.toUnnotedItemId(itemId);
        if (inventoryPortfolioService.isCurrencyItem(unnotedItemId)) {
            return null;
        }
        Map<Integer, Integer> inventoryTotals = computeRuneliteTotalInventoryItems();
        Suggestion suggestion = suggestionManager.getSuggestion();
        return buildPortfolioItemCardData(
                unnotedItemId,
                0,
                inventoryTotals.getOrDefault(unnotedItemId, fallbackQuantity),
                getBankQuantity(unnotedItemId, suggestion),
                findPortfolioItem(suggestion, unnotedItemId)
        );
    }

    public List<PortfolioItemCardData> getPortfolioItems() {
        Suggestion suggestion = suggestionManager.getSuggestion();
        if (suggestion == null || suggestion.getPortfolioItems() == null || suggestion.getPortfolioItems().isEmpty()) {
            return Collections.emptyList();
        }

        Map<Integer, Integer> inventoryTotals = computeRuneliteTotalInventoryItems();
        LinkedHashSet<Integer> itemIds = new LinkedHashSet<>();
        for (Suggestion.PortfolioItem portfolioItem : suggestion.getPortfolioItems()) {
            if (portfolioItem != null) {
                itemIds.add(portfolioItem.getItemId());
            }
        }

        List<PortfolioItemCardData> items = new ArrayList<>();
        for (Integer itemId : itemIds) {
            PortfolioItemCardData data = buildPortfolioItemCardData(
                    itemId,
                    0,
                    inventoryTotals.getOrDefault(itemId, 0),
                    getBankQuantity(itemId, suggestion),
                    findPortfolioItem(suggestion, itemId)
            );
            if (data != null) {
                items.add(data);
            }
        }
        return items;
    }

    public PortfolioSummaryData buildPortfolioSummaryData(List<PortfolioItemCardData> items) {
        long assetsValue = 0L;
        long unrealizedProfit = 0L;
        for (PortfolioItemCardData item : items) {
            assetsValue += item.getPostTaxSellUnitPrice() * item.getOpenFlipsQuantity();
            unrealizedProfit += item.flipsUnrealizedProfit();
        }

        Suggestion suggestion = suggestionManager.getSuggestion();
        long cashValue = getCashValue(suggestion);
        return new PortfolioSummaryData(assetsValue + cashValue, unrealizedProfit, cashValue, assetsValue);
    }

    public boolean isSyncEnabled() {
        return bankStateRS.get().isLoaded();
    }

    public SyncPortfolioRequest buildSyncPortfolioRequest() {
        Integer accountId = inventoryPortfolioService.getActiveAccountId();
        Suggestion suggestion = suggestionManager.getSuggestion();
        if (accountId == null || !bankStateRS.get().isLoaded()) {
            return null;
        }

        Map<Integer, Integer> quantities = computeRuneliteTotalInventoryItems();
        for (Map.Entry<Integer, Long> bankEntry : bankStateRS.get().getItems().entrySet()) {
            int itemId = bankEntry.getKey();
            long bankQuantity = bankEntry.getValue() == null ? 0L : bankEntry.getValue();
            if (bankQuantity <= 0) {
                continue;
            }
            mergeTradableQuantity(quantities, itemId, (int) Math.min(Integer.MAX_VALUE, bankQuantity));
        }

        List<Integer> activeGeItemIds = getActiveGrandExchangeItemIds();
        for (Integer geItemId : activeGeItemIds) {
            quantities.remove(geItemId);
        }

        if (suggestion != null && suggestion.getPortfolioItems() != null) {
            for (Suggestion.PortfolioItem portfolioItem : suggestion.getPortfolioItems()) {
                if (portfolioItem == null || !portfolioItem.inPortfolio) {
                    continue;
                }
                int unnotedItemId = inventoryPortfolioService.toUnnotedItemId(portfolioItem.getItemId());
                if (activeGeItemIds.contains(unnotedItemId)) {
                    continue;
                }
                if (quantities.containsKey(unnotedItemId)) {
                    continue;
                }
                mergeTradableQuantity(quantities, unnotedItemId, 0);
                quantities.putIfAbsent(unnotedItemId, 0);
            }
        }

        List<SyncPortfolioItem> syncItems = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : quantities.entrySet()) {
            if (e.getValue() != null && e.getValue() >= 0) {
                syncItems.add(new SyncPortfolioItem(e.getKey(), e.getValue()));
            }
        }
        return new SyncPortfolioRequest(accountId, syncItems);
    }

    private PortfolioItemCardData buildPortfolioItemCardData(int itemId,
                                                             int runeliteGeQuantity,
                                                             int runeliteInventoryQuantity,
                                                             int suggestionBankQuantity,
                                                             Suggestion.PortfolioItem suggestionPortfolioItem) {
        if (suggestionPortfolioItem == null && suggestionBankQuantity <= 0 && runeliteInventoryQuantity <= 0) {
            return null;
        }

        int openFlipsQuantity = suggestionPortfolioItem == null ? 0 : Math.max(0, suggestionPortfolioItem.getAmount());

        return new PortfolioItemCardData(
                itemId,
                itemController.getItemName(itemId),
                runeliteGeQuantity,
                runeliteInventoryQuantity,
                suggestionBankQuantity,
                openFlipsQuantity,
                1,
                getSellPricePostTax(suggestionPortfolioItem),
                calculateUnrealizedUnitProfit(suggestionPortfolioItem),
                getHeldMinutes(suggestionPortfolioItem),
                suggestionPortfolioItem != null && suggestionPortfolioItem.inPortfolio
        );
    }

    private Long calculateUnrealizedUnitProfit(Suggestion.PortfolioItem portfolioItem) {
        if (portfolioItem == null || portfolioItem.getAmount() <= 0) {
            return null;
        }
        return portfolioItem.getPostTaxSellUnitPrice() - portfolioItem.getUnitBuyPrice();
    }

    private int getHeldMinutes(Suggestion.PortfolioItem portfolioItem) {
        if (portfolioItem == null) {
            return 0;
        }
        return Math.max(0, portfolioItem.heldMinutes);
    }

    private Map<Integer, Integer> computeRuneliteTotalInventoryItems() {
        Map<Integer, Integer> totals = new HashMap<>();
        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        if (inventory == null || inventory.getItems() == null) {
            return totals;
        }

        for (Item item : inventory.getItems()) {
            if (item == null || item.getId() <= 0 || item.getQuantity() <= 0) {
                continue;
            }
            mergeTradableQuantity(totals, item.getId(), item.getQuantity());
        }
        return totals;
    }

    private void mergeTradableQuantity(Map<Integer, Integer> totals, int itemId, int quantity) {
        if (quantity <= 0) {
            return;
        }
        int unnotedId = inventoryPortfolioService.toUnnotedItemId(itemId);
        ItemComposition itemComposition = itemManager.getItemComposition(unnotedId);
        if (itemComposition == null || !itemComposition.isTradeable()) {
            return;
        }
        totals.merge(unnotedId, quantity, Integer::sum);
    }

    private List<Integer> getActiveGrandExchangeItemIds() {
        List<Integer> itemIds = new ArrayList<>();
        GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
        if (offers == null) {
            return itemIds;
        }
        for (GrandExchangeOffer offer : offers) {
            if (offer == null) {
                continue;
            }
            GrandExchangeOfferState state = offer.getState();
            if (state != GrandExchangeOfferState.EMPTY) {
                int itemId = offer.getItemId();
                if (itemId > 0) {
                    itemIds.add(inventoryPortfolioService.toUnnotedItemId(itemId));
                }
            }
        }
        return itemIds;
    }

    private Suggestion.PortfolioItem findPortfolioItem(Suggestion suggestion, int itemId) {
        if (suggestion == null || suggestion.getPortfolioItems() == null || suggestion.getPortfolioItems().isEmpty()) {
            return null;
        }
        for (Suggestion.PortfolioItem portfolioItem : suggestion.getPortfolioItems()) {
            if (portfolioItem != null && portfolioItem.getItemId() == itemId) {
                return portfolioItem;
            }
        }
        return null;
    }

    private int getBankQuantity(int itemId, Suggestion suggestion) {
        Long quantity = bankStateRS.get().getItems().get(itemId);
        if (quantity == null && suggestion != null && suggestion.getBankItems() != null) {
            quantity = suggestion.getBankItems().get(itemId);
        }
        if (quantity == null || quantity <= 0) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, quantity);
    }

    private long getSellPricePostTax(Suggestion.PortfolioItem portfolioItem) {
        return portfolioItem == null ? 0L : portfolioItem.getPostTaxSellUnitPrice();
    }

    private long getCashValue(Suggestion suggestion) {
        Map<Integer, Integer> inventoryTotals = computeRuneliteTotalInventoryItems();
        long inventoryCash = inventoryTotals.getOrDefault(InventoryPortfolioService.COINS_ITEM_ID, 0)
                + (long) inventoryTotals.getOrDefault(InventoryPortfolioService.PLATINUM_TOKENS_ITEM_ID, 0) * 1000L;

        if (suggestion == null || suggestion.getBankItems() == null) {
            return inventoryCash;
        }

        long bankCoins = suggestion.getBankItems().getOrDefault(InventoryPortfolioService.COINS_ITEM_ID, 0L);
        long bankPlatinumTokens = suggestion.getBankItems().getOrDefault(InventoryPortfolioService.PLATINUM_TOKENS_ITEM_ID, 0L) * 1000L;
        return inventoryCash + bankCoins + bankPlatinumTokens;
    }
}
