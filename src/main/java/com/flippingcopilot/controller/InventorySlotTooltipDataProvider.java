package com.flippingcopilot.controller;

import com.flippingcopilot.model.FlipV2;
import com.flippingcopilot.model.InventorySlotTooltipData;
import com.flippingcopilot.util.ProfitCalculator;
import lombok.RequiredArgsConstructor;
import net.runelite.client.game.ItemManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class InventorySlotTooltipDataProvider {

    private final ItemManager itemManager;
    private final InventoryPortfolioService portfolioService;

    public InventorySlotTooltipData getTooltipData(int itemId, int quantity) {
        int unnotedItemId = portfolioService.toUnnotedItemId(itemId);
        if (portfolioService.isCurrencyItem(unnotedItemId)) {
            return null;
        }

        String itemName = itemManager.getItemComposition(unnotedItemId).getName();
        Integer accountId = portfolioService.getActiveAccountId();
        FlipV2 openFlip = portfolioService.getOpenFlip(unnotedItemId, accountId);
        List<String> lines = buildTooltipLines(unnotedItemId, itemName, quantity, openFlip);
        return new InventorySlotTooltipData(unnotedItemId, quantity, itemName, lines);
    }

    private List<String> buildTooltipLines(int itemId, String itemName, int quantity, FlipV2 openFlip) {
        List<String> lines = new ArrayList<>(3);

        if (openFlip == null) {
            lines.add("Not in portfolio");
            return lines;
        }

        lines.add(itemName + " x" + NumberFormat.getIntegerInstance().format(quantity));
        long builtInPrice = itemManager.getItemPrice(itemId);
        long gpPerItem = ProfitCalculator.calculateProfitPerItem(itemId, (int) builtInPrice, openFlip.getAvgBuyPrice());
        long totalProfit = gpPerItem * quantity;
        lines.add("Profit est: " + NumberFormat.getIntegerInstance().format(totalProfit) + " gp");
        lines.add("Hold time: " + formatHoldTime(openFlip.getOpenedTime()));
        return lines;
    }

    private String formatHoldTime(int openedTimeEpochSeconds) {
        if (openedTimeEpochSeconds <= 0) {
            return "0m";
        }

        long elapsed = Math.max(0, Instant.now().getEpochSecond() - openedTimeEpochSeconds);
        long hours = elapsed / 3600;
        long minutes = (elapsed % 3600) / 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }
}
