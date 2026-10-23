package com.flippingcopilot.model;

import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FlipManagerSessionWindowTest {

    private static final int USER_ID = 7;
    private static final int MAIN = 1;
    private static final int ALT = 2;
    private static final int IDLE_ALT = 3;
    private static final int T0 = 1_700_000_000;
    private static final int DAY = 24 * 60 * 60;

    private FlipManager manager;

    @Before
    public void setUp() {
        manager = new FlipManager(null);
        manager.setCopilotUserId(USER_ID);
        merge(
                flip(MAIN, T0 + 100, 1_000),          // before main's session
                flip(MAIN, T0 + 5_000, 2_000),        // in main's session
                flip(ALT, T0 + 400, 400_000),         // before alt's session
                flip(ALT, T0 + 1_000, 30_000),        // in alt's session
                flip(ALT, T0 + 10 * DAY, 600),        // in alt's session, a later week
                flip(IDLE_ALT, T0 + 6_000, 5_000_000) // account without an active session
        );
    }

    @Test
    public void allAccountsCountsEachAccountFromItsOwnSessionStart() {
        manager.setIntervalSessionStartTimes(sessionStarts());

        Stats stats = manager.getIntervalStats();
        assertEquals(3, stats.flipsMade);
        assertEquals(32_600, stats.profit);
    }

    @Test
    public void selectedAccountOnlyCountsItsOwnSessionWindow() {
        manager.setIntervalSessionStartTimes(sessionStarts());

        manager.setIntervalAccount(ALT);
        assertEquals(2, manager.getIntervalStats().flipsMade);
        assertEquals(30_600, manager.getIntervalStats().profit);

        manager.setIntervalAccount(IDLE_ALT);
        assertEquals(0, manager.getIntervalStats().flipsMade);
    }

    @Test
    public void mergedFlipsRespectSessionWindows() {
        manager.setIntervalSessionStartTimes(sessionStarts());

        merge(
                flip(ALT, T0 + 2_000, 7),
                flip(MAIN, T0 + 3_000, 11),       // before main's session
                flip(IDLE_ALT, T0 + 7_000, 13)
        );

        Stats stats = manager.getIntervalStats();
        assertEquals(4, stats.flipsMade);
        assertEquals(32_607, stats.profit);
    }

    @Test
    public void pageFlipsAreFilteredBySessionWindows() {
        manager.setIntervalSessionStartTimes(sessionStarts());

        List<FlipV2> page = manager.getPageFlips(1, 50);
        assertEquals(Arrays.asList(600L, 2_000L, 30_000L),
                page.stream().map(FlipV2::getProfit).collect(Collectors.toList()));
    }

    @Test
    public void fixedStartTimeReplacesSessionWindows() {
        manager.setIntervalSessionStartTimes(sessionStarts());
        manager.setIntervalStartTime(T0 + 300);

        Stats stats = manager.getIntervalStats();
        assertEquals(5, stats.flipsMade);
        assertEquals(5_432_600, stats.profit);
        assertEquals(5, manager.getPageFlips(1, 50).size());
    }

    @Test
    public void noActiveSessionsCountsNothing() {
        manager.setIntervalSessionStartTimes(Collections.emptyMap());

        assertEquals(0, manager.getIntervalStats().flipsMade);
        assertTrue(manager.getPageFlips(1, 50).isEmpty());
    }

    private static Map<Integer, Integer> sessionStarts() {
        Map<Integer, Integer> starts = new HashMap<>();
        starts.put(MAIN, T0 + 4_000);
        starts.put(ALT, T0 + 500);
        return starts;
    }

    private void merge(FlipV2... flips) {
        assertTrue(manager.mergeFlips(new ArrayList<>(Arrays.asList(flips)), USER_ID));
    }

    private static FlipV2 flip(int accountId, int closedTime, long profit) {
        FlipV2 f = new FlipV2();
        f.setId(UUID.randomUUID());
        f.setAccountId(accountId);
        f.setItemId(560);
        f.setOpenedTime(closedTime - 60);
        f.setClosedTime(closedTime);
        f.setOpenedQuantity(10);
        f.setClosedQuantity(10);
        f.setSpent(10_000);
        f.setReceivedPostTax(10_000 + profit);
        f.setProfit(profit);
        f.setStatus(FlipStatus.FINISHED);
        f.setPortfolioId(PortfolioId.COFLIP_PORTFOLIO);
        f.setUpdatedTime(closedTime);
        return f;
    }
}
