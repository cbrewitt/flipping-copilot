package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.model.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Named;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Predicate;

@Slf4j
public class ItemAggregateFilterSort {

    public static final int DEFAULT_PAGE_SIZE = 50;

    private static final Map<String, Comparator<ItemAggregate>> SORT_COMPARATORS = new HashMap<>();
    static {
        SORT_COMPARATORS.put("Item", Comparator.comparing(ItemAggregate::getItemName));
        SORT_COMPARATORS.put("Number of flips", Comparator.comparing(ItemAggregate::getNumberOfFlips));
        SORT_COMPARATORS.put("Biggest loss", Comparator.comparing(ItemAggregate::getBiggestLoss));
        SORT_COMPARATORS.put("Biggest win", Comparator.comparing(ItemAggregate::getBiggestWin));
        SORT_COMPARATORS.put("Total profit", Comparator.comparing(ItemAggregate::getTotalProfit));
        SORT_COMPARATORS.put("Avg profit", Comparator.comparing(ItemAggregate::getAvgProfit));
        SORT_COMPARATORS.put("Avg profit ea.", Comparator.comparing(ItemAggregate::getAvgProfitEa));
        SORT_COMPARATORS.put("Total quantity flipped", Comparator.comparing(ItemAggregate::getTotalQuantityFlipped));
    }

    // dependencies
    private final FlipManager flipManager;
    private final ItemController itemController;
    private final Consumer<List<ItemAggregate>> aggregatesCallback;
    private final Consumer<Integer> totalPagesChangedCallback;
    private final Consumer<Boolean> slowLoadingCallback;
    private final ExecutorService executorService;

    // state
    private List<ItemAggregate> cachedAggregates = new ArrayList<>();
    private Integer cachedAccountId = null;
    private int cachedIntervalStartTime = Integer.MIN_VALUE;
    private Set<Integer> cachedFilteredItems = new HashSet<>();
    private SortDirection cachedSortDirection = SortDirection.ASC;
    private String cachedSortColumn = "";

    private int intervalStartTime = 1;
    private Integer accountId = null;
    @Getter
    private String sortColumn = "Total profit";
    @Getter
    private SortDirection sortDirection = SortDirection.ASC;
    private Set<Integer> filteredItems = new HashSet<>();
    @Getter
    private int pageSize = DEFAULT_PAGE_SIZE;
    private int page = 1;

    public ItemAggregateFilterSort(FlipManager flipManager,
                                   ItemController itemController,
                                   Consumer<List<ItemAggregate>> aggregatesCallback,
                                   Consumer<Integer> totalPagesChangedCallback,
                                   Consumer<Boolean> slowLoadingCallback,
                                   @Named("copilotExecutor") ExecutorService executorService) {
        this.flipManager = flipManager;
        this.itemController = itemController;
        this.aggregatesCallback = aggregatesCallback;
        this.totalPagesChangedCallback = totalPagesChangedCallback;
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
        reloadAggregates(true);
    }

    public synchronized void setAccountId(Integer accountId) {
        if (!Objects.equals(accountId, this.accountId)) {
            this.accountId = accountId;
            reloadAggregates(true);
        }
    }

    public synchronized void setFilteredItems(Set<Integer> filteredItems) {
        if (!Objects.equals(filteredItems, this.filteredItems)) {
            this.filteredItems = filteredItems;
            reloadAggregates(true);
        }
    }
    public synchronized Set<Integer> getFilteredItems() {
        return new HashSet<>(filteredItems);
    }

    public synchronized void setPageSize(int newSize) {
        if (newSize != pageSize) {
            pageSize = newSize;
            reloadAggregates(true);
        }
    }

    public synchronized void setSortColumn(String sortColumn) {
        if (!sortColumn.equals(this.sortColumn)) {
            this.sortColumn = sortColumn;
            reloadAggregates(false);
        }
    }

    public synchronized void setSortDirection(SortDirection sortDirection) {
        if (!Objects.equals(sortDirection, this.sortDirection)) {
            this.sortDirection = sortDirection;
            reloadAggregates(false);
        }
    }

    public synchronized void setPage(int page) {
        if (page != this.page) {
            this.page = page;
            reloadAggregates(false);
        }
    }

    public void reloadAggregates(boolean totalPagesMaybeChanged) {
        executorService.submit(() -> _reloadAggregates(totalPagesMaybeChanged));
    }


