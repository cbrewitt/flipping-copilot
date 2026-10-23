package com.flippingcopilot.model;

import javax.inject.Singleton;

/**
 * Tracks sales to the GE tax item sink. The sink buys the item the moment the sell offer is posted, and the
 * client draws the sale from the ge_itemsink_obj/price varps into a slot whose offer stays EMPTY, so it never
 * produces a GrandExchangeOfferChanged event and would otherwise go unrecorded.
 */
@Singleton
public class ItemSinkTracker {

    public enum Change { NONE, SOLD, CLEARED }

    private static final int SLOTS = 8;

    private final int[] lastItemId = new int[SLOTS];
    private final boolean[] known = new boolean[SLOTS];

    /**
     * @param itemId the slot's ge_itemsink_obj varp; an empty slot reads -1
     * @param loginBurst true while the varps are still being restored after a login or hop: a sale already
     *                   in the slot then was made before this session and is only remembered, not reported
     */
    public synchronized Change observe(int slot, int itemId, boolean loginBurst) {
        if (slot < 0 || slot >= SLOTS) {
            return Change.NONE;
        }
        int prev = lastItemId[slot];
        boolean wasKnown = known[slot];
        lastItemId[slot] = itemId;
        known[slot] = true;
        if (!wasKnown || loginBurst || itemId == prev) {
            return Change.NONE;
        }
        if (itemId > 0) {
            return Change.SOLD;
        }
        return prev > 0 ? Change.CLEARED : Change.NONE;
    }

    public synchronized void reset() {
        for (int i = 0; i < SLOTS; i++) {
            known[i] = false;
        }
    }
}
