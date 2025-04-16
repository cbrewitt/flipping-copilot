package com.flippingcopilot.ui.graph;

import lombok.Getter;
import lombok.Setter;

import java.awt.Point;

@Getter
@Setter
public class Datapoint {

    private final int time;
    private final int price;
    private final boolean isLow; // true if buy/low point, false if sell/high point
    private final boolean isPrediction;
    private Point screenPosition;

    /**
     * Creates a new data point.
     *
     * @param time The timestamp in Unix seconds
     * @param price The price value
     * @param isLow true if this is a buy/low point, false if sell/high
     * @param isPrediction true if this is a prediction point, false if historical
     */
    public Datapoint(int time, int price, boolean isLow, boolean isPrediction) {
        this.time = time;
        this.price = price;
        this.isLow = isLow;
        this.isPrediction = isPrediction;
        this.screenPosition = new Point();
    }
}