package copilot.model;

import java.util.function.*;
import copilot.controller.*;
import copilot.rs.*;
import lombok.*;
import lombok.extern.slf4j.*;

import javax.inject.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class Transactions {

    // dependencies
    private final FlipManager flipManager;
    private final ScheduledExecutorService executor;
    private final ApiClient api;
    private final CopilotLogin copilotLogin;
    private final PlayerLogin login;

    // state
    private final ConcurrentMap<String, List<Transaction>> cachedUnAckedTransactions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicBoolean> transactionSyncScheduled = new ConcurrentHashMap<>();

    public void syncUnAckedTransactions(String displayName) {

        long s = System.nanoTime();
        List<Transaction> toSend;
        synchronized (this) {
            toSend = new ArrayList<>(getUnAckedTransactions(displayName));
            if(toSend.isEmpty()) {
                transactionSyncScheduled.get(displayName).set(false);
                return;
            }
        }

        BiConsumer<Integer, List<Flip>> onSuccess = (userId, flips) -> {
            if(!flips.isEmpty()) { copilotLogin.addAccountIfMissing(flips.get(0).accountId, displayName, userId); }
            flipManager.mergeFlips(flips, userId);
            log.info("sending {} transactions took {}ms", toSend.size(), (System.nanoTime() - s) / 1000_000);
            synchronized (this) {
                var unAckedTransactions  = getUnAckedTransactions(displayName);
                transactionSyncScheduled.get(displayName).set(false);
                toSend.forEach(unAckedTransactions::remove);
                if(!unAckedTransactions.isEmpty()) { scheduleSyncIn(0, displayName); }
            }
        };

        Consumer<HttpResponseException> onFailure = (e) -> {
            synchronized (this) { transactionSyncScheduled.get(displayName).set(false); }
            String currentDisplayName = login.getPlayerDisplayName();
            if (copilotLogin.get().isLoggedIn() && (currentDisplayName == null || currentDisplayName.equals(displayName))) {
                log.warn("failed to send transactions to copilot server {}", e.getMessage(), e);
                scheduleSyncIn(10, displayName);
            }
        };
        api.sendTransactionsAsync(toSend, displayName, onSuccess, onFailure);
    }

    public long addTransaction(Transaction transaction, String displayName) {
        if (login.isUnsupportedWorldType()) {
            log.debug("ignoring transaction for {} on unsupported world type(s)", displayName);
            return 0;
        }
        synchronized (this) {
            var unAckedTransactions = getUnAckedTransactions(displayName);
            unAckedTransactions.add(transaction);
            Persistance.storeUnAckedTransactions(unAckedTransactions, displayName);
        }
        long profit = 0L;
        if (OfferStatus.SELL.equals(transaction.type)) {
            Integer accountId = copilotLogin.get().getAccountId(displayName);
            if (accountId != null && accountId != -1) {
                Long p = flipManager.estimateTransactionProfit(accountId, transaction);
                if (p != null) { profit = p; }
            }
        }
        if (copilotLogin.get().isLoggedIn()) { scheduleSyncIn(0, displayName); }
        return profit;
    }

    public List<Transaction> getUnAckedTransactions(String displayName) {
        return cachedUnAckedTransactions.computeIfAbsent(displayName, (k) -> Persistance.loadUnAckedTransactions(displayName));
    }

    public synchronized void scheduleSyncIn(int seconds, String displayName) {
        var scheduled = transactionSyncScheduled.computeIfAbsent(displayName, k -> new AtomicBoolean(false));
        if(scheduled.compareAndSet(false, true)) {
            log.info("scheduling {} attempt to sync {} transactions in {}s", displayName, getUnAckedTransactions(displayName).size(), seconds);
            executor.schedule(() ->  {
                this.syncUnAckedTransactions(displayName);
            }, seconds, TimeUnit.SECONDS);
        } else {
            log.debug("skipping scheduling sync as already scheduled");
        }
    }
}
