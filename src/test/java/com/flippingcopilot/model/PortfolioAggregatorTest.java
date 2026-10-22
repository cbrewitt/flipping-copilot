package com.flippingcopilot.model;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PortfolioAggregatorTest {

    private static final int MAIN = 1;
    private static final int ALT = 2;
    private static final int BANK_ALT = 3;
    private static final int DEATH_RUNE = 560;
    private static final int BLOOD_RUNE = 565;
    private static final long ALT_SAVED_AT = 1_700_000_000L;

    private final PortfolioState live = new PortfolioState(true,
            Collections.singletonMap(DEATH_RUNE, card(DEATH_RUNE, 100, 178, 180, 2L, 30)),
            new PortfolioSummaryData(50_000, 200, 30_000, 18_000, 2_000));

    private final AccountPortfolioSnapshot altSnapshot = new AccountPortfolioSnapshot("Alt", ALT, ALT_SAVED_AT,
            100_000, -400, 80_000, 18_100, 1_900, Arrays.asList(
            new AccountPortfolioSnapshot.Item(DEATH_RUNE, 50, 170, 182, 12L, 90),
            new AccountPortfolioSnapshot.Item(BLOOD_RUNE, 10, 1_000, 900, -100L, 10)));

    @Test
    public void liveAccountSelectedMatchesLivePortfolio() {
        PortfolioView view = build(MAIN, MAIN, snapshots(altSnapshot));

        assertTrue(view.isLiveAccount());
        assertEquals(live.getSummaryData(), view.getSummary());
        assertEquals(1, view.getRows().size());
        PortfolioRow row = view.getRows().get(0);
        assertEquals(100, row.getQuantity());
        assertEquals(18_000, row.getMarketValue());
        assertEquals(200, row.getUnrealizedProfit());
        assertEquals(2d / 178d, row.getUnrealizedRoi(), 1e-9);
        assertEquals(Long.valueOf(178), row.getAvgBuyPrice());
        assertEquals(30, row.getHeldMinutes());
        assertEquals(Collections.singletonList("Main"), row.getAccounts());
    }

    @Test
    public void allAccountsSumsSummariesAndMergesHoldings() {
        PortfolioView view = build(null, MAIN, snapshots(altSnapshot));

        assertFalse(view.isLiveAccount());
        assertEquals(new PortfolioSummaryData(150_000, -200, 110_000, 36_100, 3_900), view.getSummary());

        PortfolioRow deathRunes = row(view, DEATH_RUNE);
        assertEquals(150, deathRunes.getQuantity());
        assertEquals(18_000 + 9_100, deathRunes.getMarketValue());
        assertEquals(200 + 600, deathRunes.getUnrealizedProfit());
        assertEquals(800d / (100 * 178 + 50 * 170), deathRunes.getUnrealizedRoi(), 1e-9);
        assertEquals(Long.valueOf(175), deathRunes.getAvgBuyPrice()); // (100*178 + 50*170) / 150 = 175.33
        assertEquals(90, deathRunes.getHeldMinutes());
        assertEquals(Arrays.asList("Main", "Alt"), deathRunes.getAccounts());

        PortfolioRow bloodRunes = row(view, BLOOD_RUNE);
        assertEquals(-1_000, bloodRunes.getUnrealizedProfit());
        assertEquals(Collections.singletonList("Alt"), bloodRunes.getAccounts());

        assertEquals(Arrays.asList(new PortfolioView.Source("Main", null), new PortfolioView.Source("Alt", ALT_SAVED_AT)),
                view.getSources());
        assertEquals(Collections.singletonList("Bank alt"), view.getMissingAccounts());
    }

    @Test
    public void liveAccountUsesLiveStateOverItsOwnSnapshot() {
        AccountPortfolioSnapshot staleMain = new AccountPortfolioSnapshot("Main", MAIN, ALT_SAVED_AT,
                999_999, 999_999, 0, 0, 0, Collections.emptyList());

        PortfolioView view = build(null, MAIN, snapshots(altSnapshot, staleMain));

        assertEquals(150_000, view.getSummary().getPortfolioMarketValue());
        assertEquals(-200, view.getSummary().getUnrealizedProfit());
    }

    @Test
    public void otherAccountSelectedUsesItsSnapshot() {
        PortfolioView view = build(ALT, MAIN, snapshots(altSnapshot));

        assertFalse(view.isLiveAccount());
        assertEquals(altSnapshot.summary(), view.getSummary());
        assertEquals(2, view.getRows().size());
        assertEquals(Collections.singletonList(new PortfolioView.Source("Alt", ALT_SAVED_AT)), view.getSources());
    }

    @Test
    public void otherAccountWithoutSnapshotIsReportedMissing() {
        PortfolioView view = build(BANK_ALT, MAIN, snapshots(altSnapshot));

        assertEquals(0, view.getSummary().getPortfolioMarketValue());
        assertTrue(view.getRows().isEmpty());
        assertEquals(Collections.singletonList("Bank alt"), view.getMissingAccounts());
    }

    @Test
    public void allAccountsWithoutLiveAccountUsesSnapshotsOnly() {
        PortfolioView view = build(null, null, snapshots(altSnapshot));

        assertEquals(altSnapshot.summary(), view.getSummary());
        assertEquals(Arrays.asList("Bank alt", "Main"), view.getMissingAccounts());
        assertEquals(50, row(view, DEATH_RUNE).getQuantity());
        assertEquals(Collections.singletonList("Alt"), row(view, DEATH_RUNE).getAccounts());
    }

    @Test
    public void unknownUnitProfitGivesUnknownRoi() {
        AccountPortfolioSnapshot unknown = new AccountPortfolioSnapshot("Alt", ALT, ALT_SAVED_AT, 0, 0, 0, 0, 0,
                Collections.singletonList(new AccountPortfolioSnapshot.Item(BLOOD_RUNE, 5, 0, 900, null, 1)));

        PortfolioRow row = row(build(ALT, MAIN, snapshots(unknown)), BLOOD_RUNE);

        assertNull(row.getUnrealizedRoi());
        assertNull(row.getAvgBuyPrice());
        assertEquals(0, row.getUnrealizedProfit());
    }

    private PortfolioView build(Integer selected, Integer liveAccountId, Map<Integer, AccountPortfolioSnapshot> snapshots) {
        Map<Integer, String> names = new HashMap<>();
        names.put(MAIN, "Main");
        names.put(ALT, "Alt");
        names.put(BANK_ALT, "Bank alt");
        return PortfolioAggregator.build(selected, liveAccountId, live, snapshots, names, id -> "item " + id);
    }

    private static Map<Integer, AccountPortfolioSnapshot> snapshots(AccountPortfolioSnapshot... snapshots) {
        Map<Integer, AccountPortfolioSnapshot> map = new HashMap<>();
        for (AccountPortfolioSnapshot s : snapshots) {
            map.put(s.accountId, s);
        }
        return map;
    }

    private static PortfolioRow row(PortfolioView view, int itemId) {
        return view.getRows().stream().filter(r -> r.getItemId() == itemId).findFirst().orElseThrow(AssertionError::new);
    }

    private static PortfolioItemCardData card(int itemId, int quantity, long unitBuyPrice, long postTaxSellUnitPrice,
                                              Long unrealizedUnitProfit, int heldMinutes) {
        return new PortfolioItemCardData(itemId, "item " + itemId, 0, 0, 0, 1, postTaxSellUnitPrice, unitBuyPrice,
                unrealizedUnitProfit, heldMinutes, quantity);
    }
}
