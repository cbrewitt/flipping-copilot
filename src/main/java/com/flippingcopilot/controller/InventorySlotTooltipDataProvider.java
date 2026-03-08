package com.flippingcopilot.controller;

import com.flippingcopilot.model.InventorySlotTooltipData;
import com.flippingcopilot.model.PortfolioItemCardData;
import com.flippingcopilot.model.Suggestion;
import com.flippingcopilot.model.SuggestionManager;
import com.flippingcopilot.ui.UIUtilities;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

@Singleton
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class InventorySlotTooltipDataProvider {

    private final ItemManager itemManager;
    private final SuggestionManager suggestionManager;
    private final PortfolioController portfolioController;

    public InventorySlotTooltipData getTooltipData(int itemId, int quantity) {
        PortfolioItemCardData itemData = portfolioController.getInventoryTooltipData(itemId, quantity);
        if (itemData == null) {
            return null;
        }

        String itemName = itemManager.getItemComposition(itemData.getItemId()).getName();
        Suggestion suggestion = suggestionManager.getSuggestion();
        Suggestion.PortfolioItem portfolioItem = findPortfolioItem(suggestion, itemData.getItemId());
        List<String> lines = buildTooltipLines(itemData, portfolioItem);
        return new InventorySlotTooltipData(itemData.getItemId(), quantity, itemName, lines);
    }

    private List<String> buildTooltipLines(PortfolioItemCardData itemData, Suggestion.PortfolioItem portfolioItem) {
        boolean inPortfolio = itemData.isInPortfolio();

        List<String> lines = new ArrayList<>(5);

        lines.add(formatQuantityLine(itemData.getRuneliteInventoryQuantity(), itemData.getSuggestionBankQuantity()));
        if (!inPortfolio) {
            Long totalValue = calculateValue(itemData, portfolioItem);
            lines.add("Total value: " + (totalValue == null ? "Unknown" : UIUtilities.formatProfit(totalValue)));
        } else {
            lines.add("Time held: " + formatHoldTime(itemData.getHeldMinutes()));
            Long totalValue = calculateValue(itemData, portfolioItem);
            lines.add("Total value: " + (totalValue == null ? "Unknown" : UIUtilities.formatProfit(totalValue)));
            Long unrealizedPnl = itemData.getUnrealizedUnitPNL() == null ? null : itemData.inventoryTooltipUnrealizedPNL();
            lines.add("Unrealized PNL: " + (unrealizedPnl == null ? "Unknown" : UIUtilities.formatProfit(unrealizedPnl)));
        }
        return lines;
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

    private Long calculateValue(PortfolioItemCardData itemData, Suggestion.PortfolioItem portfolioItem) {
        if (portfolioItem == null || portfolioItem.getAmount() <= 0) {
            return null;
        }
        long totalQuantity = Math.max(0L, (long) itemData.getRuneliteInventoryQuantity() + itemData.getSuggestionBankQuantity());
        return (portfolioItem.getSellValue() * totalQuantity) / portfolioItem.getAmount();
    }

    private String formatQuantityLine(int totalInventoryQuantity, long bankQuantity) {
        String quantity = NumberFormat.getIntegerInstance().format(totalInventoryQuantity);
        if (bankQuantity > 0) {
            return "Quantity: " + quantity + " (" + NumberFormat.getIntegerInstance().format(bankQuantity) + " in bank)";
        }
        return "Quantity: " + quantity;
    }

    private String formatHoldTime(int heldMinutes) {
        if (heldMinutes <= 0) {
            return "0m";
        }

        long elapsed = Math.max(0L, (long) heldMinutes * 60L);
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
