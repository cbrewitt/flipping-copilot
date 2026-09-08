package copilot.ui.flipsdialog;
import copilot.ui.flipsdialog.FilterSortUtil.FlipTotals;

import copilot.model.*;
import copilot.rs.*;
import lombok.*;
import lombok.extern.slf4j.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

@Slf4j
@RequiredArgsConstructor
public class AccountsAggregateFilterSort {

    // dependencies
    private final FlipManager flips;
    private final CopilotLogin copilotLogin;
    private final Consumer<List<AccountAggregate>> aggregatesCallback;
    private final Consumer<Boolean> slowLoadingCallback;
    private final ExecutorService executor;

    // state
    private int intervalStartTime = 1, cachedIntervalStartTime = Integer.MIN_VALUE;
    private final List<AccountAggregate> cachedAggregates =  new ArrayList<>();

    public synchronized void setInterval(IntervalTimeUnit timeUnit, Integer value) {
        intervalStartTime = FilterSortUtil.intervalStart(timeUnit, value);
        reloadAggregates(false);
    }

    public void reloadAggregates(boolean forceReload) { executor.submit(() -> _reloadAggregates(forceReload)); }

    private synchronized void _reloadAggregates(boolean forceReload) {
        try {
            slowLoadingCallback.accept(true);

            if(forceReload || cachedIntervalStartTime != intervalStartTime) {
                cachedAggregates.clear();
                var a = new Aggregator();
                copilotLogin.get().accountIdToDisplayName.forEach(
                        (accountId, displayName)  -> a.accounts.put(accountId, new FlipTotals())
                );
                flips.aggregateFlips(intervalStartTime, null, false, a);
                cachedIntervalStartTime = intervalStartTime;
                a.accounts.forEach((k, v) -> cachedAggregates.add(v.account(k, copilotLogin.get().getDisplayName(k))));
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

    static class Aggregator implements Consumer<Flip> {
        final Map<Integer, FlipTotals> accounts = new HashMap<>();

        public void accept(Flip flip) { accounts.computeIfAbsent(flip.accountId, id -> new FlipTotals()).accept(flip); }
    }
}
