package com.flippingcopilot.model;

import com.flippingcopilot.controller.*;
import com.flippingcopilot.rs.CopilotLoginRS;
import com.flippingcopilot.util.MutableReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.inject.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

import static com.flippingcopilot.util.FormatUtil.formatDurationSince;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class TransactionManager {

    private final Persistance persistance;

    // dependencies
    private final FlipManager flipManager;
    private final ScheduledExecutorService executorService;
    private final ApiRequestHandler api;
    private final CopilotLoginRS copilotLoginRS;
    private final OsrsLoginManager osrsLoginManager;

    // state
    private final ConcurrentMap<String, List<Transaction>> cachedUnAckedTransactions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicBoolean> transactionSyncScheduled = new ConcurrentHashMap<>();

    public void syncUnAckedTransactions(String displayName, Market market) {

        long s = System.nanoTime();
        List<Transaction> toSend;
        synchronized (this) {
            toSend = new ArrayList<>(getUnAckedTransactions(displayName, market));
            if(toSend.isEmpty()) {
                transactionSyncScheduled.get(market.storageKey(displayName)).set(false);
                return;
            }
        }

        BiConsumer<Integer, List<FlipV2>> onSuccess = (userId, flips) -> {
            if(!flips.isEmpty()) {
                copilotLoginRS.addAccountIfMissing(flips.get(0).getAccountId(), displayName, userId, market);
            }
            flipManager.mergeFlips(flips, userId);
            log.info("sending {} transactions took {}", toSend.size(), formatDurationSince(s));
            synchronized (this) {
                List<Transaction> unAckedTransactions  = getUnAckedTransactions(displayName, market);
                transactionSyncScheduled.get(market.storageKey(displayName)).set(false);
                toSend.forEach(unAckedTransactions::remove);
                persistance.storeUnAckedTransactions(unAckedTransactions, displayName, market);
                if(!unAckedTransactions.isEmpty()) {
                    scheduleSyncIn(0, displayName, market);
                }
            }
        };

        Consumer<HttpResponseException> onFailure = (e) -> {
            synchronized (this) {
                transactionSyncScheduled.get(market.storageKey(displayName)).set(false);
            }
            String currentDisplayName = osrsLoginManager.getPlayerDisplayName();
            if (copilotLoginRS.get().isLoggedIn() && (currentDisplayName == null || currentDisplayName.equals(displayName))) {
                log.warn("failed to send transactions to copilot server {}", e.getMessage(), e);
                scheduleSyncIn(10, displayName, market);
            }
        };
        api.sendTransactionsAsync(toSend, displayName, market, onSuccess, onFailure);
    }

    public long addTransaction(Transaction transaction, String displayName) {
        Market market = transaction.getMarket() == null ? osrsLoginManager.getMarket() : transaction.getMarket();
        if (osrsLoginManager.isUnsupportedWorldType()) {
            log.debug("ignoring transaction for {} on unsupported world type(s)", displayName);
            return 0;
        }
        synchronized (this) {
            List<Transaction> unAckedTransactions = getUnAckedTransactions(displayName, market);
            unAckedTransactions.add(transaction);
            persistance.storeUnAckedTransactions(unAckedTransactions, displayName, market);
        }
        MutableReference<Long> profit = new MutableReference<>(0L);
        if (market == osrsLoginManager.getMarket() && OfferStatus.SELL.equals(transaction.getType())) {
            Integer accountId = copilotLoginRS.get().getAccountId(displayName);
            if (accountId != null && accountId != -1) {
                Long p = flipManager.current().estimateTransactionProfit(accountId, transaction);
                if (p != null) {
                    profit.setValue(p);
                }
            }
        }
        if (copilotLoginRS.get().isLoggedIn()) {
            scheduleSyncIn(0, displayName, market);
        }
        return profit.getValue();
    }

    public List<Transaction> getUnAckedTransactions(String displayName) {
        return getUnAckedTransactions(displayName, osrsLoginManager.getMarket());
    }

    private List<Transaction> getUnAckedTransactions(String displayName, Market market) {
        return cachedUnAckedTransactions.computeIfAbsent(market.storageKey(displayName), (k) -> persistance.loadUnAckedTransactions(displayName, market));
    }

    public void scheduleSyncIn(int seconds, String displayName) {
        for (Market market : Market.values()) scheduleSyncIn(seconds, displayName, market);
    }

    private synchronized void scheduleSyncIn(int seconds, String displayName, Market market) {
        AtomicBoolean scheduled = transactionSyncScheduled.computeIfAbsent(market.storageKey(displayName), k -> new AtomicBoolean(false));
        if(scheduled.compareAndSet(false, true)) {
            log.info("scheduling {} attempt to sync {} transactions in {}s", displayName, getUnAckedTransactions(displayName, market).size(), seconds);
            executorService.schedule(() ->  {
                this.syncUnAckedTransactions(displayName, market);
            }, seconds, TimeUnit.SECONDS);
        } else {
            log.debug("skipping scheduling sync as already scheduled");
        }
    }
}
