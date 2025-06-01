package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.model.FlipManager;
import com.flippingcopilot.model.FlipV2;
import com.flippingcopilot.model.IntervalTimeUnit;
import com.flippingcopilot.model.SortDirection;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Named;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.Collectors;


@Slf4j
public class FlipFilterAndSort {

    public static final int DEFAULT_PAGE_SIZE = 50;

    // Sort comparators map
    private static final Map<String, Comparator<FlipV2>> SORT_COMPARATORS = new HashMap<>();

    static {
        // Last sell time - special handling for non-closed trades
        SORT_COMPARATORS.put("Last sell time", (f1, f2) -> {
            // Non-closed trades (closedTime == 0) always go to top
            if (f1.getClosedTime() == 0 && f2.getClosedTime() == 0) {
                return Integer.compare(f2.getOpenedTime(), f1.getOpenedTime()); // Newer opened trades first
            }
            if (f1.getClosedTime() == 0) return -1;
            if (f2.getClosedTime() == 0) return 1;
            // Both closed, sort by Last sell time
            return Integer.compare(f2.getClosedTime(), f1.getClosedTime());
        });

        SORT_COMPARATORS.put("First buy time", Comparator.comparing(FlipV2::getOpenedTime));
        SORT_COMPARATORS.put("Account", Comparator.comparing(f -> f.getAccountId()));
        SORT_COMPARATORS.put("Item", Comparator.comparing(f -> f.getItemName() != null ? f.getItemName() : ""));
        SORT_COMPARATORS.put("Status", Comparator.comparing(FlipV2::getStatus));
        SORT_COMPARATORS.put("Bought", Comparator.comparing(FlipV2::getOpenedQuantity));
        SORT_COMPARATORS.put("Sold", Comparator.comparing(FlipV2::getClosedQuantity));
        SORT_COMPARATORS.put("Avg. buy price", Comparator.comparing(FlipV2::getSpent));
        SORT_COMPARATORS.put("Avg. sell price", Comparator.comparing(FlipV2::getReceivedPostTax));
        SORT_COMPARATORS.put("Tax", Comparator.comparing(FlipV2::getTaxPaid));
        SORT_COMPARATORS.put("Profit", Comparator.comparing(FlipV2::getProfit));
        SORT_COMPARATORS.put("Profit ea.", Comparator.comparing(f ->
                f.getClosedQuantity() > 0 ? f.getProfit() / f.getClosedQuantity() : 0L));
    }

    // dependencies
    private final FlipManager flipManager;
    private final Consumer<List<FlipV2>> flipsCallback;
    private final Consumer<Integer> totalPagesChangedCallback;
    private final Consumer<Boolean> slowLoadingCallback;
    private final ExecutorService executorService;

    // state
    private List<FlipV2> cachedFlips = new ArrayList<>();
    private String cachedDisplayName = null;
    private int cachedIntervalStartTime = -10;
    private boolean cachedIncludeOpeningFlips = false;
    private Set<Integer> cachedFilteredItems = new HashSet<>();
    private SortDirection cachedSortDirection = SortDirection.DESC;
    private String cachedSortColumn = "";

    private int intervalStartTime = -1;
    private String displayName = null;
    private boolean includeOpeningFlips = false;
    @Getter
    private String sortColumn = "Last sell time";
    @Getter
    private SortDirection sortDirection = SortDirection.DESC;
    private Set<Integer> filteredItems = new HashSet<>();
    @Getter
    private int pageSize = DEFAULT_PAGE_SIZE;
    private int totalFlips = 1;
    private int page = 1;

    public FlipFilterAndSort(FlipManager flipManager,
                             Consumer<List<FlipV2>> flipsCallback,
                             Consumer<Integer> totalPagesChangedCallback,
                             Consumer<Boolean> slowLoadingCallback,
                             @Named("copilotExecutor") ExecutorService executorService) {
        this.flipManager = flipManager;

        this.flipsCallback = flipsCallback;
        this.totalPagesChangedCallback = totalPagesChangedCallback;
        this.slowLoadingCallback = slowLoadingCallback;
        this.executorService = executorService;
    }

    public synchronized void setIncludeOpeningFlips(boolean b) {
        includeOpeningFlips = b;
        reloadFlips(true);
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
        reloadFlips(true);
    }

