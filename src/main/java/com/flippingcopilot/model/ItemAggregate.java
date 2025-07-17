package com.flippingcopilot.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ItemAggregate {
    private final int itemId;
    private final String itemName;
    private final int numberOfFlips;
    private final int totalQuantityFlipped;
    private final long biggestLoss;
    private final long biggestWin;
    private final long totalProfit;
    private final long avgProfit;
    private final long avgProfitEa;
}