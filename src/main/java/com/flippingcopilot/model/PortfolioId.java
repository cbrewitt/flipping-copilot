package com.flippingcopilot.model;

public final class PortfolioId {

    public static final int COFLIP_PORTFOLIO = 0;
    public static final int PERSONAL_PORTFOLIO = 1;
    // Ghost flips: tracked server-side for context but excluded from in-portfolio
    // calculations and stats. Note: shares an int value with ToggleItemPortfolioRequest.REMOVE
    // — they are unrelated semantic uses that happen to share encoding.
    public static final int GHOST = -1;

    private PortfolioId() {}

    public static boolean isInPortfolio(int portfolioId) {
        return portfolioId == COFLIP_PORTFOLIO || portfolioId == PERSONAL_PORTFOLIO;
    }
}
