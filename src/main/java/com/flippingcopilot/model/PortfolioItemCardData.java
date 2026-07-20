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

    public int getPortfolioBankQuantity() {
        long quantityOutsideBank = Math.max(0L, runeliteGeQuantity)
                + Math.max(0L, runeliteInventoryQuantity);
        long bankPortfolioQuantity = Math.max(0L, (long) portfolioQuantity - quantityOutsideBank);
        return (int) Math.min(Math.max(0L, suggestionBankQuantity), bankPortfolioQuantity);
    }

    public boolean hasPortfolioQuantityInBank() {
        return getPortfolioBankQuantity() > 0;
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
