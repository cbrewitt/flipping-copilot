package copilot.model;

import lombok.*;

@Value
public class PortfolioSummary {
    public long portfolioMarketValue, unrealizedProfit;
    public long cashValue, assetsValue;
    public long lockedBuyCash;
}
