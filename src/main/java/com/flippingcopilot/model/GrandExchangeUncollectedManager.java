package com.flippingcopilot.model;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.ItemID;

import java.util.HashMap;
import java.util.Map;


@Singleton
public class GrandExchangeUncollectedManager {

    // dependencies
    private final Client client;

    // state
    @Getter
    private int lastClearedTick = -1;
    @Getter
    private final Map<Integer, Long> lastClearedUncollected = new HashMap<>();
    // accountId -> [slot -> [itemID -> quantity]]
    private final Map<Long, Map<Integer, Map<Integer, Long>>> uncollected = new HashMap<>();

    @Inject
    public GrandExchangeUncollectedManager(Client client) {
        this.client = client;
    }

    public synchronized boolean HasUncollected(Long accountHash) {
        for(Map<Integer, Long> u : this.uncollected.computeIfAbsent(accountHash, (k) -> new HashMap<>()).values()) {
            for(Long v : u.values()) {
                if(v > 0) {
                    return true;
                }
            }
        }
        return true;
    }

    public synchronized Map<Integer, Long> loadAllUncollected(Long accountHash) {
        Map<Integer, Map<Integer, Long>> slotToUncollected = this.uncollected.computeIfAbsent(accountHash, (k) -> new HashMap<>());
        Map<Integer, Long> itemIdToQuantity = new HashMap<>();
        slotToUncollected.values().forEach(itemIdToQty -> itemIdToQty.forEach((k, v) -> itemIdToQty.merge(k, v, Long::sum)));
        return itemIdToQuantity;
    }

    public synchronized Map<Integer, Long> loadSlotUncollected(Long accountHash, Integer slot) {
        Map<Integer, Map<Integer, Long>> slotToUncollected = this.uncollected.computeIfAbsent(accountHash, (k) -> new HashMap<>());
        return slotToUncollected.computeIfAbsent(slot, (k) -> new HashMap<>());
    }

    public synchronized void addUncollected(Long accountHash, Integer slot, int itemId, long quantity, int gp) {
        Map<Integer, Map<Integer, Long>> slotToUncollected = this.uncollected.computeIfAbsent(accountHash, (k) -> new HashMap<>());
        Map<Integer, Long> itemIdToQuantity = slotToUncollected.computeIfAbsent(slot, (k) -> new HashMap<>());
        if (!itemIdToQuantity.containsKey(itemId)) {
           // must be a new offer
           itemIdToQuantity.clear();
        }
        itemIdToQuantity.merge(itemId, quantity, Long::sum);
        itemIdToQuantity.merge(ItemID.COINS_995, quantity, Long::sum);
    }

    public synchronized void clearSlotUncollected(Long accountHash, int slot) {
        Map<Integer, Long> slotUncollected = loadSlotUncollected(accountHash, slot);
        int tick = client.getTickCount();
        if(tick != lastClearedTick) {
            lastClearedUncollected.clear();
            lastClearedTick = tick;
        }
        slotUncollected.forEach((key, value) -> lastClearedUncollected.merge(key, value, Long::sum));
        Map<Integer, Map<Integer, Long>> slotToUncollected = this.uncollected.computeIfAbsent(accountHash, (k) -> new HashMap<>());
        slotToUncollected.remove(slot);
    }

    public synchronized void clearAllUncollected(Long accountHash) {
        Map<Integer, Long> allUncollected = loadAllUncollected(accountHash);
        int tick = client.getTickCount();
        if(tick != lastClearedTick) {
            lastClearedUncollected.clear();
            lastClearedTick = tick;
        }
        allUncollected.forEach((key, value) -> lastClearedUncollected.merge(key, value, Long::sum));
        uncollected.remove(accountHash);
    }

    public void reset() {
        lastClearedUncollected.clear();
        lastClearedTick = -1;
        uncollected.clear();
    }
}
