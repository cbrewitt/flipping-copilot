package copilot.ui.graph;

import copilot.ui.graph.model.*;

import java.time.*;
import java.util.Arrays;

public class AxisCalculator {

    public static TimeAxis calculateTimeAxis(Bounds bounds, int localTimeOffsetSeconds) {

        int timeMin = bounds.xMin, timeMax = bounds.xMax, timeDelta = bounds.xDelta();

        if (timeDelta < Constants.DAY_SECONDS) {
            return calculateSubDayTimeAxis(timeMin, timeMax, timeDelta, localTimeOffsetSeconds);
        }

        int days = timeDelta / Constants.DAY_SECONDS, daysStep = Math.max(1, days / 7);

        int maxDay = ((timeMax + localTimeOffsetSeconds) / Constants.DAY_SECONDS) * Constants.DAY_SECONDS - localTimeOffsetSeconds;
        int minDay = ((timeMin + localTimeOffsetSeconds) / Constants.DAY_SECONDS) * Constants.DAY_SECONDS - localTimeOffsetSeconds;
        int[] dayTicks = new int[0];
        while (maxDay > timeMin) {
            dayTicks = append(dayTicks, maxDay);
            maxDay -= daysStep * Constants.DAY_SECONDS;
        }

        int[] timeTicks = new int[0];
        if (daysStep == 1 && dayTicks.length < 10) {
            int hoursStep = dayTicks.length < 5 ? 6 : 12;
            int firstDay = dayTicks.length < 5 ? minDay : dayTicks[0] - Constants.DAY_SECONDS;
            for (int day : preAppend(dayTicks, firstDay)) {
                for (int hour = hoursStep; hour < 24; hour += hoursStep) {
                    int tick = day + hour * Constants.HOUR_SECONDS;
                    if (tick > timeMin && tick < timeMax) { timeTicks = append(timeTicks, tick); }
                }
            }
        }

        return new TimeAxis(dayTicks, timeTicks, new int[0] );
    }

    private static TimeAxis calculateSubDayTimeAxis(int timeMin, int timeMax, int timeDelta, int localTimeOffsetSeconds) {
        int[] ticks = new int[0];
        int tickInterval;
        if (timeDelta < Constants.HOUR_SECONDS) { tickInterval = Constants.TEN_MIN_SECONDS; } else if (timeDelta < Constants.HOUR_SECONDS * 3) {
            tickInterval  = Constants.THIRTY_MIN_SECONDS;
        } else if (timeDelta < Constants.HOUR_SECONDS * 6) {
            tickInterval = Constants.HOUR_SECONDS;
        } else {
            tickInterval = Constants.HOUR_SECONDS*3;
        }

        int firstTick = ((timeMin+localTimeOffsetSeconds) / tickInterval) * tickInterval - localTimeOffsetSeconds;
        if (firstTick < (timeMin+localTimeOffsetSeconds)) { firstTick += tickInterval; }

        int currentTick = firstTick;
        while (currentTick <= timeMax) {
            ticks = append(ticks, currentTick);
            currentTick += tickInterval;
        }
        return new TimeAxis(new int[0], ticks, ticks );
    }

    public static YAxis calculateNumericAxis(long min, long max, long range, int maxAllowableTicks, int maxAllowableGridLines) {
        if (range <= 0 || max < min) { return new YAxis(new long[]{min}, new long[0]); }

        int[] possibleSteps = {1, 2, 5, 10, 20, 25, 50, 100, 200, 250, 500};
        long magnitudeScale = 1;
        while (magnitudeScale <= Long.MAX_VALUE / 10 && range / magnitudeScale >= 10) { magnitudeScale *= 10; }
        long baseScale = Math.max(1, magnitudeScale / 10);

        long stepSize = 0;
        int numTicks = Integer.MAX_VALUE;

        for (int baseStep : possibleSteps) {
            long candidateStep = multiplySaturated(baseStep, baseScale);

            long candidateTicks = range / candidateStep + 1;

            if (candidateTicks <= maxAllowableTicks && candidateStep > 0) {
                stepSize = candidateStep;
                numTicks = (int) candidateTicks;
                break;
            }
        }

        if (stepSize == 0) {
            stepSize = Long.MAX_VALUE;
            numTicks = 1;
        }

        long remainder = Math.floorMod(min, stepSize), startTick = min;
        if (remainder != 0) {
            long adjustment = stepSize - remainder;
            if (min > Long.MAX_VALUE - adjustment) { return new YAxis(new long[0], new long[0]); }
            startTick += adjustment;
        }

        long[] ticks = new long[numTicks];
        int tickIndex = 0;

        for (long value = startTick; value <= max && tickIndex < numTicks; ) {
            ticks[tickIndex++] = value;
            if (value > Long.MAX_VALUE - stepSize) break;
            value += stepSize;
        }

        if (tickIndex < numTicks) { ticks = Arrays.copyOf(ticks, tickIndex); }

        long[] gridOnlyTicks = new long[0];

        if (ticks.length > 1) {
            long gridStep = stepSize / 2;
            if (gridStep > 0 && (ticks.length * 2 - 1) <= maxAllowableGridLines) {
                gridOnlyTicks = new long[ticks.length - 1];

                for (int i = 0; i < ticks.length - 1; i++) {
                    gridOnlyTicks[i] = ticks[i] + gridStep;
                }
            }
        }

        return new YAxis(ticks, gridOnlyTicks);
    }

    private static long multiplySaturated(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    public static int[] append(int[] arr, int v) {
        int[] result = Arrays.copyOf(arr, arr.length + 1);
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
        var offset = ZoneId.systemDefault().getRules().getOffset(Instant.now());
        return offset.getTotalSeconds();
    }
}
