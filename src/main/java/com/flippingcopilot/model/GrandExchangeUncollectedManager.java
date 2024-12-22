package com.flippingcopilot.model;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemID;

import java.util.*;


@Slf4j
@Singleton
public class GrandExchangeUncollectedManager {

    // dependencies
    private final Client client;

    // state
    private int lastClearedTick = -1;
    private final Map<Integer, Long> lastClearedUncollected = new HashMap<>();
    private final List<Integer> lastClearedSlots = new ArrayList<>();
    // accountId -> [slot -> [itemID -> quantity]]
    private final Map<Long, Map<Integer, Map<Integer, Long>>> uncollected = new HashMap<>();

    @Inject
    public GrandExchangeUncollectedManager(Client client) {
        this.client = client;
    }

    public synchronized boolean HasUncollected(Long accountHash) {
        Map<Integer, Map<Integer, Long>> slotToUncollected = this.uncollected.computeIfAbsent(accountHash, (k) -> new HashMap<>());
        for(Map<Integer, Long> u : slotToUncollected.values()) {
            for(Long v : u.values()) {
                if(v > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    public synchronized Map<Integer, Long> loadAllUncollected(Long accountHash) {
        Map<Integer, Map<Integer, Long>> slotToUncollected = this.uncollected.computeIfAbsent(accountHash, (k) -> new HashMap<>());
        Map<Integer, Long> itemIdToQuantity = new HashMap<>();
        slotToUncollected.values().forEach(itemIdToQty -> itemIdToQty.forEach((k, v) -> itemIdToQuantity.merge(k, v, Long::sum)));
        return itemIdToQuantity;
    }

    public synchronized Map<Integer, Long> loadSlotUncollected(Long accountHash, Integer slot) {
        Map<Integer, Map<Integer, Long>> slotToUncollected = this.uncollected.computeIfAbsent(accountHash, (k) -> new HashMap<>());
        return slotToUncollected.computeIfAbsent(slot, (k) -> new HashMap<>());
    }

    public synchronized void addUncollected(Long accountHash, Integer slot, int itemId, long quantity, long gp) {
        Map<Integer, Map<Integer, Long>> slotToUncollected = this.uncollected.computeIfAbsent(accountHash, (k) -> new HashMap<>());
        Map<Integer, Long> itemIdToQuantity = slotToUncollected.computeIfAbsent(slot, (k) -> new HashMap<>());
        if (!itemIdToQuantity.containsKey(itemId)) {
           // must be a new offer
           itemIdToQuantity.clear();
        }
        if(quantity > 0) {
            log.debug("tick {} added {} of item {} to uncollected", client.getTickCount(), itemId, quantity);
            itemIdToQuantity.merge(itemId, quantity, Long::sum);
        }
        if (gp > 0) {
            log.debug("tick {} added {} gp to uncollected", client.getTickCount(), gp);
            itemIdToQuantity.merge(ItemID.COINS_995, gp, Long::sum);
        }
    }

    public synchronized void clearSlotUncollected(Long accountHash, int slot) {
        Map<Integer, Long> slotUncollected = loadSlotUncollected(accountHash, slot);
        int tick = client.getTickCount();
        if(tick != lastClearedTick) {
            lastClearedUncollected.clear();
            lastClearedSlots.clear();
            lastClearedTick = tick;
        }
        lastClearedSlots.add(slot);
        slotUncollected.forEach((key, value) -> lastClearedUncollected.merge(key, value, Long::sum));
        Map<Integer, Map<Integer, Long>> slotToUncollected = this.uncollected.computeIfAbsent(accountHash, (k) -> new HashMap<>());
        slotToUncollected.remove(slot);
    }

    public synchronized void clearAllUncollected(Long accountHash) {
        log.debug("tick {} clearAllUncollected", client.getTickCount());
        Map<Integer, Long> allUncollected = loadAllUncollected(accountHash);
        int tick = client.getTickCount();
        if(tick != lastClearedTick) {
            lastClearedUncollected.clear();
            lastClearedSlots.clear();
            lastClearedTick = tick;
        }
        lastClearedSlots.addAll(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7));
        allUncollected.forEach((key, value) -> {
            if(value > 0) {
                log.debug("tick {} cleared item {}, qty {}", client.getTickCount(), key, value);
                lastClearedUncollected.merge(key, value, Long::sum);
            }
        });
        uncollected.remove(accountHash);
    }

    public synchronized int getLastClearedTick() {
        return lastClearedTick;
    }

    public synchronized Map<Integer, Long> getLastClearedUncollected() {
        return lastClearedUncollected;
    }

    public synchronized List<Integer> getLastClearedSlots() {
        return lastClearedSlots;
    }

    public void reset() {
        lastClearedUncollected.clear();
        lastClearedTick = -1;
        uncollected.clear();
    }
}
