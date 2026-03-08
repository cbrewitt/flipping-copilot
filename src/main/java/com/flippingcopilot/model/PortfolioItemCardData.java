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
    Long unrealizedUnitPNL;
    int heldMinutes;
    boolean inPortfolio;

    public long inventoryTooltipUnrealizedPNL() {
        return ((long) runeliteInventoryQuantity + suggestionBankQuantity) * safeUnrealizedUnitPNL();
    }

    public long flipsUnrealizedPNL() {
        return (long) openFlipsQuantity * safeUnrealizedUnitPNL();
    }

    private long safeUnrealizedUnitPNL() {
        return unrealizedUnitPNL == null ? 0L : unrealizedUnitPNL;
    }
}
