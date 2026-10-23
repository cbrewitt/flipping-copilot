package com.flippingcopilot.util;

import com.flippingcopilot.model.SessionData;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class SessionRateUtilTest {

    private static final long HOUR_MILLIS = 60 * 60 * 1000;

    @Test
    public void hourlyProfitDividesByActiveSessionTime() {
        assertEquals(500_000, SessionRateUtil.hourlyProfit(1_000_000, 2 * HOUR_MILLIS));
        assertEquals(0, SessionRateUtil.hourlyProfit(1_000_000, 0));
    }

    @Test
    public void combinedHourlyProfitSumsEachAccountsOwnRate() {
        Map<Integer, SessionData> sessions = new HashMap<>();
        sessions.put(1, new SessionData(100, 2 * HOUR_MILLIS, 0));
        sessions.put(2, new SessionData(200, HOUR_MILLIS / 2, 0));
        sessions.put(3, new SessionData(300, 0, 0));
        Map<Integer, Long> profitByAccount = new HashMap<>();
        profitByAccount.put(1, 1_000_000L);
        profitByAccount.put(2, 250_000L);
        profitByAccount.put(3, 5_000_000L);

        long combined = SessionRateUtil.combinedHourlyProfit(sessions, (startTime, accountId) -> {
            assertEquals(sessions.get(accountId).startTime, (int) startTime);
            return profitByAccount.get(accountId);
        });

        assertEquals(500_000 + 500_000, combined);
    }
}
