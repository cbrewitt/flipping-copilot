package com.flippingcopilot.model;

import com.flippingcopilot.controller.ApiRequestHandler;
import com.flippingcopilot.controller.Persistance;
import com.flippingcopilot.util.MutableReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class TransactionManger {

    // dependencies
    private final FlipManager flipManager;
    private final ScheduledExecutorService executorService;
    private final ApiRequestHandler api;
    private final LoginResponseManager loginResponseManager;
    private final OsrsLoginManager osrsLoginManager;
    private final OkHttpClient okHttpClient;

    // state
    private final Map<String, List<Transaction>> cachedUnAckedTransactions = new HashMap<>();
    private final Map<String, Boolean> transactionSyncScheduled = new HashMap<>();

    public void syncUnAckedTransactions(String displayName) {
        List<Transaction> unAckedTransactions = getUnAckedTransactions(displayName);
        synchronized (this) {
            if(unAckedTransactions.isEmpty()) {
                transactionSyncScheduled.put(displayName, false);
                return;
            }
        }
        // ScheduledExecutorService only has one thread, we don't really want to block it. Need to
        // refactor the API call to be async style but until then just run in okHttpClient's executor
        okHttpClient.dispatcher().executorService().submit(() -> {
            try {
                long s = System.nanoTime();
                List<Transaction> toSend = new ArrayList<>(getUnAckedTransactions(displayName));
                log.debug("sending {} transactions to server", toSend.size());
                List<FlipV2> flips = api.SendTransactions(toSend, displayName);
                for (FlipV2 f : flips) {
                    log.debug("server updated flip for {} closed qty {}, profit {}", f.getItemName(), f.getClosedQuantity(), f.getProfit());
                }
                flipManager.mergeFlips(flips, displayName);
                log.info("sending {} transactions took {}ms", toSend.size(), (System.nanoTime() - s) / 1000_000);
                synchronized (this) {
                    transactionSyncScheduled.put(displayName, false);
                    toSend.forEach(unAckedTransactions::remove);
                    if(!unAckedTransactions.isEmpty()) {
                        scheduleSyncIn(0, displayName);
                    }
                }
            } catch (Exception e) {
                synchronized (this) {
                    transactionSyncScheduled.put(displayName, false);
                }
                String currentDisplayName = osrsLoginManager.getPlayerDisplayName();
                if (loginResponseManager.isLoggedIn() && (currentDisplayName == null || currentDisplayName.equals(displayName))) {
                    log.warn("failed to send transactions to copilot server {}", e.getMessage(), e);
                    scheduleSyncIn(10, displayName);
                }
            }
        });
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
                this.syncUnAckedTransactions(displayName);
            }, seconds, TimeUnit.SECONDS);
        }
    }
}
