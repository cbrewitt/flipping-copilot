package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.model.*;
import com.flippingcopilot.rs.CopilotLoginRS;
import joptsimple.internal.Strings;
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
public class FlipFilterAndSort extends PagedFilterSort {

    // dependencies
    private final FlipManager flipManager;
    private final Consumer<List<FlipV2>> flipsCallback;
    private final Consumer<Integer> totalPagesChangedCallback;
    private final Consumer<Boolean> slowLoadingCallback;
    private final ExecutorService executorService;
    private final CopilotLoginRS copilotLoginRS;
    private final ItemController itemController;

    // state
    private final List<FlipV2> cachedFlips = new ArrayList<>();
    private EnumSet<FlipStatus> cachedIncludedStatuses = EnumSet.allOf(FlipStatus.class);
    private SortDirection cachedSortDirection = SortDirection.DESC;
    private String cachedSortColumn = "";

    private EnumSet<FlipStatus> includedStatuses = EnumSet.allOf(FlipStatus.class);
    private int totalFlips = 1;

    public FlipFilterAndSort(FlipManager flipManager,
                             Consumer<List<FlipV2>> flipsCallback,
                             Consumer<Integer> totalPagesChangedCallback,
                             Consumer<Boolean> slowLoadingCallback,
                             @Named("copilotExecutor") ExecutorService executorService,
                             CopilotLoginRS copilotLoginRS, ItemController itemController) {
        super("Last sell time", SortDirection.DESC);
        this.flipManager = flipManager;

        this.flipsCallback = flipsCallback;
        this.totalPagesChangedCallback = totalPagesChangedCallback;
        this.slowLoadingCallback = slowLoadingCallback;
        this.executorService = executorService;
        this.copilotLoginRS = copilotLoginRS;
        this.itemController = itemController;
    }

    public synchronized void setIncludedStatuses(Set<FlipStatus> statuses) {
        EnumSet<FlipStatus> resolved = statuses == null || statuses.isEmpty()
                ? EnumSet.noneOf(FlipStatus.class)
                : EnumSet.copyOf(statuses);
        if (!Objects.equals(includedStatuses, resolved)) {
            includedStatuses = resolved;
            reloadFlips(true, false);
        }
    }

    @Override
    protected void reload(boolean totalPagesMaybeChanged) {
        reloadFlips(totalPagesMaybeChanged, false);
    }

    public void reloadFlips(boolean totalPagesMaybeChanged, boolean forceReload) {
        executorService.submit(() -> _reloadFlips(totalPagesMaybeChanged, forceReload));
    }

    private synchronized void _reloadFlips(boolean totalPagesMaybeChanged, boolean forceReload) {
        try {
            if (canUseFlipsManager()) {
                if (totalPagesMaybeChanged || forceReload) {
                    totalFlips = flipManager.calculateStats(intervalStartTime, accountId).flipsMade;
                    totalPagesChangedCallback.accept(FilterSortUtil.totalPages(totalFlips, pageSize));
                }
                flipsCallback.accept(flipManager.getPageFlips(page, pageSize, intervalStartTime, accountId));
            } else {
                slowLoadingCallback.accept(true);
                boolean cachedFlipsOutOfDate = !Objects.equals(cachedAccountId, accountId)
                        || !cachedFilteredItems.equals(filteredItems)
                        || cachedIntervalStartTime != intervalStartTime
                        || !cachedIncludedStatuses.equals(includedStatuses);
                boolean cachedSortOutOfDate = cachedFlipsOutOfDate || !cachedSortColumn.equals(sortColumn) || !cachedSortDirection.equals(sortDirection);
                log.debug("cachedFlipsOutOfDate={}, cachedSortOutOfDate={}", cachedFlipsOutOfDate, cachedSortOutOfDate);
                if (cachedFlipsOutOfDate || forceReload) {
                    log.debug("reloading cached flips");
                    cachedAccountId = accountId;
                    cachedFilteredItems.clear();
                    cachedFilteredItems.addAll(filteredItems);
                    cachedIntervalStartTime = intervalStartTime;
                    cachedIncludedStatuses = includedStatuses.isEmpty()
                            ? EnumSet.noneOf(FlipStatus.class)
                            : EnumSet.copyOf(includedStatuses);
                    cachedFlips.clear();
                    Predicate<FlipV2> itemFilter = filteredItems.isEmpty() ? f -> true : f -> filteredItems.contains(f.getItemId());
                    Predicate<FlipV2> statusFilter = f -> includedStatuses.contains(f.getStatus());
                    flipManager.aggregateFlips(intervalStartTime, accountId, includedStatuses.contains(FlipStatus.BUYING), (f) -> {
                        if(itemFilter.test(f) && statusFilter.test(f)) {
                            f.setCachedItemName(itemController.getItemName(f.getItemId()));
                            cachedFlips.add(f);
                        }
                    });
                    log.debug("loaded {} cached flips", cachedFlips.size());
                }

                if (totalPagesMaybeChanged || forceReload) {
                    log.debug("updating total pages");
                    int totalPages = FilterSortUtil.totalPages(cachedFlips.size(), pageSize);
                    totalPagesChangedCallback.accept(totalPages);
                    log.debug("updated total pages to {} cached flips", totalPages);
                }

                if (cachedSortOutOfDate || forceReload) {
                    log.debug("re-sorting cached flips");
                    cachedSortColumn = sortColumn;
                    cachedSortDirection = sortDirection;
                    FilterSortUtil.sort(cachedFlips, FlipTableUtil.COMPARATORS, sortColumn, sortDirection);
                    log.debug("re-sorted flips");
                }
                slowLoadingCallback.accept(false);
                flipsCallback.accept(FilterSortUtil.page(cachedFlips, page, pageSize));
                log.debug("_reloadFlips end");
            }
        } catch (Exception e) {
            log.warn("error filtering/sorting flips", e);
        }
    }

    private boolean canUseFlipsManager() {
        return !includedStatuses.contains(FlipStatus.BUYING)
                && includedStatuses.contains(FlipStatus.FINISHED)
                && includedStatuses.contains(FlipStatus.SELLING)
                && sortDirection == SortDirection.DESC
                && sortColumn.equals("Last sell time")
                && filteredItems.isEmpty();
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
            flipManager.aggregateFlips(intervalStartTime, accountId, false, c);
        } else {
            cachedFlips.forEach(c);
        }
    }

    private String toCSVRow(FlipV2 f) {
        Map<Integer, String> accountIdToDisplayName = copilotLoginRS.get().accountIdToDisplayName;
        return String.join(",",
                formatTimestampISO(f.getOpenedTime()),
                formatTimestampISO(f.getClosedTime()),
                escapeCSV(accountIdToDisplayName.getOrDefault(f.getAccountId(), "Display name not loaded")),
                escapeCSV(f.getCachedItemName()),
                f.getStatus().name(),
                String.valueOf(f.getOpenedQuantity()),
                String.valueOf(f.getClosedQuantity()),
                String.valueOf(FlipTableUtil.averageBuy(f)),
                String.valueOf(FlipTableUtil.averageSell(f)),
                String.valueOf(f.getTaxPaid()),
                String.valueOf(f.getProfit()),
                String.valueOf(FlipTableUtil.profitEach(f))
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
