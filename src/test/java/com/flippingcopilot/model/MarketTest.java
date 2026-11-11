package com.flippingcopilot.model;

import com.flippingcopilot.rs.MarketRS;
import com.flippingcopilot.rs.OsrsLoginRS;
import net.runelite.api.WorldType;
import org.junit.Test;
import java.util.*;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

public class MarketTest {
    @Test public void hardcoreWorldsSelectSeparateEndpoints() {
        assertEquals(Market.OSRS, Market.fromWorlds(EnumSet.of(WorldType.MEMBERS)));
        assertEquals(Market.HC, Market.fromWorlds(EnumSet.of(WorldType.MEMBERS, WorldType.HARDCORE_MAIN)));
        assertEquals("/v2/prices", Market.OSRS.path("/v2/prices"));
        assertEquals("/v2/osrs-hc/prices", Market.HC.path("/v2/prices"));
        assertEquals("/v3/osrs-hc/suggestion", Market.HC.path("/v3/suggestion"));
    }

    @Test public void conversionInvalidatesOldCallbacksWithoutChangingIdentity() {
        OsrsLoginRS login = new OsrsLoginRS();
        MarketRS markets = new MarketRS(login);
        login.set(OsrsLoginState.builder().loggedIn(true).displayName("Player").accountHash(42L).market(Market.HC).build());
        assertEquals(Market.HC, markets.get());
        BooleanSupplier hcRequest = markets.guard();
        login.set(login.get().toBuilder().market(Market.OSRS).build());
        assertFalse(hcRequest.getAsBoolean());
        assertEquals(Market.OSRS, markets.get());
        assertEquals(Long.valueOf(42), login.get().accountHash);
        login.set(login.get().toBuilder().market(Market.HC).build());
        assertFalse(hcRequest.getAsBoolean()); // returning to HC cannot revive an old request
    }

    @Test public void sameAccountAndFlipIdHaveIndependentProfitInEachMarket() {
        OsrsLoginRS login = new OsrsLoginRS();
        MarketRS markets = new MarketRS(login);
        FlipManager manager = new FlipManager(null, markets);
        manager.setCopilotUserId(7);
        UUID id = UUID.randomUUID();
        manager.mergeFlips(Arrays.asList(flip(id, Market.OSRS, 100), flip(id, Market.HC, 1000)), 7);
        assertEquals(100, manager.calculateStats(0, null).profit);
        login.set(login.get().toBuilder().loggedIn(true).market(Market.HC).build());
        assertEquals(1000, manager.calculateStats(0, null).profit);
        assertEquals(1, manager.getPageFlips(0, 50, 0, null).size());
        manager.deleteAccount(42);
        assertEquals(0, manager.calculateStats(0, null).profit);
        login.set(login.get().toBuilder().market(Market.OSRS).build());
        assertEquals(0, manager.calculateStats(0, null).profit);
    }

    private FlipV2 flip(UUID id, Market market, long profit) {
        FlipV2 flip = new FlipV2();
        flip.setId(id); flip.setMarket(market); flip.setAccountId(42); flip.setItemId(4151);
        flip.setOpenedQuantity(1); flip.setClosedQuantity(1); flip.setSpent(100);
        flip.setReceivedPostTax(100 + profit); flip.setProfit(profit);
        flip.setStatus(FlipStatus.FINISHED); flip.setClosedTime((int)(System.currentTimeMillis()/1000));
        flip.setUpdatedTime(flip.getClosedTime());
        return flip;
    }
}
