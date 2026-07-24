package com.flippingcopilot.ui.graph;

import com.flippingcopilot.ui.graph.model.Data;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class IndicatorsTest {

    private static int[] range(int start, int step, int count) {
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = start + i * step;
        }
        return out;
    }

    private static int[] constant(int value, int count) {
        int[] out = new int[count];
        java.util.Arrays.fill(out, value);
        return out;
    }

    @Test
    public void computeProducesAllSeriesPerSide() {
        int n = 30; // > slow EMA period (24) and Bollinger period (24) at 3600s resolution
        int[] times = range(3600, 3600, n);
        Indicators ind = Indicators.compute(
                times, constant(100, n),
                times, constant(110, n), 3600);

        // warm-up: outputs start at period-1
        assertEquals(n - 12 + 1, ind.emaFast.times.length);
        assertEquals(n - 24 + 1, ind.emaSlow.times.length);
        assertEquals(n - 24 + 1, ind.bollinger.times.length);

        // mid-price of low=100, high=110 is 105; constant prices -> EMA flat at the mid price
        assertEquals(105.0, ind.emaFast.values[0], 1e-9);
        // constant mid-price -> zero std dev, so upper/middle/lower all collapse to 105
        assertEquals(105.0, ind.bollinger.middle[0], 1e-9);
        assertEquals(105.0, ind.bollinger.upper[0], 1e-9);
        assertEquals(105.0, ind.bollinger.lower[0], 1e-9);
    }

    @Test
    public void midPriceDoesNotOverflowForHighValueItems() {
        // low+high exceeds Integer.MAX_VALUE (e.g. billion-gp items like Twisted bow);
        // the mid must be their true average, not a negative int overflow.
        int n = 30;
        int[] times = range(3600, 3600, n);
        Indicators ind = Indicators.compute(
                times, constant(2_000_000_000, n),
                times, constant(2_100_000_000, n), 3600);

        assertEquals(2_050_000_000.0, ind.emaFast.values[0], 1.0);
    }

    @Test
    public void computeWithNullArraysProducesEmptySeries() {
        Indicators ind = Indicators.compute(null, null, null, null, 3600);
        assertNotNull(ind);
        assertEquals(0, ind.emaFast.times.length);
        assertEquals(0, ind.bollinger.times.length);
        assertFalse(ind.covers(0));
    }

    @Test
    public void coversReflectsEarliestPriceTime() {
        int n = 30;
        int[] times = range(1000, 3600, n);
        Indicators ind = Indicators.compute(
                times, constant(100, n),
                times, constant(110, n), 3600);
        assertTrue(ind.covers(1000));
        assertTrue(ind.covers(50_000));
        assertFalse(ind.covers(999));
    }

    @Test
    public void emaPeriodDerivedFromResolution() {
        // 30 hourly bins; slow EMA span 24h at 3600s/bin -> period 24 -> output length 30-24+1 = 7
        int n = 30;
        int[] times = range(3600, 3600, n);
        Indicators ind = Indicators.compute(times, constant(100, n), times, constant(110, n), 3600);
        assertEquals(7, ind.emaSlow.times.length);   // period 24
        assertEquals(19, ind.emaFast.times.length);  // period 12 (span 43200/3600)
        // mid-price of low=100, high=110 is 105
        assertEquals(105.0, ind.emaFast.values[0], 1e-9);
    }

    @Test
    public void fiveMinuteResolutionUsesLargerPeriod() {
        int n = 300;
        int[] times = range(300, 300, n);
        // slow span 86400 / 300 = 288 -> output length 300-288+1 = 13
        Indicators ind = Indicators.compute(times, constant(100, n), times, constant(110, n), 300);
        assertEquals(13, ind.emaSlow.times.length);
    }

    @Test
    public void dataManagerComputesBothResolutions() {
        int n = 30;
        // 5m bins need enough history to fill the 24h Bollinger/EMA span (period 288 at 300s resolution)
        int n5m = 300;
        Data data = new Data();
        data.low1hTimes = range(3600, 3600, n);
        data.low1hPrices = constant(100, n);
        data.high1hTimes = range(3600, 3600, n);
        data.high1hPrices = constant(110, n);
        data.low5mTimes = range(300, 300, n5m);
        data.low5mPrices = constant(100, n5m);
        data.high5mTimes = range(300, 300, n5m);
        data.high5mPrices = constant(110, n5m);
        data.volume1hTimes = range(3600, 3600, n);
        data.volume1hLows = constant(5, n);
        data.volume1hHighs = constant(7, n);
        data.volume5mTimes = range(300, 300, n5m);
        data.volume5mLows = constant(2, n5m);
        data.volume5mHighs = constant(3, n5m);
        // lowLatestTimes left null: processDatapoints() returns early, indicators must still compute

        DataManager dm = new DataManager(data, null);

        assertNotNull(dm.indicators1h);
        assertNotNull(dm.indicators5m);
        assertTrue(dm.indicators1h.emaFast.times.length > 0);
        assertTrue(dm.indicators1h.emaSlow.times.length > 0);
        assertTrue(dm.indicators5m.bollinger.times.length > 0);
    }
}
