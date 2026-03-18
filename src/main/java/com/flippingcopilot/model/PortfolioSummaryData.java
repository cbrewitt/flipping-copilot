package com.flippingcopilot.model;

import lombok.Value;

@Value
public class PortfolioSummaryData {
    long portfolioMarketValue;
    long unrealizedPnl;
    long cashValue;
    long assetsValue;
}