    public synchronized void setDisplayName(String displayName) {
        this.displayName = displayName;
        reloadFlips(true);
    }

    public synchronized void setFilteredItems(Set<Integer> filteredItems) {
        this.filteredItems = filteredItems;
        reloadFlips(true);
    }

    public synchronized void setPageSize(int newSize) {
        pageSize = newSize;
        reloadFlips(true);
    }

    public synchronized void setSortColumn(String sortColumn) {
        this.sortColumn = sortColumn;
        reloadFlips(false);
    }

    public synchronized void setSortDirection(SortDirection sortDirection) {
        this.sortDirection = sortDirection;
        reloadFlips(false);
    }

    public synchronized void setPage(int page) {
        this.page = page;
        reloadFlips(false);
    }
    public synchronized void reloadFlips(boolean totalPagesMaybeChanged) {
        executorService.submit(() -> _reloadFlips(totalPagesMaybeChanged));
    }

    private synchronized void _reloadFlips(boolean totalPagesMaybeChanged) {
        try {
            if (canUseFlipsManager()) {
                if (totalPagesMaybeChanged) {
                    totalFlips = flipManager.calculateStats(intervalStartTime, displayName).flipsMade;
                    totalPagesChangedCallback.accept(1 + totalFlips / pageSize);
                }
                List<FlipV2> flips = flipManager.getPageFlips(page, pageSize, intervalStartTime, displayName, includeOpeningFlips);
                flipsCallback.accept(flips);
            } else {
                slowLoadingCallback.accept(true);
                boolean cachedFlipsOutOfDate = !Objects.equals(cachedDisplayName, displayName) || !cachedFilteredItems.equals(filteredItems) || cachedIntervalStartTime != intervalStartTime || cachedIncludeOpeningFlips != includeOpeningFlips;
                boolean cachedSortOutOfDate = cachedFlipsOutOfDate || !cachedSortColumn.equals(sortColumn) || !cachedSortDirection.equals(sortDirection);
                log.debug("cachedFlipsOutOfDate={}, cachedSortOutOfDate={}", cachedFlipsOutOfDate, cachedSortOutOfDate);
                if (cachedFlipsOutOfDate) {
                    log.debug("reloading cached flips");
                    cachedDisplayName = displayName;
                    cachedFilteredItems = new HashSet<>(filteredItems);
                    cachedIntervalStartTime = intervalStartTime;
                    cachedIncludeOpeningFlips = includeOpeningFlips;
                    cachedFlips = flipManager.getPageFlips(1, 1_000_000_000, intervalStartTime, displayName, includeOpeningFlips);
                    if (!filteredItems.isEmpty()) {
                        cachedFlips = cachedFlips.stream().filter(i -> filteredItems.contains(i.getItemId())).collect(Collectors.toList());
                    }
                    log.debug("loaded {} cached flips", cachedFlips.size());
                }

                if (totalPagesMaybeChanged) {
                    log.debug("updating total pages");
                    int totalPages = 1 + cachedFlips.size() / pageSize;
                    totalPagesChangedCallback.accept(totalPages);
                    log.debug("updated total pages to {} cached flips", totalPages);
                }

                if (cachedSortOutOfDate) {
                    log.debug("re-sorting cached flips");
                    cachedSortColumn = sortColumn;
                    cachedSortDirection = sortDirection;

                    // Apply sorting
                    Comparator<FlipV2> comparator = SORT_COMPARATORS.get(sortColumn);
                    if (comparator != null) {
                        if (sortDirection == SortDirection.ASC) {
                            comparator = comparator.reversed();
                        }
                        cachedFlips.sort(comparator);
                    }
                    log.debug("re-sorted flips");
                }
                int startIndex = (page - 1) * pageSize;
                int endIndex = Math.min(startIndex + pageSize, cachedFlips.size());
                slowLoadingCallback.accept(false);
                flipsCallback.accept(cachedFlips.subList(startIndex, endIndex));
                log.debug("_reloadFlips end");
            }
        } catch (Exception e) {
            log.warn("error filtering/sorting flips", e);
        }
    }

    private boolean canUseFlipsManager() {
        return sortDirection == SortDirection.DESC && sortColumn.equals("Last sell time") && filteredItems.isEmpty();
    }
}