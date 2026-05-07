package com.flippingcopilot.model;

import lombok.Value;

@Value
public class GeHistoryRow {
    int itemId;
    int quantity;
    int price;
    boolean buy;
}
