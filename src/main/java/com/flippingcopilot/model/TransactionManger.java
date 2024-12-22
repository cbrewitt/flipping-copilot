package com.flippingcopilot.model;

import com.flippingcopilot.controller.ApiRequestHandler;
import com.flippingcopilot.controller.Persistance;
import com.flippingcopilot.util.MutableReference;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
public class TransactionManger {

    // dependencies
    private final FlipManager flipManager;
    private final ScheduledExecutorService executorService;
    private final ApiRequestHandler api;
    private final LoginResponseManager loginResponseManager;
    private final OsrsLoginManager osrsLoginManager;

    // state
    private final Map<String, List<Transaction>> cachedUnAckedTransactions;
    private final Map<String, Boolean> transactionSyncScheduled = new HashMap<>();

    @Inject
    public TransactionManger(FlipManager flipManager, ScheduledExecutorService executorService, ApiRequestHandler api, LoginResponseManager loginResponseManager, OsrsLoginManager osrsLoginManager) {
        this.flipManager = flipManager;
        this.executorService = executorService;
        this.loginResponseManager = loginResponseManager;
        this.osrsLoginManager = osrsLoginManager;
        this.cachedUnAckedTransactions = new HashMap<>();
        this.api = api;
    }

    public void syncUnAckedTransactions(String displayName) {
        List<Transaction> unAckedTransactions = getUnAckedTransactions(displayName);
        if(!unAckedTransactions.isEmpty()){
            try {
                long s = System.nanoTime();
                List<Transaction> toSend = new ArrayList<>(getUnAckedTransactions(displayName));
                log.debug("sending {} transactions to server", toSend.size());
                List<FlipV2> flips = api.SendTransactions(toSend, displayName);
                for(FlipV2 f : flips) {
                    log.debug("server updated flip for {} closed qty {}, profit {}", f.getItemName(), f.getClosedQuantity(), f.getProfit());
                }
                flipManager.mergeFlips(flips, displayName);
                log.info("sending {} transactions took {}ms", toSend.size(), (System.nanoTime() - s) / 1000_000);
                toSend.forEach(unAckedTransactions::remove);
            } catch (Exception e) {
                String currentDisplayName = osrsLoginManager.getPlayerDisplayName();
                if(loginResponseManager.isLoggedIn() && (currentDisplayName == null || currentDisplayName.equals(displayName))) {
                    log.warn("failed to send transactions to copilot server {}", e.getMessage(), e);
                    scheduleSyncIn(10, displayName);
                }
            }
        }
    }

    public long addTransaction(Transaction transaction, String displayName) {
        List<Transaction> unAckedTransactions  = getUnAckedTransactions(displayName);
        unAckedTransactions.add(transaction);
        Persistance.storeUnAckedTransactions(unAckedTransactions, displayName);
        Persistance.storeTransaction(transaction, displayName);
        MutableReference<Long> profit = new MutableReference<>(0L);
        if (OfferStatus.SELL.equals(transaction.getType())) {
            profit.setValue(flipManager.estimateTransactionProfit(displayName, transaction));
        }
        scheduleSyncIn(0, displayName);
        return profit.getValue();
    }

    private List<Transaction> getUnAckedTransactions(String displayName) {
        return cachedUnAckedTransactions.computeIfAbsent(displayName, (k) -> Collections.synchronizedList(Persistance.loadUnAckedTransactions(displayName)));
    }

    public synchronized void scheduleSyncIn(int seconds, String displayName) {
        if (!transactionSyncScheduled.computeIfAbsent(displayName, (k) -> false)) {
            log.info("scheduling attempt to sync {} transactions in {}s", displayName, seconds);
            transactionSyncScheduled.put(displayName, true);
            executorService.schedule(() ->  {
                transactionSyncScheduled.put(displayName, false);
                this.syncUnAckedTransactions(displayName);
            }, seconds, TimeUnit.SECONDS);
        }
    }
}
