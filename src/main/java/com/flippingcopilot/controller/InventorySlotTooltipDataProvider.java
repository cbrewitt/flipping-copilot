package com.flippingcopilot.controller;

import com.flippingcopilot.model.FlipV2;
import com.flippingcopilot.model.InventorySlotTooltipData;
import com.flippingcopilot.model.AccountStatusManager;
import com.flippingcopilot.model.Suggestion;
import com.flippingcopilot.model.SuggestionManager;
import com.flippingcopilot.ui.UIUtilities;
import com.flippingcopilot.util.FlipUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class InventorySlotTooltipDataProvider {

    private final ItemManager itemManager;
    private final InventoryPortfolioService portfolioService;
    private final AccountStatusManager accountStatusManager;
    private final SuggestionManager suggestionManager;
    private final Client client;

    public InventorySlotTooltipData getTooltipData(int itemId, int quantity) {
        int unnotedItemId = portfolioService.toUnnotedItemId(itemId);
        if (portfolioService.isCurrencyItem(unnotedItemId)) {
            return null;
        }

        String itemName = itemManager.getItemComposition(unnotedItemId).getName();
        Integer accountId = portfolioService.getActiveAccountId();
        FlipV2 openFlip = portfolioService.getOpenFlip(unnotedItemId, accountId);
        Map<Integer, Integer> inventoryTotals = computeRunliteTotalInventoryItems();
        int totalInventoryQuantity = inventoryTotals.getOrDefault(unnotedItemId, quantity);

        Suggestion suggestion = suggestionManager.getSuggestion();
        long bankQuantity = suggestion == null || suggestion.getBankItems() == null ? 0L : suggestion.getBankItems().getOrDefault(unnotedItemId, 0L);
        Suggestion.PortfolioItem portfolioItem = findPortfolioItem(suggestion, unnotedItemId);
        List<FlipV2> openFlips = openFlip == null ? Collections.emptyList() : Collections.singletonList(openFlip);
        List<String> lines = buildTooltipLines(totalInventoryQuantity, bankQuantity, openFlip, portfolioItem, openFlips);
        return new InventorySlotTooltipData(unnotedItemId, quantity, itemName, lines);
    }

    private List<String> buildTooltipLines(
            int totalInventoryQuantity,
            long bankQuantity,
            FlipV2 openFlip,
            Suggestion.PortfolioItem portfolioItem,
            List<FlipV2> openFlips) {

        boolean inPortfolio = FlipUtils.inPortfolio(openFlips);

        List<String> lines = new ArrayList<>(5);

        lines.add(formatQuantityLine(totalInventoryQuantity, bankQuantity));
        if (!inPortfolio) {
            lines.add("Not in portfolio");
            Long totalValue = calculateValue(totalInventoryQuantity, bankQuantity, portfolioItem);
            lines.add("Total value: " + (totalValue == null ? "Unknown" : UIUtilities.formatProfit(totalValue)));
        } else {
            lines.add("Time held: " + formatHoldTime(openFlip.getOpenedTime()));
            Long totalValue = calculateValue(totalInventoryQuantity, bankQuantity, portfolioItem);
            lines.add("Total value: " + (totalValue == null ? "Unknown" : UIUtilities.formatProfit(totalValue)));
            Long unrealizedPnl = calculateUnrealisedPNL(totalInventoryQuantity, bankQuantity, portfolioItem, openFlips);
            lines.add("Unrealized PNL: " + (unrealizedPnl == null ? "Unknown" : UIUtilities.formatProfit(unrealizedPnl)));
        }
        return lines;
    }

    private Map<Integer, Integer> computeRunliteTotalInventoryItems() {
        Map<Integer, Integer> totals = new HashMap<>();
        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        if (inventory == null || inventory.getItems() == null) {
            return totals;
        }

        for (Item item : inventory.getItems()) {
            if (item == null || item.getId() <= 0 || item.getQuantity() <= 0) {
                continue;
            }
            int unnotedId = portfolioService.toUnnotedItemId(item.getId());
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

    private Long calculateUnrealisedPNL(
            int inventoryAmount,
            long bankAmount,
            Suggestion.PortfolioItem portfolioItem,
            List<FlipV2> openFlips) {
        if(portfolioItem == null ){
            return null;
        }
        long sellPricePostTax = portfolioItem.sellValue / portfolioItem.amount;
        long buyPrice = FlipUtils.AverageUnclosedBuyPrice(openFlips);
        return (sellPricePostTax - buyPrice) * (inventoryAmount + bankAmount);
    }

    private Long calculateValue(int inventoryAmount, long bankAmount, Suggestion.PortfolioItem portfolioItem) {
        if (portfolioItem == null || portfolioItem.getAmount() <= 0) {
            return null;
        }
        long totalQuantity = Math.max(0L, (long) inventoryAmount + bankAmount);
        return (portfolioItem.getSellValue() * totalQuantity) / portfolioItem.getAmount();
    }

    private String formatQuantityLine(int totalInventoryQuantity, long bankQuantity) {
        String quantity = NumberFormat.getIntegerInstance().format(totalInventoryQuantity);
        if (bankQuantity > 0) {
            return "Quantity: " + quantity + " (" + NumberFormat.getIntegerInstance().format(bankQuantity) + " in bank)";
        }
        return "Quantity: " + quantity;
    }

    private String formatHoldTime(int openedTimeEpochSeconds) {
        if (openedTimeEpochSeconds <= 0) {
            return "0m";
        }

        long elapsed = Math.max(0, Instant.now().getEpochSecond() - openedTimeEpochSeconds);
        if (elapsed > 2L * 24 * 60 * 60) {
            return formatHoldTimeWithDayHourPrecision(elapsed);
        }

        long hours = elapsed / 3600;
        long minutes = (elapsed % 3600) / 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    private String formatHoldTimeWithDayHourPrecision(long elapsedSeconds) {
        long days = elapsedSeconds / (24 * 3600);
        long hours = (elapsedSeconds % (24 * 3600)) / 3600;
        return days + "d " + hours + "h";
    }
}
