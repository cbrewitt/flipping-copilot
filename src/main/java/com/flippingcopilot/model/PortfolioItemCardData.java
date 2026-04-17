package com.flippingcopilot.model;

import lombok.Value;

@Value
public class PortfolioItemCardData {
    int itemId;
    String itemName;
    int runeliteGeQuantity;
    int runeliteInventoryQuantity;
    int suggestionBankQuantity;
    int openFlipsCount;
    long postTaxSellUnitPrice;
    long unitBuyPrice;
    Long unrealizedUnitProfit;
    int heldMinutes;
    int portfolioQuantity; // quantity with portfolio_id 0 (COFLIP_PORTFOLIO) or 1 (PERSONAL_PORTFOLIO)

    public boolean isInPortfolio() {
        return portfolioQuantity > 0;
    }

    public long inventoryTooltipUnrealizedProfit() {
        return ((long) runeliteInventoryQuantity + suggestionBankQuantity) * safeUnrealizedUnitProfit();
    }

    public long portfolioUnrealizedProfit() {
        return (long) portfolioQuantity * safeUnrealizedUnitProfit();
    }

    private long safeUnrealizedUnitProfit() {
        return unrealizedUnitProfit == null ? 0L : unrealizedUnitProfit;
    }

    public boolean isPartiallyInPortfolio() {
        return isInPortfolio() && portfolioQuantity < runeliteGeQuantity + runeliteInventoryQuantity + suggestionBankQuantity;
    }

    public int getNotInPortfolioQuantity() {
        int clientHeld = runeliteGeQuantity + runeliteInventoryQuantity + suggestionBankQuantity;
        return Math.max(0, clientHeld - portfolioQuantity);
    }
}
