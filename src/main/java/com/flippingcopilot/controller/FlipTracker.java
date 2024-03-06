package com.flippingcopilot.controller;

import com.flippingcopilot.model.Flip;
import com.flippingcopilot.model.Transaction;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;

@Getter
public class FlipTracker {
    private final HashMap<Integer, Flip> activeFlips = new HashMap<>();
    private final ArrayList<Flip> completedFlips = new ArrayList<>();

    public long addTransaction(Transaction transaction) {
        int itemId = transaction.getItemId();
        if (!activeFlips.containsKey(itemId)) {
            activeFlips.put(itemId, new Flip(itemId));
        }
        Flip flip = activeFlips.get(itemId);
        long profit = flip.add(transaction);
        if (flip.isCompleted()) {
            archive(flip);
        }
        return profit;
    }

    private void archive(Flip flip) {
        if (flip.getQuantityFlipped() > 0) {
            completedFlips.add(flip);
        }
        activeFlips.remove(flip.getItemId());
    }

    public long getProfit() {
        return getFlips().stream()
                .mapToLong(Flip::getProfit)
                .sum();
    }

    public ArrayList<Flip> getFlips() {
        ArrayList<Flip> allFlips = new ArrayList<>(completedFlips);
        allFlips.addAll(activeFlips.values());
        allFlips.sort((f1, f2) -> Long.compare(f2.getLastTransactionTimestamp().toEpochMilli(),
                f1.getLastTransactionTimestamp().toEpochMilli()));
        allFlips.removeIf(flip -> flip.getProfit() == 0);
        return allFlips;
    }
}
