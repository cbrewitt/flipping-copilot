package com.flippingcopilot.ui.graph.model;

import com.flippingcopilot.ui.graph.PlotArea;
import lombok.Getter;
import lombok.Setter;

import java.awt.Point;

@Getter
@Setter
public class Datapoint {

    public final int time;
    public final int price;
    public final Type type;
    public final boolean isLow; // true if buy/low point, false if sell/high point

    // IQR values for prediction points
    public final Integer iqrLower;
    public final Integer iqrUpper;

    public Datapoint(int time, int price, boolean isLow, Type type) {
        this.time = time;
        this.price = price;
        this.isLow = isLow;
        this.type = type;
        this.iqrLower = null;
        this.iqrUpper = null;
    }

    public Datapoint(int time, int price, int iqrLower, int iqrUpper, boolean isLow) {
        this.time = time;
        this.price = price;
        this.isLow = isLow;
        this.type = Type.PREDICTION;
        this.iqrLower = iqrLower;
        this.iqrUpper = iqrUpper;
    }

    public Point getHoverPosition(PlotArea pa) {
        int x = pa.timeToX(time);
        int y = pa.priceToY(price);
        if (type == Type.FIVE_MIN_AVERAGE) {
            x += pa.timeDeltaToXDelta(Constants.FIVE_MIN_SECONDS / 2);
        } else if (type == Type.HOUR_AVERAGE) {
            x += pa.timeDeltaToXDelta(Constants.HOUR_SECONDS / 2);
        }
        return new Point(x, y);
    }

    public enum Type {
        INSTA_SELL_BUY,
        FIVE_MIN_AVERAGE,
        HOUR_AVERAGE,
        PREDICTION,
    }
}