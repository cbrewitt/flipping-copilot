package com.flippingcopilot.controller;

import com.flippingcopilot.model.OfferStatus;
import com.flippingcopilot.model.Transaction;
import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertEquals;

public class FlipTrackerTest {

    @Test
    public void addTransaction_newFlip_addsToActiveFlips() {
        FlipTracker flipTracker = new FlipTracker();
        Transaction transaction = new Transaction(OfferStatus.BUY, 1, 1000, 10, 0, 10000, Instant.now());
        flipTracker.addTransaction(transaction);

        assertEquals(1, flipTracker.getActiveFlips().size());
    }

    @Test
    public void addTransaction_completedFlip_movesToCompletedFlips() {
        FlipTracker flipTracker = new FlipTracker();
        Transaction buyTransaction = new Transaction(OfferStatus.BUY, 1, 1000, 10, 0, 10000, Instant.now());
        Transaction sellTransaction = new Transaction(OfferStatus.SELL, 1, 2000, 10, 0, 20000, Instant.now());
        flipTracker.addTransaction(buyTransaction);
        flipTracker.addTransaction(sellTransaction);

        assertEquals(0, flipTracker.getActiveFlips().size());
        assertEquals(1, flipTracker.getCompletedFlips().size());
    }

    @Test
    public void getProfit_noFlips_returnsZero() {
        FlipTracker flipTracker = new FlipTracker();
        assertEquals(0, flipTracker.getProfit());
    }

    @Test
    public void getProfit_withCompletedFlips_returnsCorrectProfit() {
        FlipTracker flipTracker = new FlipTracker();
        Transaction buyTransaction = new Transaction(OfferStatus.BUY, 1, 1000, 10, 0, 10000, Instant.now());
        Transaction sellTransaction = new Transaction(OfferStatus.SELL, 1, 2000, 10, 0, 20000, Instant.now());
        flipTracker.addTransaction(buyTransaction);
        flipTracker.addTransaction(sellTransaction);

        assertEquals(9800, flipTracker.getProfit());
    }
}
