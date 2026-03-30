package com.flippingcopilot.model;

import lombok.Value;

@Value
public class PortfolioChangeEvent {

    Location from;
    Location to;
    Integer itemId;
    Long quantity;
    int createdTick;
    String reason;

    public enum Location {
        INVENTORY,
        BANK,
        GE,
        DISOWNED
    }
}
