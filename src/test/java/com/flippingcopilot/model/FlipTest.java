package com.flippingcopilot.model;

import org.junit.Test;

public class FlipTest {

    @Test
    public void testAddBuyTransaction() {
        Flip flip = new Flip(560);
        Transaction transaction = new Transaction(OfferStatus.BUY, 560, 100, 10, 0, 1000, null);
        flip.add(transaction);
        assert flip.getProfit() == 0;
        assert !flip.isCompleted();
    }

    @Test
    public void testAddSellTransaction() {
        Flip flip = new Flip(560);
        Transaction transaction = new Transaction(OfferStatus.SELL, 560, 100, 10, 0, 1000, null);
        flip.add(transaction);
        assert flip.getProfit() == 0;
        assert flip.isCompleted();
    }

    @Test
    public void testSimpleFlip() {
        Flip flip = new Flip(560);
        Transaction transaction1 = new Transaction(OfferStatus.BUY, 560, 100, 10, 0, 1000, null);
        Transaction transaction2 = new Transaction(OfferStatus.SELL, 560, 110, 10, 0, 1100, null);
        flip.add(transaction1);
        flip.add(transaction2);
        assert flip.getProfit() == 90;
        assert flip.isCompleted();
    }

    @Test
    public void partialFlip() {
        Flip flip = new Flip(560);
        Transaction transaction1 = new Transaction(OfferStatus.BUY, 560, 100, 10, 0, 1000, null);
        Transaction transaction2 = new Transaction(OfferStatus.SELL, 560, 110, 5, 0, 550, null);
        flip.add(transaction1);
        flip.add(transaction2);
        assert flip.getProfit() == 45;
        assert !flip.isCompleted();
    }

    @Test
    public void testSoldTooMany() {
        Flip flip = new Flip(560);
        Transaction transaction1 = new Transaction(OfferStatus.BUY, 560, 100, 10, 0, 1000, null);
        Transaction transaction2 = new Transaction(OfferStatus.SELL, 560, 110, 20, 0, 2200, null);
        flip.add(transaction1);
        flip.add(transaction2);
        assert flip.getProfit() == 90;
        assert flip.isCompleted();
    }

}
