package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.manager.CopilotLoginManager;
import com.flippingcopilot.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Named;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

@Slf4j
public class AccountsAggregateFilterSort {

    // dependencies
    private final FlipManager flipManager;
    private final CopilotLoginManager copilotLoginManager;
    private final Consumer<List<AccountAggregate>> aggregatesCallback;
    private final Consumer<Boolean> slowLoadingCallback;
    private final ExecutorService executorService;

    // state
    private int intervalStartTime = 1;
    private int cachedIntervalStartTime = Integer.MIN_VALUE;
    private final List<AccountAggregate> cachedAggregates =  new ArrayList<>();

    public AccountsAggregateFilterSort(FlipManager flipManager,
                                       CopilotLoginManager copilotLoginManager,
                                       Consumer<List<AccountAggregate>> aggregatesCallback,
                                       Consumer<Boolean> slowLoadingCallback,
                                       @Named("copilotExecutor") ExecutorService executorService) {
        this.flipManager = flipManager;
        this.copilotLoginManager = copilotLoginManager;
        this.aggregatesCallback = aggregatesCallback;
        this.slowLoadingCallback = slowLoadingCallback;
        this.executorService = executorService;
    }

    public synchronized void setInterval(IntervalTimeUnit timeUnit, Integer value) {
        switch (timeUnit) {
            case ALL:
                intervalStartTime = 1;
                break;
            case SESSION:
                // TODO: Get session start time from SessionManager
                intervalStartTime = (int) Instant.now().getEpochSecond() - 3600; // Default to 1 hour ago
                break;
            default:
                intervalStartTime = (int) (Instant.now().getEpochSecond() - (long) value * timeUnit.getSeconds());
        }
        reloadAggregates(false);
    }

    public void reloadAggregates(boolean forceReload) {
        executorService.submit(() -> _reloadAggregates(forceReload));
    }

    private synchronized void _reloadAggregates(boolean forceReload) {
        try {
            slowLoadingCallback.accept(true);

            if(forceReload || cachedIntervalStartTime != intervalStartTime) {
                log.debug("loading account aggregates");
                cachedAggregates.clear();
                Aggregator a = new Aggregator();
                copilotLoginManager.accountIDToDisplayNameMap().forEach(
                        (accountId, displayName)  -> a.accounts.put(accountId, new AccountAggregator(accountId))
                );
                flipManager.aggregateFlips(intervalStartTime, null, false, a);
                cachedIntervalStartTime = intervalStartTime;
                a.accounts.forEach((k, v) -> cachedAggregates.add(v.toAccountAggregate(copilotLoginManager.getDisplayName(k))));
                log.debug("loaded {} account aggregates", cachedAggregates.size());
            }
            // Final callback to indicate completion
            slowLoadingCallback.accept(false);
            aggregatesCallback.accept(cachedAggregates);
        } catch (Exception e) {
            log.warn("error loading account aggregates", e);
            slowLoadingCallback.accept(false);
        }
    }

    static class Aggregator implements Consumer<FlipV2> {
        final Map<Integer, AccountAggregator> accounts = new HashMap<>();

        public void accept(FlipV2 flip) {
            {
                AccountAggregator a = accounts.computeIfAbsent(flip.getAccountId(), AccountAggregator::new);
                long profit = flip.getProfit();
                a.totalProfit += profit;
                a.numberOfFlips++;
                if (profit < 0) {
                    a.biggestLoss = Math.min(a.biggestLoss, profit);
                } else {
                    a.biggestWin = Math.max(a.biggestWin, profit);
                }
            }
        }
    }

    @RequiredArgsConstructor
    static class AccountAggregator {
        final int accountId;
        long totalProfit = 0;
        long biggestLoss = Long.MAX_VALUE;
        long biggestWin = Long.MIN_VALUE;
        int numberOfFlips = 0;

        public AccountAggregate toAccountAggregate(String accountName) {
            return AccountAggregate.builder()
                    .accountId(accountId)
                    .accountName(accountName == null ? "Unknown" : accountName)
                    .numberOfFlips(numberOfFlips)
                    .biggestLoss(biggestLoss == Long.MAX_VALUE ? 0 : biggestLoss)
                    .biggestWin(biggestWin == Long.MIN_VALUE ? 0 : biggestWin)
                    .totalProfit(totalProfit)
                    .build();
        }
    }
}