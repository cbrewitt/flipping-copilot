package com.flippingcopilot.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ItemSinkTrackerTest {
    private static final int EMPTY = -1;
    private static final int TORTURE = 19553;

    @Test
    public void saleAfterTheFirstObservationIsReported() {
        ItemSinkTracker t = new ItemSinkTracker();
        assertEquals(ItemSinkTracker.Change.NONE, t.observe(0, EMPTY, false));
        assertEquals(ItemSinkTracker.Change.SOLD, t.observe(0, TORTURE, false));
        assertEquals(ItemSinkTracker.Change.NONE, t.observe(0, TORTURE, false));
        assertEquals(ItemSinkTracker.Change.CLEARED, t.observe(0, EMPTY, false));
    }

    @Test
    public void saleAlreadyPresentAtLoginIsNotReported() {
        ItemSinkTracker t = new ItemSinkTracker();
        assertEquals(ItemSinkTracker.Change.NONE, t.observe(3, TORTURE, false));
        assertEquals(ItemSinkTracker.Change.NONE, t.observe(3, TORTURE, false));
    }

    @Test
    public void varpsRestoredDuringTheLoginBurstAreNotReported() {
        ItemSinkTracker t = new ItemSinkTracker();
        t.observe(2, EMPTY, true);
        assertEquals(ItemSinkTracker.Change.NONE, t.observe(2, TORTURE, true));
        assertEquals(ItemSinkTracker.Change.NONE, t.observe(2, TORTURE, false));
    }

    @Test
    public void resetForgetsEverySlot() {
        ItemSinkTracker t = new ItemSinkTracker();
        t.observe(1, EMPTY, false);
        t.reset();
        assertEquals(ItemSinkTracker.Change.NONE, t.observe(1, TORTURE, false));
    }

    @Test
    public void slotsAreIndependent() {
        ItemSinkTracker t = new ItemSinkTracker();
        t.observe(0, EMPTY, false);
        t.observe(1, EMPTY, false);
        assertEquals(ItemSinkTracker.Change.SOLD, t.observe(1, TORTURE, false));
        assertEquals(ItemSinkTracker.Change.NONE, t.observe(0, EMPTY, false));
    }

    @Test
    public void outOfRangeSlotIsIgnored() {
        ItemSinkTracker t = new ItemSinkTracker();
        assertEquals(ItemSinkTracker.Change.NONE, t.observe(8, TORTURE, false));
    }
}
