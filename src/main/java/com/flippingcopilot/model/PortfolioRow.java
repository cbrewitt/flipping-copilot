package com.flippingcopilot.model;

import lombok.Value;

import java.util.List;

@Value
public class PortfolioRow {
    int itemId;
    String itemName;
    int quantity;
    long marketValue;
    long unrealizedProfit;
    Double unrealizedRoi; // null when unknown
    Long avgBuyPrice; // null when unknown
    int heldMinutes;
    List<String> accounts;
}
