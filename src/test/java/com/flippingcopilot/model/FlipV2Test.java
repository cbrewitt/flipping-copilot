package com.flippingcopilot.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FlipV2Test {

    @Test
    public void calculateProfitHandlesLargeSyntheticTotals() {
        FlipV2 flip = new FlipV2();
        flip.setOpenedQuantity(50_000);
        flip.setClosedQuantity(0);
        flip.setSpent(4_500_000_000L);

        Transaction transaction = new Transaction();
        transaction.setItemId(13190);
        transaction.setPrice(100_000);
        transaction.setQuantity(50_000);
        transaction.setAmountSpent((long) transaction.getPrice() * transaction.getQuantity());

        assertEquals(500_000_000L, flip.calculateProfit(transaction));
    }
}
