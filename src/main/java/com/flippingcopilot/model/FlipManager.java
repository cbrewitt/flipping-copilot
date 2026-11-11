package com.flippingcopilot.model;

import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.rs.MarketRS;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.function.Consumer;

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
    private FlipStore history() { return stores.get(markets.get()); }
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
    public Long estimateTransactionProfit(Integer account, Transaction tx) { return stores.get(markets.get()).estimateTransactionProfit(account, tx); }
    public Integer getIntervalAccount() { return history().getIntervalAccount(); }
    public Stats getIntervalStats() { return history().getIntervalStats(); }
    public Stats calculateStats(int start, Integer account) { return history().calculateStats(start, account); }
    public void setIntervalAccount(Integer account) { history().setIntervalAccount(account); }
    public void setIntervalStartTime(int start) { history().setIntervalStartTime(start); }
    public List<FlipV2> getPageFlips(int page, int size) { return history().getPageFlips(page, size); }
    public List<FlipV2> getPageFlips(int page, int size, int start, Integer account) { return history().getPageFlips(page, size, start, account); }
    public void aggregateFlips(int start, Integer account, boolean buying, Consumer<FlipV2> consumer) { history().aggregateFlips(start, account, buying, consumer); }
    public boolean isGhostFlip(int account, UUID id) { return history().isGhostFlip(account, id); }
    public List<FlipV2> getMissedFlipsForAccount(Integer account) { return history().getMissedFlipsForAccount(account); }
    public void deleteAccount(int account) { stores.values().forEach(s -> s.deleteAccount(account)); }
}
