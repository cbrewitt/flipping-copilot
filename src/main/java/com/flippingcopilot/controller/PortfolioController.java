package com.flippingcopilot.controller;

import com.flippingcopilot.model.PortfolioItemCardData;
import com.flippingcopilot.model.PortfolioChangeEvent;
import com.flippingcopilot.model.PortfolioSummaryData;
import com.flippingcopilot.model.PortfolioState;
import com.flippingcopilot.model.Suggestion;
import com.flippingcopilot.model.SuggestionManager;
import com.flippingcopilot.rs.OsrsLoginRS;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;

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

    // state (only interact from within client thread!)
    private PortfolioState state;
    private final List<PortfolioChangeEvent> pendingChanges = new ArrayList<>();

    @Inject
    public PortfolioController(Client client,
                               OsrsLoginRS osrsLoginRS,
                               InventoryPortfolioService inventoryPortfolioService,
                               SuggestionManager suggestionManager,
                               ItemController itemController) {
        this.client = client;
        this.osrsLoginRS = osrsLoginRS;
        this.inventoryPortfolioService = inventoryPortfolioService;
        this.suggestionManager = suggestionManager;
        this.itemController = itemController;
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
                suggestion == null || suggestion.getBankItems() == null ? 0 : suggestion.getBankItems().getOrDefault(unnotedItemId, 0L).intValue(),
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
                    suggestion == null || suggestion.getBankItems() == null ? 0 : suggestion.getBankItems().getOrDefault(itemId, 0L).intValue(),
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
        long unrealizedPnl = 0L;
        for (PortfolioItemCardData item : items) {
            assetsValue += item.getPostTaxSellUnitPrice() * item.getOpenFlipsQuantity();
            unrealizedPnl += item.flipsUnrealizedPNL();
        }

        Suggestion suggestion = suggestionManager.getSuggestion();
        long cashValue = getCashValue(suggestion);
        return new PortfolioSummaryData(assetsValue + cashValue, unrealizedPnl, cashValue, assetsValue);
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
                calculateUnrealizedUnitPnl(suggestionPortfolioItem),
                getHeldMinutes(suggestionPortfolioItem),
                suggestionPortfolioItem != null && suggestionPortfolioItem.inPortfolio
        );
    }

    private Long calculateUnrealizedUnitPnl(Suggestion.PortfolioItem portfolioItem) {
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
            int unnotedId = inventoryPortfolioService.toUnnotedItemId(item.getId());
            totals.merge(unnotedId, item.getQuantity(), Integer::sum);
        }
        return totals;
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
