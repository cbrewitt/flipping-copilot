package com.flippingcopilot.ui.flipsdialog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

public class AxisCalculator {

    public static final int DAY_SECONDS = 24*60*60;

    public static List<Tick> calculateYTicks(long minValue, long maxValue, int a) {
        long range = maxValue - minValue;
        long roughSpacing = range / a;
        long magnitude = 1;
        while (magnitude * 10 <= roughSpacing) {
            magnitude *= 10;
        }
        // Round to nearest 1, 2, 5, or 10 * magnitude
        long step;
        if (roughSpacing <= magnitude) {
            step = magnitude;
        } else if (roughSpacing <= 2 * magnitude) {
            step = 2 * magnitude;
        } else if (roughSpacing <= 5 * magnitude) {
            step = 5 * magnitude;
        } else {
            step = 10 * magnitude;
        }

        Function<Long, String> ff = formatFunc(range);

        List<Tick> ticks = new ArrayList<>();
        ticks.add(new Tick(0, "0"));
        long value = 0;
        while (minValue < value) {
            value -= step;
            ticks.add(0, new Tick(value, ff.apply(value)));
        }
        value = 0;
        while (maxValue > value) {
            value += step;
            ticks.add(new Tick(value, ff.apply(value)));
        }
        return ticks;
    }


    public static Function<Long, String> formatFunc(long range) {
        return (v) -> {
            String sign = v < 0 ? "-" : "";
            long absProfit = Math.abs(v);
            if (range >= 1_000_000_000) {
                return sign + String.format("%.1fB", absProfit / 1_000_000_000.0).replace(".0B", "B");
            } else if (range >= 1_000_000) {
                return sign + String.format("%.1fM", absProfit / 1_000_000.0).replace(".0M", "M");
            } else if (range >= 1_000) {
                return sign + String.format("%.1fK", absProfit / 1_000.0).replace(".0K", "K");
            } else {
                return sign + absProfit;
            }
        };
    }

    public static List<Tick> calculateXTicks(int minXValue, int maxXValue) {
        int rangeInDays = (maxXValue - minXValue) / DAY_SECONDS;
        int step = Math.max(rangeInDays / 10, 1)*DAY_SECONDS;
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d");
        List<Tick> ticks = new ArrayList<>();
        int value = (minXValue / DAY_SECONDS) * DAY_SECONDS;
        while(value <= maxXValue) {
            if( value >= minXValue ) {
                ticks.add(new Tick(value, dateFormat.format(new Date(value * 1000L))));
            }
            value+= step;
        }
        return ticks;
    }
}
