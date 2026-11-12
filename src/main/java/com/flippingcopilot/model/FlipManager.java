package com.flippingcopilot.model;

import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.rs.MarketRS;
import javax.inject.*;
import java.util.*;

/** Reuses the same aggregation code with one independent store per economy. */
@Singleton
public class FlipManager {
    public static final Comparator<FlipV2> FLIP_STATUS_TIME_COMPARATOR = FlipStore.FLIP_STATUS_TIME_COMPARATOR;
    private final MarketRS markets;
    private final Map<Market, FlipStore> stores = new EnumMap<>(Market.class);
    @Inject public FlipManager(ItemController items, MarketRS markets) {
        this.markets = markets;
        for (Market market : Market.values()) stores.put(market, new FlipStore(items));
    }
    public FlipStore current() { return stores.get(markets.get()); }
    public void setCopilotUserId(int id) { stores.values().forEach(s -> s.setCopilotUserId(id)); }
    public void setFlipsChangedCallback(Runnable callback) {
        stores.values().forEach(s -> s.setFlipsChangedCallback(callback));
        markets.registerListener(m -> callback.run());
    }
    public void reset() { stores.values().forEach(FlipStore::reset); }
    public boolean mergeFlips(List<FlipV2> flips, int userId) {
        boolean accepted = true;
        for (Market market : Market.values()) {
            List<FlipV2> selected = new ArrayList<>();
            for (FlipV2 flip : flips) if (flip.getMarket() == market) selected.add(flip);
            accepted &= stores.get(market).mergeFlips(selected, userId);
        }
        return accepted;
    }
    public void deleteAccount(int account) { stores.values().forEach(s -> s.deleteAccount(account)); }
}
