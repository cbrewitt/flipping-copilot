package copilot.controller;

import static copilot.ui.UIUtilities.*;
import copilot.model.*;
import copilot.rs.*;
import lombok.*;
import lombok.extern.slf4j.*;
import net.runelite.client.game.*;

import javax.inject.*;
import java.text.*;
import java.util.*;
import java.util.List;

@Singleton
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ItemTooltipProvider {

    private final ItemManager itemManager;
    private final Suggestions suggestions;
    private final Items items;
    private final PortfolioStateRS portfolio;

    public ItemTooltipData getTooltipData(int itemId, int quantity, TooltipHoverSource source) {
        if (!portfolio.get().loaded) { return null; }
        int unnotedItemId = items.toUnnotedItemId(itemId);
        var itemData = portfolio.get().itemCardDataByItemId.get(unnotedItemId);
        if (itemData == null) { return null; }

        String itemName = itemManager.getItemComposition(itemData.itemId).getName();
        Suggestion suggestion = suggestions.getSuggestion();
        var portfolioItem = findPortfolioItem(suggestion, itemData.itemId);
        var lines = buildTooltipLines(itemData, portfolioItem, source);
        return new ItemTooltipData(itemData.itemId, quantity, itemName, lines);
    }

    private List<String> buildTooltipLines(PortfolioItem itemData, Suggestion.PortfolioItem portfolioItem, TooltipHoverSource source) {
        boolean inPortfolio = itemData.isInPortfolio();

        List<String> lines = new ArrayList<>(5);

        lines.add(formatQuantityLine(itemData.runeliteInventoryQuantity, itemData.suggestionBankQuantity, source));
        if (itemData.isPartiallyInPortfolio()) {
            lines.add("Quantity in Portfolio: " + NumberFormat.getIntegerInstance().format(itemData.portfolioQuantity));
        }
        if (!inPortfolio) {
            Long totalValue = calculateValue(itemData);
            lines.add("Total value: " + (totalValue == null ? "Unknown" : formatProfit(totalValue)));
        } else {
            lines.add("Time held: " + formatDurationMinutes(itemData.heldMinutes));
            lines.add("Avg buy price: " + (portfolioItem == null || portfolioItem.getAmount() <= 0
                    ? "Unknown"
                    : formatProfit(portfolioItem.getUnitBuyPrice())));
            Long totalValue = calculateValue(itemData);
            lines.add("Total value: " + (totalValue == null ? "Unknown" : formatProfit(totalValue)));
            Long unrealizedProfit = itemData.unrealizedUnitProfit == null ? null : itemData.inventoryTooltipUnrealizedProfit();
            lines.add("Unrealized Profit: " + (unrealizedProfit == null ? "Unknown" : formatProfit(unrealizedProfit)));
            String unrealizedRoi = calculateUnrealizedRoi(itemData, portfolioItem);
            lines.add("Unrealized ROI: " + (unrealizedRoi == null ? "Unknown" : unrealizedRoi));
        }
        return lines;
    }

    private String calculateUnrealizedRoi(PortfolioItem itemData, Suggestion.PortfolioItem portfolioItem) {
        if (portfolioItem == null || portfolioItem.getAmount() <= 0 || portfolioItem.getUnitBuyPrice() <= 0 || itemData.unrealizedUnitProfit == null) {
            return null;
        }
        double roi = (double) itemData.unrealizedUnitProfit / (double) portfolioItem.getUnitBuyPrice();
        return String.format("%.2f%%", roi * 100.0d);
    }

    private Suggestion.PortfolioItem findPortfolioItem(Suggestion suggestion, int itemId) {
        if (suggestion == null || suggestion.portfolioItems == null || suggestion.portfolioItems.isEmpty()) {
            return null;
        }
        for (Suggestion.PortfolioItem portfolioItem : suggestion.portfolioItems) {
            if (portfolioItem != null && portfolioItem.itemId == itemId) { return portfolioItem; }
        }
        return null;
    }

    private Long calculateValue(PortfolioItem itemData) {
        long unitPrice = itemData.postTaxSellUnitPrice;
        if (unitPrice <= 0) { return null; }
        long totalQuantity = Math.max(0L, (long) itemData.runeliteInventoryQuantity + itemData.suggestionBankQuantity);
        return unitPrice * totalQuantity;
    }

    private String formatQuantityLine(int inventoryQuantity, long bankQuantity, TooltipHoverSource source) {
        var fmt = NumberFormat.getIntegerInstance();
        String invPart = "Inventory: " + fmt.format(Math.max(0, inventoryQuantity));
        String bankPart = "Bank: " + fmt.format(Math.max(0L, bankQuantity));

        if (source == TooltipHoverSource.BANK) {
            if (inventoryQuantity <= 0) { return bankPart; }
            return bankPart + ", " + invPart;
        }
        if (bankQuantity <= 0) { return invPart; }
        return invPart + ", " + bankPart;
    }

}
