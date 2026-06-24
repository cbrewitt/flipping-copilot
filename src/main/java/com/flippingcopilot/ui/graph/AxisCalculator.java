package com.flippingcopilot.ui.graph;


import com.flippingcopilot.ui.graph.model.Bounds;
import com.flippingcopilot.ui.graph.model.Constants;
import com.flippingcopilot.ui.graph.model.YAxis;
import com.flippingcopilot.ui.graph.model.TimeAxis;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

public class AxisCalculator {

    public static TimeAxis calculateTimeAxis(Bounds bounds, int localTimeOffsetSeconds) {

        int timeMin = bounds.xMin;
        int timeMax = bounds.xMax;
        int timeDelta = bounds.xDelta();

        if (timeDelta < Constants.DAY_SECONDS) {
            return calculateSubDayTimeAxis(timeMin, timeMax, timeDelta, localTimeOffsetSeconds);
        }

        int days = timeDelta / Constants.DAY_SECONDS;
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

    private static TimeAxis calculateSubDayTimeAxis(int timeMin, int timeMax, int timeDelta, int localTimeOffsetSeconds) {
        int[] ticks = new int[]{};
        int tickInterval;
        if (timeDelta < Constants.HOUR_SECONDS) {
            tickInterval = Constants.TEN_MIN_SECONDS;
        } else if (timeDelta < Constants.HOUR_SECONDS * 3) {
            tickInterval  = Constants.THIRTY_MIN_SECONDS;
        } else if (timeDelta < Constants.HOUR_SECONDS * 6) {
            tickInterval = Constants.HOUR_SECONDS;
        } else {
            tickInterval = Constants.HOUR_SECONDS*3;
        }

        int firstTick = ((timeMin+localTimeOffsetSeconds) / tickInterval) * tickInterval - localTimeOffsetSeconds;
        if (firstTick < (timeMin+localTimeOffsetSeconds)) {
            firstTick += tickInterval;
        }

        int currentTick = firstTick;
        while (currentTick <= timeMax) {
            ticks = append(ticks, currentTick);
            currentTick += tickInterval;
        }
        return new TimeAxis(
                new int[]{},
                ticks,
                ticks
        );
    }

    public static YAxis calculatePriceAxis(Bounds bounds) {
        return calculateNumericAxis((int) bounds.yMin, (int) bounds.yMax, (int) bounds.yDelta(), 18, 28);
    }

    public static YAxis calculateVolumeAxis(Bounds bounds) {
        return calculateNumericAxis((int) bounds.y2Min, (int) bounds.y2Max, (int) bounds.y2Delta(), 8, 16);
    }

    private static YAxis calculateNumericAxis(int min, int max, int range, int maxAllowableTicks, int maxAllowableGridLines) {
        int magnitude = (int) Math.floor(Math.log10(range));
        int[] possibleSteps = {1, 2, 5, 10, 20, 25, 50, 100, 200, 250, 500};

        int stepSize = 0;
        int numTicks = Integer.MAX_VALUE;

        for (int baseStep : possibleSteps) {
            int candidateStep = baseStep * (int) Math.pow(10, magnitude - 1);
            candidateStep = Math.max(1, candidateStep);

            int candidateTicks = range / candidateStep + 1;

            if (candidateTicks <= maxAllowableTicks && candidateStep > 0) {
                stepSize = candidateStep;
                numTicks = candidateTicks;
                break;
            }
        }

        if (stepSize == 0) {
            stepSize = 500 * (int) Math.pow(10, magnitude - 1);
            numTicks = range / stepSize + 1;
        }

        int startTick = (min / stepSize) * stepSize;
        if (startTick < min) {
            startTick += stepSize;
        }

        long[] ticks = new long[numTicks];
        int tickIndex = 0;

        for (int value = startTick; value <= max && tickIndex < numTicks; value += stepSize) {
            ticks[tickIndex++] = value;
        }

        if (tickIndex < numTicks) {
            long[] resizedTicks = new long[tickIndex];
            System.arraycopy(ticks, 0, resizedTicks, 0, tickIndex);
            ticks = resizedTicks;
        }

        long[] gridOnlyTicks = new long[0];

        if (ticks.length > 1) {
            int gridStep = stepSize / 2;
            if (gridStep > 0 && (ticks.length * 2 - 1) <= maxAllowableGridLines) {
                gridOnlyTicks = new long[ticks.length - 1];

                for (int i = 0; i < ticks.length - 1; i++) {
                    gridOnlyTicks[i] = ticks[i] + gridStep;
                }
            }
        }

        return new YAxis(ticks, gridOnlyTicks);
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
