package com.flippingcopilot.controller;

import com.flippingcopilot.model.OfferStatus;
import com.flippingcopilot.model.Transaction;
import com.flippingcopilot.util.MutableReference;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.flippingcopilot.util.AtomicReferenceUtils.ifPresent;

@Slf4j
public class TransactionManger {

    // dependencies
    private final AtomicReference<FlipManager> flipManager;
    private final ScheduledExecutorService executorService;
    private final ApiRequestHandler api;

    // state
    private final String displayName;
    private final List<Transaction> unAckedTransactions;
    private boolean transactionSyncScheduled;
    public volatile boolean cancelOngoingSyncSignalled;

    public TransactionManger(AtomicReference<FlipManager> flipManager, ScheduledExecutorService executorService, ApiRequestHandler api, String displayName) {
        this.flipManager = flipManager;
        this.executorService = executorService;
        this.displayName = displayName;
        this.unAckedTransactions = Collections.synchronizedList(Persistance.loadUnAckedTransactions(displayName));
        this.api = api;
        scheduleSyncIn(0);
    }

    public void cancelOngoingSync() {
        this.cancelOngoingSyncSignalled = true;
    }

    public void syncUnAckedTransactions() {
        FlipManager fm = flipManager.get();
        if (fm == null || !fm.flipsLoaded) {
            scheduleSyncIn(1);
            return;
        }
        if(!unAckedTransactions.isEmpty()){
            try {
                long s = System.nanoTime();
                List<Transaction> toSend = new ArrayList<>(unAckedTransactions);
                fm.mergeFlips(api.SendTransactions(toSend, displayName), displayName);
                log.debug("sending transactions took {}ms", (System.nanoTime() - s) / 1000_000);
                toSend.forEach(unAckedTransactions::remove);
            } catch (Exception e) {
                log.warn("failed to send transactions to copilot server {}", e.getMessage(), e);
                scheduleSyncIn(10);
            }
        }
    }

    public long addTransaction(Transaction transaction) {
        unAckedTransactions.add(transaction);
        Persistance.storeUnAckedTransactions(unAckedTransactions, displayName);
        Persistance.storeTransaction(transaction, displayName);
        MutableReference<Long> profit = new MutableReference<>(0L);
        if (OfferStatus.SELL.equals(transaction.getType())) {
            ifPresent(flipManager, i -> profit.setValue(i.estimateTransactionProfit(displayName, transaction)));
        }
        // everything involving network calls to our server we run in the executor service to avoid possible blocking
        executorService.execute(this::syncUnAckedTransactions);
        return profit.getValue();
    }

    private synchronized void scheduleSyncIn(int seconds) {
        if (!transactionSyncScheduled && !cancelOngoingSyncSignalled) {
            log.info("scheduling attempt to re-sync transaction in {}s", seconds);
            executorService.schedule(() ->  {
                transactionSyncScheduled = false;
                this.syncUnAckedTransactions();
            }, seconds, TimeUnit.SECONDS);
            transactionSyncScheduled = true;
        }
    }
}
