package copilot.ui.flipsdialog;
import copilot.ui.flipsdialog.FilterSortUtil.FlipTotals;
import static java.util.Comparator.*;
import static java.util.Map.entry;

import java.util.function.*;
import copilot.controller.*;
import copilot.model.*;
import lombok.*;
import lombok.extern.slf4j.*;

import javax.inject.*;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
public class ItemAggregateFilterSort extends PagedFilterSort<ItemAggregate> {

    private static final Map<String, Comparator<ItemAggregate>> SORT_COMPARATORS = new HashMap<>(Map.ofEntries(
        entry("Item", comparing(ItemAggregate::getItemName)),
        entry("Number of flips", comparing(ItemAggregate::getNumberOfFlips)),
        entry("Biggest loss", comparing(ItemAggregate::getBiggestLoss)),
        entry("Biggest win", comparing(ItemAggregate::getBiggestWin)),
        entry("Total profit", comparing(ItemAggregate::getTotalProfit)),
        entry("Avg profit", comparing(ItemAggregate::getAvgProfit)),
        entry("Avg profit ea.", comparing(ItemAggregate::getAvgProfitEa)),
        entry("Total quantity flipped", comparing(ItemAggregate::getTotalQuantityFlipped))
    ));

    // dependencies
    private final FlipManager flipManager;
    private final Items itemController;

    // state
    private final List<ItemAggregate> cachedAggregates = new ArrayList<>();

    public ItemAggregateFilterSort(FlipManager flipManager,
                                   Items itemController,
                                   Consumer<List<ItemAggregate>> aggregatesCallback,
                                   Consumer<Integer> totalPagesChangedCallback,
                                   Consumer<Boolean> slowLoadingCallback,
                                   @Named("copilotExecutor") ExecutorService executor) {
        super("Total profit", SortDirection.ASC, aggregatesCallback, totalPagesChangedCallback, slowLoadingCallback, executor);
        this.flipManager = flipManager; this.itemController = itemController;
    }

    @Override
    protected void reload(boolean totalPagesMaybeChanged) { reloadAggregates(totalPagesMaybeChanged); }

    public void reloadAggregates(boolean totalPagesMaybeChanged) {
        executor.submit(() -> _reloadAggregates(totalPagesMaybeChanged));
    }

    private synchronized void _reloadAggregates(boolean totalPagesMaybeChanged) {
        try {
            slowLoadingCallback.accept(true);

            boolean cachedAggregatesOutOfDate = queryChanged();

            if (cachedAggregatesOutOfDate) {
                rememberQuery();
                cachedAggregates.clear();
                var flipFilter = itemFilter();
                var a = new Aggregator(flipFilter);
                flipManager.aggregateFlips(intervalStartTime, cachedAccountId, false, a);
                cachedIntervalStartTime = intervalStartTime;
                a.items.forEach((k, v) -> cachedAggregates.add(v.item(itemController.getItemName(k))));
                log.debug("loaded {} cached item aggregates", cachedAggregates.size());
            }

            publishPage(cachedAggregates, SORT_COMPARATORS, totalPagesMaybeChanged, cachedAggregatesOutOfDate);

        } catch (Exception e) {
            log.warn("error filtering/sorting item aggregates", e);
        }
    }

    @AllArgsConstructor
    static class Aggregator implements Consumer<Flip> {
        final Predicate<Flip> p;
        final Map<Integer, FlipTotals> items = new HashMap<>();

        public void accept(Flip flip) {
            if (p.test(flip)) { items.computeIfAbsent(flip.itemId, id -> new FlipTotals()).accept(flip); }
        }
    }
}
