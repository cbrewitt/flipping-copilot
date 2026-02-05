package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.manager.CopilotLoginManager;
import com.flippingcopilot.model.FlipManager;
import com.flippingcopilot.model.FlipV2;
import com.flippingcopilot.model.IntervalTimeUnit;
import com.flippingcopilot.model.SortDirection;
import joptsimple.internal.Strings;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Named;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Predicate;


@Slf4j
public class FlipFilterAndSort {

    public static final int DEFAULT_PAGE_SIZE = 50;

    // Sort comparators map
    private static final Map<String, Comparator<FlipV2>> SORT_COMPARATORS = new HashMap<>();

    static {
        // Last sell time - special handling for non-closed trades
        SORT_COMPARATORS.put("Last sell time", Comparator.comparing(FlipV2::lastTransactionTime).reversed());
        SORT_COMPARATORS.put("First buy time", Comparator.comparing(FlipV2::getOpenedTime));
        SORT_COMPARATORS.put("Account", Comparator.comparing(FlipV2::getAccountId));
        SORT_COMPARATORS.put("Item", Comparator.comparing(f -> f.getCachedItemName() != null ? f.getCachedItemName() : ""));
        SORT_COMPARATORS.put("Status", Comparator.comparing(FlipV2::getStatus));
        SORT_COMPARATORS.put("Bought", Comparator.comparing(FlipV2::getOpenedQuantity));
        SORT_COMPARATORS.put("Sold", Comparator.comparing(FlipV2::getClosedQuantity));
        SORT_COMPARATORS.put("Avg. buy price", Comparator.comparing(FlipV2::getSpent));
        SORT_COMPARATORS.put("Avg. sell price", Comparator.comparing(FlipV2::getReceivedPostTax));
        SORT_COMPARATORS.put("Tax", Comparator.comparing(FlipV2::getTaxPaid));
        SORT_COMPARATORS.put("Profit", Comparator.comparing(FlipV2::getProfit));
        SORT_COMPARATORS.put("Profit ea.", Comparator.comparing(f -> f.getClosedQuantity() > 0 ? f.getProfit() / f.getClosedQuantity() : 0L));
    }

    // dependencies
    private final FlipManager flipManager;
    private final Consumer<List<FlipV2>> flipsCallback;
    private final Consumer<Integer> totalPagesChangedCallback;
    private final Consumer<Boolean> slowLoadingCallback;
    private final ExecutorService executorService;
    private final CopilotLoginManager copilotLoginManager;
    private final ItemController itemController;

    // state
    private List<FlipV2> cachedFlips = new ArrayList<>();
    private Integer cachedAccountId = null;
    private int cachedIntervalStartTime = Integer.MIN_VALUE;
    private boolean cachedIncludeBuyingFlips = true;
    private Set<Integer> cachedFilteredItems = new HashSet<>();
    private SortDirection cachedSortDirection = SortDirection.DESC;
    private String cachedSortColumn = "";

