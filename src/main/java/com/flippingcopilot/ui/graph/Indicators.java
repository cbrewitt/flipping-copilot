package com.flippingcopilot.ui.graph;

import com.flippingcopilot.ui.graph.model.IndicatorBand;
import com.flippingcopilot.ui.graph.model.IndicatorSeries;

import static com.flippingcopilot.ui.graph.IndicatorCalculator.BOLLINGER_STD_DEVS;

public class Indicators {

    private static final int EMA_FAST_SPAN = 43200; // 12h
    private static final int EMA_SLOW_SPAN = 86400; // 24h
    private static final int BOLLINGER_SPAN = 86400; // 24h

    public final IndicatorSeries emaFast;
    public final IndicatorSeries emaSlow;
    public final IndicatorBand bollinger;
    private final int firstTime;

    private Indicators(IndicatorSeries emaFast, IndicatorSeries emaSlow,
                       IndicatorBand bollinger,
                       int firstTime) {
        this.emaFast = emaFast;
        this.emaSlow = emaSlow;
        this.bollinger = bollinger;
        this.firstTime = firstTime;
    }

    public static Indicators compute(int[] lowTimes, int[] lowPrices,
                                     int[] highTimes, int[] highPrices,
                                     int resolutionSeconds) {
        int firstTime = Integer.MAX_VALUE;
        if (lowTimes != null && lowTimes.length > 0) {
            firstTime = Math.min(firstTime, lowTimes[0]);
        }
        if (highTimes != null && highTimes.length > 0) {
            firstTime = Math.min(firstTime, highTimes[0]);
        }

        int fastPeriod = Math.max(1, Math.round((float) EMA_FAST_SPAN / resolutionSeconds));
        int slowPeriod = Math.max(1, Math.round((float) EMA_SLOW_SPAN / resolutionSeconds));
        int bollingerPeriod = Math.max(1, Math.round((float) BOLLINGER_SPAN / resolutionSeconds));

        int[] midTimes = null;
        int[] midPrices = null;
        if (lowTimes != null && highTimes != null) {
            midTimes = new int[Math.min(lowTimes.length, highTimes.length)];
            midPrices = new int[midTimes.length];
            int count = 0;
            int i = 0;
            int j = 0;
            while (i < lowTimes.length && j < highTimes.length) {
                if (lowTimes[i] == highTimes[j]) {
                    midTimes[count] = lowTimes[i];
                    midPrices[count] = (int) (((long) lowPrices[i] + highPrices[j]) / 2);
                    count++;
                    i++;
                    j++;
                } else if (lowTimes[i] < highTimes[j]) {
                    i++;
                } else {
                    j++;
                }
            }
            midTimes = java.util.Arrays.copyOf(midTimes, count);
            midPrices = java.util.Arrays.copyOf(midPrices, count);
        }

        return new Indicators(
                IndicatorCalculator.ema(midTimes, midPrices, fastPeriod),
                IndicatorCalculator.ema(midTimes, midPrices, slowPeriod),
                IndicatorCalculator.bollinger(midTimes, midPrices, bollingerPeriod, BOLLINGER_STD_DEVS),
                firstTime);
    }

    public boolean covers(int time) {
        return firstTime <= time;
    }
}
