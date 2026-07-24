package com.flippingcopilot.ui.graph;

import com.flippingcopilot.ui.graph.model.IndicatorBand;
import com.flippingcopilot.ui.graph.model.IndicatorSeries;

public class IndicatorCalculator {

    public static final double BOLLINGER_STD_DEVS = 2.0;

    private IndicatorCalculator() {
    }

    public static IndicatorSeries ema(int[] times, int[] prices, int period) {
        if (times == null || prices == null || times.length < period) {
            return IndicatorSeries.EMPTY;
        }
        int n = times.length;
        int[] outTimes = new int[n - period + 1];
        double[] outValues = new double[n - period + 1];

        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += prices[i];
        }
        double ema = sum / period;
        outTimes[0] = times[period - 1];
        outValues[0] = ema;

        double k = 2.0 / (period + 1);
        for (int i = period; i < n; i++) {
            ema = prices[i] * k + ema * (1 - k);
            outTimes[i - period + 1] = times[i];
            outValues[i - period + 1] = ema;
        }
        return new IndicatorSeries(outTimes, outValues);
    }

    public static IndicatorBand bollinger(int[] times, int[] prices, int period, double numStdDevs) {
        if (times == null || prices == null || times.length < period) {
            return IndicatorBand.EMPTY;
        }
        int n = times.length;
        int outN = n - period + 1;
        int[] outTimes = new int[outN];
        double[] upper = new double[outN];
        double[] lower = new double[outN];
        double[] middle = new double[outN];

        for (int i = 0; i < outN; i++) {
            double sum = 0;
            for (int j = i; j < i + period; j++) {
                sum += prices[j];
            }
            double mean = sum / period;
            double sqSum = 0;
            for (int j = i; j < i + period; j++) {
                double d = prices[j] - mean;
                sqSum += d * d;
            }
            double sigma = Math.sqrt(sqSum / period);
            outTimes[i] = times[i + period - 1];
            upper[i] = mean + numStdDevs * sigma;
            lower[i] = mean - numStdDevs * sigma;
            middle[i] = mean;
        }
        return new IndicatorBand(outTimes, upper, lower, middle);
    }
}