    private int intervalStartTime = 1;
    private Integer accountId = null;
    private boolean includeBuyingFlips = true;
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
                             @Named("copilotExecutor") ExecutorService executorService,
                             CopilotLoginManager copilotLoginManager, ItemController itemController) {
        this.flipManager = flipManager;

        this.flipsCallback = flipsCallback;
        this.totalPagesChangedCallback = totalPagesChangedCallback;
        this.slowLoadingCallback = slowLoadingCallback;
        this.executorService = executorService;
        this.copilotLoginManager = copilotLoginManager;
        this.itemController = itemController;
    }

    public synchronized void setIncludeBuyingFlips(boolean b) {
        includeBuyingFlips = b;
        reloadFlips(true, false);
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
        reloadFlips(true, false);
    }

    public synchronized void setAccountId(Integer accountId) {
        if(!Objects.equals(accountId,this.accountId)) {
            this.accountId = accountId;
            reloadFlips(true, false);
        }
    }

    public synchronized Set<Integer> getFilteredItems() {
        return new HashSet<>(filteredItems);
    }

    public synchronized void setFilteredItems(Set<Integer> filteredItems) {
        if(!Objects.equals(filteredItems, this.filteredItems)) {
            this.filteredItems = filteredItems;
            reloadFlips(true, false);
        }
    }

    public synchronized void setPageSize(int newSize) {
        if(newSize != pageSize) {
            pageSize = newSize;
            reloadFlips(true, false);
        }
    }

    public synchronized void setSortColumn(String sortColumn) {
        if(!sortColumn.equals(this.sortColumn)) {
            this.sortColumn = sortColumn;
            reloadFlips(false, false);
        }
    }

    public synchronized void setSortDirection(SortDirection sortDirection) {
        if(!Objects.equals(sortDirection, this.sortDirection)) {
            this.sortDirection = sortDirection;
            reloadFlips(false, false);
        }
    }

    public synchronized void setPage(int page) {
        if(page != this.page) {
            this.page = page;
            reloadFlips(false, false);
        }
    }
    public void reloadFlips(boolean totalPagesMaybeChanged, boolean forceReload) {
        executorService.submit(() -> _reloadFlips(totalPagesMaybeChanged, forceReload));
    }

    private synchronized void _reloadFlips(boolean totalPagesMaybeChanged, boolean forceReload) {
        try {
            if (canUseFlipsManager()) {
                if (totalPagesMaybeChanged || forceReload) {
                    totalFlips = flipManager.calculateStats(intervalStartTime, accountId).flipsMade;
                    totalPagesChangedCallback.accept(1 + totalFlips / pageSize);
                }
                flipsCallback.accept(flipManager.getPageFlips(page, pageSize, intervalStartTime, accountId));
            } else {
                slowLoadingCallback.accept(true);
                boolean cachedFlipsOutOfDate = !Objects.equals(cachedAccountId, accountId) || !cachedFilteredItems.equals(filteredItems) || cachedIntervalStartTime != intervalStartTime || cachedIncludeBuyingFlips != includeBuyingFlips;
                boolean cachedSortOutOfDate = cachedFlipsOutOfDate || !cachedSortColumn.equals(sortColumn) || !cachedSortDirection.equals(sortDirection);
                log.debug("cachedFlipsOutOfDate={}, cachedSortOutOfDate={}", cachedFlipsOutOfDate, cachedSortOutOfDate);
                if (cachedFlipsOutOfDate || forceReload) {
                    log.debug("reloading cached flips");
                    cachedAccountId = accountId;
                    cachedFilteredItems.clear();
                    cachedFilteredItems.addAll(filteredItems);
                    cachedIntervalStartTime = intervalStartTime;
                    cachedIncludeBuyingFlips = includeBuyingFlips;
                    cachedFlips.clear();
                    Predicate<FlipV2> flipFilter = filteredItems.isEmpty() ? f -> true : f -> filteredItems.contains(f.getItemId());
                    flipManager.aggregateFlips(intervalStartTime, accountId, includeBuyingFlips, (f) -> {
                        if(flipFilter.test(f)) {
                            f.setCachedItemName(itemController.getItemName(f.getItemId()));
                            cachedFlips.add(f);
                        }
                    });
                    log.debug("loaded {} cached flips", cachedFlips.size());
                }

                if (totalPagesMaybeChanged || forceReload) {
                    log.debug("updating total pages");
                    int totalPages = 1 + cachedFlips.size() / pageSize;
                    totalPagesChangedCallback.accept(totalPages);
                    log.debug("updated total pages to {} cached flips", totalPages);
                }

                if (cachedSortOutOfDate || forceReload) {
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
        return !includeBuyingFlips && sortDirection == SortDirection.DESC && sortColumn.equals("Last sell time") && filteredItems.isEmpty();
    }

    public synchronized void writeCsvRecords(FileWriter writer) {
        try {
            writer.write(Strings.join(FlipsPanel.COLUMN_NAMES, ","));
        } catch (IOException e) {
            throw new RuntimeException("writing flips CSV header", e);
        }

        Consumer<FlipV2> c = f -> {
            try {
                writer.write("\n"+toCSVRow(f));
            } catch (IOException e) {
                throw new RuntimeException("writing flips CSV row", e);
            }
        };
        if (canUseFlipsManager()) {
            flipManager.aggregateFlips(intervalStartTime, accountId, includeBuyingFlips, c);
        } else {
            cachedFlips.forEach(c);
        }
    }

    private String toCSVRow(FlipV2 f) {
        Map<Integer, String> accountIdToDisplayName = copilotLoginManager.accountIDToDisplayNameMap();
        long profitPerItem = f.getClosedQuantity() > 0 ? f.getProfit() / f.getClosedQuantity() : 0L;

        return String.join(",",
                formatTimestampISO(f.getOpenedTime()),
                formatTimestampISO(f.getClosedTime()),
                escapeCSV(accountIdToDisplayName.getOrDefault(f.getAccountId(), "Display name not loaded")),
                escapeCSV(f.getCachedItemName()),
                f.getStatus().name(),
                String.valueOf(f.getOpenedQuantity()),
                String.valueOf(f.getClosedQuantity()),
                String.valueOf(f.getSpent() / f.getOpenedQuantity()),
                String.valueOf(f.getClosedQuantity() == 0 ? 0 : (f.getReceivedPostTax() + f.getTaxPaid()) / f.getClosedQuantity()),
                String.valueOf(f.getTaxPaid()),
                String.valueOf(f.getProfit()),
                String.valueOf(profitPerItem)
        );
    }

    public static String formatTimestampISO(int timestamp) {
        if (timestamp == 0) {
            return "";
        }
        return Instant.ofEpochSecond(timestamp).toString();
    }

    public static String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}