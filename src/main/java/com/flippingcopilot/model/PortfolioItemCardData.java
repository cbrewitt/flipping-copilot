package com.flippingcopilot.model;

import lombok.Value;

@Value
public class PortfolioItemCardData {
    int itemId;
    String itemName;
    int runeliteGeQuantity;
    int runeliteInventoryQuantity;
    int suggestionBankQuantity;
    int openFlipsQuantity;
    int openFlipsCount;
    long postTaxSellUnitPrice;
    long unitBuyPrice;
    Long unrealizedUnitProfit;
    int heldMinutes;
    boolean inPortfolio;

    public long inventoryTooltipUnrealizedProfit() {
        return ((long) runeliteInventoryQuantity + suggestionBankQuantity) * safeUnrealizedUnitProfit();
    }

    public long flipsUnrealizedProfit() {
        return (long) openFlipsQuantity * safeUnrealizedUnitProfit();
    }

    private long safeUnrealizedUnitProfit() {
        return unrealizedUnitProfit == null ? 0L : unrealizedUnitProfit;
    }
}
