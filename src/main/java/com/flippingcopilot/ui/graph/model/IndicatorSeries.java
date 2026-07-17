package com.flippingcopilot.ui.graph.model;

public class IndicatorSeries {

    public static final IndicatorSeries EMPTY = new IndicatorSeries(new int[0], new double[0]);

    public final int[] times;
    public final double[] values;

    public IndicatorSeries(int[] times, double[] values) {
        this.times = times;
        this.values = values;
    }
}
