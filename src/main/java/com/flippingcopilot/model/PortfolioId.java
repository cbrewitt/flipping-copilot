package com.flippingcopilot.model;

public final class PortfolioId {

    public static final int COFLIP_PORTFOLIO = 0;
    public static final int PERSONAL_PORTFOLIO = 1;
    // Ghost flips: tracked server-side for context but excluded from in-portfolio
    // calculations and stats. Note: shares an int value with ToggleItemPortfolioRequest.REMOVE
    // — they are unrelated semantic uses that happen to share encoding.
    public static final int GHOST = -1;
    // Disappeared buckets: items vanished from inventory. Server-side mapping is
    // 0 -> -2 (coflip), -1 -> -3 (ghost), 1 -> -4 (personal). They can be revived
    // back to their original portfolio if the items reappear.
    public static final int DISAPPEARED_COFLIP = -2;
    public static final int DISAPPEARED_GHOST = -3;
    public static final int DISAPPEARED_PERSONAL = -4;

    private PortfolioId() {}

    public static boolean isInPortfolio(int portfolioId) {
        return portfolioId == COFLIP_PORTFOLIO || portfolioId == PERSONAL_PORTFOLIO;
    }

    public static boolean isDisappeared(int portfolioId) {
        return portfolioId == DISAPPEARED_COFLIP
                || portfolioId == DISAPPEARED_GHOST
                || portfolioId == DISAPPEARED_PERSONAL;
    }

    public static boolean isMissed(int portfolioId) {
        return portfolioId == GHOST || isDisappeared(portfolioId);
    }
}