    private synchronized void _reloadAggregates(boolean totalPagesMaybeChanged) {
        try {
            slowLoadingCallback.accept(true);

            boolean cachedAggregatesOutOfDate = !Objects.equals(cachedAccountId, accountId) ||
                    !cachedFilteredItems.equals(filteredItems) ||
                    cachedIntervalStartTime != intervalStartTime;
            boolean cachedSortOutOfDate = cachedAggregatesOutOfDate ||
                    !cachedSortColumn.equals(sortColumn) ||
                    !cachedSortDirection.equals(sortDirection);

            log.debug("cachedAggregatesOutOfDate={}, cachedSortOutOfDate={}", cachedAggregatesOutOfDate, cachedSortOutOfDate);

            if (cachedAggregatesOutOfDate) {
                log.debug("reloading cached item aggregates");
                cachedAccountId = accountId;
                cachedFilteredItems.clear();
                cachedFilteredItems.addAll(filteredItems);
                cachedIntervalStartTime = intervalStartTime;
                cachedAggregates.clear();
                Predicate<FlipV2> flipFilter = filteredItems.isEmpty() ? f -> true : f -> filteredItems.contains(f.getItemId());
                Aggregator a = new Aggregator(flipFilter);
                flipManager.aggregateFlips(intervalStartTime, cachedAccountId, false, a);
                cachedIntervalStartTime = intervalStartTime;
                a.items.forEach((k, v) -> cachedAggregates.add(v.toItemAggregate(itemController.getItemName(k))));
                log.debug("loaded {} cached item aggregates", cachedAggregates.size());
            }

            if (totalPagesMaybeChanged) {
                log.debug("updating total pages");
                int totalPages = 1 + cachedAggregates.size() / pageSize;
                totalPagesChangedCallback.accept(totalPages);
                log.debug("updated total pages to {}", totalPages);
            }

            if (cachedSortOutOfDate) {
                log.debug("re-sorting cached item aggregates");
                cachedSortColumn = sortColumn;
                cachedSortDirection = sortDirection;

                // Apply sorting
                Comparator<ItemAggregate> comparator = SORT_COMPARATORS.get(sortColumn);
                if (comparator != null) {
                    if (sortDirection == SortDirection.ASC) {
                        comparator = comparator.reversed();
                    }
                    cachedAggregates.sort(comparator);
                }
                log.debug("re-sorted item aggregates");
            }

            int startIndex = (page - 1) * pageSize;
            int endIndex = Math.min(startIndex + pageSize, cachedAggregates.size());
            slowLoadingCallback.accept(false);
            aggregatesCallback.accept(cachedAggregates.subList(startIndex, endIndex));
            log.debug("_reloadAggregates end");

        } catch (Exception e) {
            log.warn("error filtering/sorting item aggregates", e);
        }
    }

    @AllArgsConstructor
    static class Aggregator implements Consumer<FlipV2> {
        final Predicate<FlipV2> p;
        final Map<Integer,  ItemAggregator> items = new HashMap<>();

        public void accept(FlipV2 flip) {
            if(p.test(flip)) {
                ItemAggregator i = items.computeIfAbsent(flip.getItemId(), (k) -> new ItemAggregator());
                long profit = flip.getProfit();
                long quantitySold = flip.getClosedQuantity();
                i.totalProfit += profit;
                i.totalQuantitySold += quantitySold;
                i.numberOfFlips++;
                if (profit < 0) {
                    i.biggestLoss = Math.min(i.biggestLoss, profit);
                } else {
                    i.biggestWin = Math.max(i.biggestWin, profit);
                }
                i.quantityFlipped += flip.getClosedQuantity();
            }
        }
    }

    static class ItemAggregator {
        private long totalProfit = 0;
        private long totalQuantitySold = 0;
        private long biggestLoss = Long.MAX_VALUE;
        private long biggestWin = Long.MIN_VALUE;
        private int numberOfFlips = 0;
        private int quantityFlipped = 0;


        public ItemAggregate toItemAggregate(String itemName) {
            return ItemAggregate.builder()
                    .itemName(itemName)
                    .numberOfFlips(numberOfFlips)
                    .totalQuantityFlipped(quantityFlipped)
                    .biggestLoss(biggestLoss == Long.MAX_VALUE ? 0 : biggestLoss)
                    .biggestWin(biggestWin == Long.MIN_VALUE ? 0 : biggestWin)
                    .totalProfit(totalProfit)
                    .avgProfit(numberOfFlips == 0 ? 0 : totalProfit / numberOfFlips)
                    .avgProfitEa(totalQuantitySold == 0 ? 0 : totalProfit / totalQuantitySold)
                    .build();
        }
    }
}