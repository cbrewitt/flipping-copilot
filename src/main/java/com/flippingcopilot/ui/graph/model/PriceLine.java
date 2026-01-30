package com.flippingcopilot.ui.graph.model;

import lombok.Getter;

@Getter
public class PriceLine {
    private final long price;
    private final String message;
    private final boolean textAbove;

    public PriceLine(long price, String message, boolean textAbove) {
        this.price = price;
        this.message = message;
        this.textAbove = textAbove;
    }
}
