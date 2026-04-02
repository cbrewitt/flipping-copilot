package com.flippingcopilot.controller;

import com.flippingcopilot.model.InventorySlotTooltipData;
import com.flippingcopilot.model.PortfolioItemCardData;
import com.flippingcopilot.model.Suggestion;
import com.flippingcopilot.model.SuggestionManager;
import com.flippingcopilot.rs.PortfolioStateRS;
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
    private final ItemController itemController;
    private final PortfolioStateRS portfolioStateRS;

    public InventorySlotTooltipData getTooltipData(int itemId, int quantity) {
        if (!portfolioStateRS.get().isLoaded()) {
            return null;
        }
        int unnotedItemId = itemController.toUnnotedItemId(itemId);
        PortfolioItemCardData itemData = portfolioStateRS.get().getItemCardDataByItemId().get(unnotedItemId);
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
            lines.add("Time held: " + UIUtilities.formatDurationMinutes(itemData.getHeldMinutes()));
            lines.add("Avg buy price: " + (portfolioItem == null || portfolioItem.getAmount() <= 0
                    ? "Unknown"
                    : UIUtilities.formatProfit(portfolioItem.getUnitBuyPrice())));
            Long totalValue = calculateValue(itemData, portfolioItem);
            lines.add("Total value: " + (totalValue == null ? "Unknown" : UIUtilities.formatProfit(totalValue)));
            Long unrealizedProfit = itemData.getUnrealizedUnitProfit() == null ? null : itemData.inventoryTooltipUnrealizedProfit();
            lines.add("Unrealized Profit: " + (unrealizedProfit == null ? "Unknown" : UIUtilities.formatProfit(unrealizedProfit)));
            String unrealizedRoi = calculateUnrealizedRoi(itemData, portfolioItem);
            lines.add("Unrealized ROI: " + (unrealizedRoi == null ? "Unknown" : unrealizedRoi));
        }
        return lines;
    }

    private String calculateUnrealizedRoi(PortfolioItemCardData itemData, Suggestion.PortfolioItem portfolioItem) {
        if (portfolioItem == null || portfolioItem.getAmount() <= 0 || portfolioItem.getUnitBuyPrice() <= 0 || itemData.getUnrealizedUnitProfit() == null) {
            return null;
        }
        double roi = (double) itemData.getUnrealizedUnitProfit() / (double) portfolioItem.getUnitBuyPrice();
        return String.format("%.2f%%", roi * 100.0d);
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

}
