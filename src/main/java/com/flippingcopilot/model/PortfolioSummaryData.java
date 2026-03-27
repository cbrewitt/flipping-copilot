package com.flippingcopilot.model;

import lombok.Value;

@Value
public class PortfolioSummaryData {
    long portfolioMarketValue;
    long unrealizedProfit;
    long cashValue;
    long assetsValue;
}
