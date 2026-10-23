package com.flippingcopilot.util;

import com.flippingcopilot.model.SessionData;

import java.util.Map;
import java.util.function.BiFunction;

public final class SessionRateUtil {

    private SessionRateUtil() {
    }

    public static long hourlyProfit(long profit, long durationMillis) {
        long seconds = durationMillis / 1000;
        float hoursFloat = (((float) seconds) / 3600.0f);
        return hoursFloat == 0 ? 0 : (long) (profit / hoursFloat);
    }

    // sum of each account's own session GP/hr, profitSince takes (start time, account id)
    public static long combinedHourlyProfit(Map<Integer, SessionData> sessionsByAccountId,
                                            BiFunction<Integer, Integer, Long> profitSince) {
        long total = 0;
        for (Map.Entry<Integer, SessionData> e : sessionsByAccountId.entrySet()) {
            SessionData sd = e.getValue();
            total += hourlyProfit(profitSince.apply(sd.startTime, e.getKey()), sd.durationMillis);
        }
        return total;
    }
}
