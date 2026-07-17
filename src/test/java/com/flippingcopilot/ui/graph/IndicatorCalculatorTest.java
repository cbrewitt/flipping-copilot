package com.flippingcopilot.ui.graph;

import com.flippingcopilot.ui.graph.model.IndicatorBand;
import com.flippingcopilot.ui.graph.model.IndicatorSeries;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class IndicatorCalculatorTest {

    @Test
    public void emaSeedsWithSmaThenRecurses() {
        int[] times = {100, 200, 300, 400};
        int[] prices = {10, 20, 30, 40};

        // period 3: seed = SMA(10,20,30) = 20 at t=300; k = 2/(3+1) = 0.5
        // next = 40*0.5 + 20*0.5 = 30 at t=400
        IndicatorSeries s = IndicatorCalculator.ema(times, prices, 3);

        assertArrayEquals(new int[]{300, 400}, s.times);
        assertArrayEquals(new double[]{20.0, 30.0}, s.values, 1e-9);
    }

    @Test
    public void emaTooShortInputReturnsEmpty() {
        IndicatorSeries s = IndicatorCalculator.ema(new int[]{100, 200}, new int[]{10, 20}, 3);
        assertEquals(0, s.times.length);
        assertEquals(0, s.values.length);
    }

    @Test
    public void emaNullInputReturnsEmpty() {
        IndicatorSeries s = IndicatorCalculator.ema(null, null, 3);
        assertEquals(0, s.times.length);
    }

    @Test
    public void bollingerComputesMeanPlusMinusSigma() {
        int[] times = {100, 200, 300, 400};
        int[] prices = {10, 20, 30, 40};

        // period 3, 2σ, population σ:
        // window [10,20,30]: mean 20, σ = sqrt(200/3); window [20,30,40]: mean 30, same σ
        double twoSigma = 2.0 * Math.sqrt(200.0 / 3.0);
        IndicatorBand b = IndicatorCalculator.bollinger(times, prices, 3, 2.0);

        assertArrayEquals(new int[]{300, 400}, b.times);
        assertArrayEquals(new double[]{20.0 + twoSigma, 30.0 + twoSigma}, b.upper, 1e-9);
        assertArrayEquals(new double[]{20.0 - twoSigma, 30.0 - twoSigma}, b.lower, 1e-9);
        assertArrayEquals(new double[]{20.0, 30.0}, b.middle, 1e-9);
    }

    @Test
    public void bollingerTooShortOrNullReturnsEmpty() {
        assertEquals(0, IndicatorCalculator.bollinger(new int[]{100}, new int[]{10}, 3, 2.0).times.length);
        assertEquals(0, IndicatorCalculator.bollinger(null, null, 3, 2.0).times.length);
    }
}
