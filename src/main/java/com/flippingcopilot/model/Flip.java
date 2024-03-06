package com.flippingcopilot.model;

import com.flippingcopilot.util.GeTax;
import lombok.Getter;

import java.lang.Math;
import java.time.Instant;


public class Flip {
    @Getter
    private final int itemId;
    private long gpSent;
    private long gpReceived;
    private int itemsReceived;
    private int itemsSent;
    @Getter
    private boolean completed;
    @Getter
    private Instant lastTransactionTimestamp = Instant.now();


    public Flip(int itemId) {
        this.itemId = itemId;
        this.completed = false;
    }

    public long getProfit() {
        long quantity = getQuantityFlipped();
        if (quantity == 0) {
            return 0;
        }
        // use average cost pooling
        long cost = quantity * gpSent / itemsReceived;
        long revenue = quantity * gpReceived / itemsSent;
        return revenue - cost;
    }

    public int getQuantityFlipped() {
        return Math.min(itemsSent, itemsReceived);
    }

    public long add(Transaction transaction) {
        assert transaction.getItemId() == itemId;
        assert !completed;
        lastTransactionTimestamp = transaction.getTimestamp();
        long profit = 0;
        if(transaction.getType() == OfferStatus.BUY) {
            addBuyTransaction(transaction);
        } else {
            long profitBefore = getProfit();
            addSellTransaction(transaction);
            profit = getProfit() - profitBefore;

        }
        if(itemsSent >= itemsReceived) {
            completed = true;
        }
        return profit;
    }

    private void addBuyTransaction(Transaction transaction) {
        gpSent += transaction.getAmountSpent();
        itemsReceived += transaction.getQuantity();
    }

    private void addSellTransaction(Transaction transaction) {
        int price = transaction.getAmountSpent() / transaction.getQuantity();
        int postTaxPrice = GeTax.getPostTaxPrice(itemId, price);
        gpReceived += (long) postTaxPrice * transaction.getQuantity();
        itemsSent += transaction.getQuantity();
    }
}
