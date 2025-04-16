package com.flippingcopilot.ui.graph;


import com.flippingcopilot.ui.graph.model.Constants;
import com.flippingcopilot.ui.graph.model.PriceAxis;
import com.flippingcopilot.ui.graph.model.TimeAxis;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

public class AxisCalculator {

    public static TimeAxis calculateTimeAxis(PlotArea pa, int localTimeOffsetSeconds) {

        int timeMin = pa.bounds.xMin;
        int timeMax = pa.bounds.xMax;

        int days = pa.bounds.xDelta() / Constants.DAY_SECONDS;
        int daysStep = Math.max(1, days / 7);

        int maxDay = ((timeMax + localTimeOffsetSeconds) / Constants.DAY_SECONDS) * Constants.DAY_SECONDS - localTimeOffsetSeconds;
        int minDay = ((timeMin + localTimeOffsetSeconds) / Constants.DAY_SECONDS) * Constants.DAY_SECONDS - localTimeOffsetSeconds;
        int[] dayTicks = new int[]{};
        while (maxDay > timeMin) {
            dayTicks = append(dayTicks, maxDay);
            maxDay -= daysStep * Constants.DAY_SECONDS;
        }

        int[] timeTicks = new int[]{};
        if (daysStep == 1) {
            if (dayTicks.length < 5) {
                // add 06:00, 12:00, 18:00
                for (int d : preAppend(dayTicks, minDay)) {
                    int tick06 = d + 6 * 60 * 60;     // 06:00
                    int tick12 = d + 12 * 60 * 60;    // 12:00
                    int tick18 = d + 18 * 60 * 60;    // 18:00

                    if (tick06 < timeMax && tick06 > timeMin) {
                        timeTicks = append(timeTicks, tick06);
                    }
                    if (tick12 < timeMax && tick12 > timeMin) {
                        timeTicks = append(timeTicks, tick12);
                    }
                    if (tick18 < timeMax && tick18 > timeMin) {
                        timeTicks = append(timeTicks, tick18);
                    }
                }
            } else if (dayTicks.length < 10){
                // add only 12:00
                for (int d : preAppend(dayTicks, dayTicks[0] - Constants.DAY_SECONDS)) {
                    int tick12 = d + 12 * 60 * 60;    // 12:00
                    if (tick12 > timeMin && tick12 < timeMax) {
                        timeTicks = append(timeTicks, tick12);
                    }
                }
            }
        }

        return new TimeAxis(
                dayTicks,
                timeTicks,
                new int[]{}
        );
    }

    public static PriceAxis calculatePriceAxis(PlotArea pa) {
        int maxAllowableTicks = 18;
        int maxAllowableGridLines = 28;

        int priceRange = pa.bounds.yDelta();
        int priceMin = pa.bounds.yMin;
        int priceMax = pa.bounds.yMax;

        int magnitude = (int) Math.floor(Math.log10(priceRange));
        int[] possibleSteps = {1, 2, 5, 10, 20, 25, 50, 100, 200, 250, 500};

        int stepSize = 0;
        int numTicks = Integer.MAX_VALUE;

        for (int baseStep : possibleSteps) {
            int candidateStep = baseStep * (int) Math.pow(10, magnitude - 1);
            candidateStep = Math.max(1, candidateStep);

            int candidateTicks = priceRange / candidateStep + 1;

            if (candidateTicks <= maxAllowableTicks && candidateStep > 0) {
                stepSize = candidateStep;
                numTicks = candidateTicks;
                break;
            }
        }

        if (stepSize == 0) {
            stepSize = 500 * (int) Math.pow(10, magnitude - 1);
            numTicks = priceRange / stepSize + 1;
        }

        int startTick = (priceMin / stepSize) * stepSize;
        if (startTick < priceMin) {
            startTick += stepSize;
        }

        int[] tickPrices = new int[numTicks];
        int tickIndex = 0;

        for (int price = startTick; price <= priceMax && tickIndex < numTicks; price += stepSize) {
            tickPrices[tickIndex++] = price;
        }

        if (tickIndex < numTicks) {
            int[] resizedTicks = new int[tickIndex];
            System.arraycopy(tickPrices, 0, resizedTicks, 0, tickIndex);
            tickPrices = resizedTicks;
        }

        int[] gridOnlyPrices = new int[0];

        if (tickPrices.length > 1) {
            int gridStep = stepSize / 2;
            if (gridStep > 0 && (tickPrices.length * 2 - 1) <= maxAllowableGridLines) {
                gridOnlyPrices = new int[tickPrices.length - 1];

                for (int i = 0; i < tickPrices.length - 1; i++) {
                    gridOnlyPrices[i] = tickPrices[i] + gridStep;
                }
            }
        }

        return new PriceAxis(tickPrices, gridOnlyPrices);
    }


    public static int[] append(int[] arr, int v) {
        int[] result = new int[arr.length + 1];
        System.arraycopy(arr, 0, result, 0, arr.length);
        result[arr.length] = v;
        return result;
    }

    public static int[] preAppend(int[] arr, int v) {
        int[] result = new int[arr.length + 1];
        result[0] = v;
        System.arraycopy(arr, 0, result, 1, arr.length);
        return result;
    }

    public static int getLocalTimeOffsetSeconds() {
        ZoneOffset offset = ZoneId.systemDefault().getRules().getOffset(Instant.now());
        return offset.getTotalSeconds();
    }
}
