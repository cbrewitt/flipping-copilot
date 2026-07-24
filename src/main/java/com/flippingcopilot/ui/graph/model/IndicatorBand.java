package com.flippingcopilot.ui.graph.model;

public class IndicatorBand {

    public static final IndicatorBand EMPTY = new IndicatorBand(new int[0], new double[0], new double[0], new double[0]);

    public final int[] times;
    public final double[] upper;
    public final double[] lower;
    public final double[] middle;

    public IndicatorBand(int[] times, double[] upper, double[] lower, double[] middle) {
        this.times = times;
        this.upper = upper;
        this.lower = lower;
        this.middle = middle;
    }
}
