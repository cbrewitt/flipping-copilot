package com.flippingcopilot.ui.graph.model;

import lombok.Getter;
import lombok.Setter;

import java.awt.*;

@Getter
@Setter
public class Datapoint {

    public int time;
    public final int price;
    public final Type type;
    public final boolean isLow; // true if buy/low point, false if sell/high point

    // IQR values for prediction points
    public final Integer iqrLower;
    public final Integer iqrUpper;

    // volume
    public long lowVolume;
    public long highVolume;

    // tx
    public long qty;

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

    public static Datapoint newVolumeDatapoint(int time, long lowVolume, long highVolume) {
        Datapoint dp = new Datapoint(time, 0,false, Type.VOLUME_1H);
        dp.lowVolume = lowVolume;
        dp.highVolume = highVolume;
        return dp;
    }

    public Point getHoverPosition(Rectangle pa, Bounds bounds) {
        int x = bounds.toX(pa,time);
        int y = bounds.toY(pa, price);
        if (type == Type.FIVE_MIN_AVERAGE) {
            x += bounds.toW(pa,Constants.FIVE_MIN_SECONDS / 2);
        } else if (type == Type.HOUR_AVERAGE) {
            x += bounds.toW(pa,Constants.HOUR_SECONDS / 2);
        }
        return new Point(x, y);
    }

    public static Datapoint newBuyTx(int time, int price, long qty) {
        Datapoint dp = new Datapoint(time, price,true, Type.FLIP_TRANSACTION);
        dp.qty = qty;
        return dp;
    }

    public static Datapoint newSellTx(int time, int price, long qty) {
        Datapoint dp = new Datapoint(time, price,false, Type.FLIP_TRANSACTION);
        dp.qty = qty;
        return dp;
    }


    public enum Type {
        INSTA_SELL_BUY,
        FIVE_MIN_AVERAGE,
        HOUR_AVERAGE,
        PREDICTION,
        VOLUME_1H,
        FLIP_TRANSACTION
    }
}