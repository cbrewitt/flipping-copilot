package copilot.rs;

import copilot.controller.Items;
import copilot.model.*;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.*;

public class PortfolioSummaryTest {
    @Test
    public void absentPortfolioKeepsCashAndBuyOffersInMarketValue() {
        // Item lookup initialization is unrelated to cash accounting; do not schedule it.
        var scheduler = (ScheduledExecutorService) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{ScheduledExecutorService.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("scheduleAtFixedRate")) return null;
                    throw new AssertionError("Unexpected scheduling: " + method);
                });
        var items = new Items(null, null, null, null, scheduler);
        var portfolio = new PortfolioStateRS(new GameLogin(), items, null, null, null);
        var offers = new StatusOfferList();
        offers.set(0, new Offer(OfferStatus.BUY, 560, 100, 10, 300, 3, 0, true, false));
        offers.set(1, new Offer(OfferStatus.SELL, 560, 100, 20, 0, 0, 1, true, false));
        offers.set(2, null);
        var state = portfolio.buildPortfolioState(
                Map.of(995, 50, 13204, 2), Map.of(995, 70, 13204, 3), null,
                offers, Map.of(995, 80L, 560, 999L));
        assertTrue(state.loaded);
        assertTrue(state.itemCardDataByItemId.isEmpty());
        assertEquals(new PortfolioSummary(6200, 0, 5200, 0, 1000), state.summaryData);

        var empty = portfolio.buildPortfolioState(null, null, null, null, null);
        assertEquals(new PortfolioSummary(0, 0, 0, 0, 0), empty.summaryData);
        var negativeCoins = portfolio.buildPortfolioState(null, null, null, null, Map.of(995, -5L));
        assertEquals(empty, negativeCoins);
        assertEquals(empty, portfolio.buildPortfolioState(null, null, Collections.emptyList(), null, null));
    }
}
