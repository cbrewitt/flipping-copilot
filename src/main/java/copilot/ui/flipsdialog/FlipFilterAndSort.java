package copilot.ui.flipsdialog;

import java.util.function.*;
import java.io.*;
import copilot.controller.*;
import copilot.model.*;
import copilot.rs.*;
import joptsimple.internal.*;
import lombok.extern.slf4j.*;

import javax.inject.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
public class FlipFilterAndSort extends PagedFilterSort<Flip> {

    // dependencies
    private final FlipManager flipManager;
    private final CopilotLogin copilotLogin;
    private final Items items;

    // state
    private final List<Flip> cachedFlips = new ArrayList<>();
    private EnumSet<FlipStatus> cachedIncludedStatuses = EnumSet.allOf(FlipStatus.class);

    private EnumSet<FlipStatus> includedStatuses = EnumSet.allOf(FlipStatus.class);
    private int totalFlips = 1;

    public FlipFilterAndSort(FlipManager flipManager,
                             Consumer<List<Flip>> flipsCallback,
                             Consumer<Integer> totalPagesChangedCallback,
                             Consumer<Boolean> slowLoadingCallback,
                             @Named("copilotExecutor") ExecutorService executor,
                             CopilotLogin copilotLogin, Items items) {
        super("Last sell time", SortDirection.DESC, flipsCallback, totalPagesChangedCallback, slowLoadingCallback, executor);
        this.flipManager = flipManager;

        this.copilotLogin = copilotLogin; this.items = items;
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
    protected void reload(boolean totalPagesMaybeChanged) { reloadFlips(totalPagesMaybeChanged, false); }

    public void reloadFlips(boolean totalPagesMaybeChanged, boolean forceReload) {
        executor.submit(() -> _reloadFlips(totalPagesMaybeChanged, forceReload));
    }

    private synchronized void _reloadFlips(boolean totalPagesMaybeChanged, boolean forceReload) {
        try {
            if (canUseFlipsManager()) {
                if (totalPagesMaybeChanged || forceReload) {
                    totalFlips = flipManager.calculateStats(intervalStartTime, accountId).flipsMade;
                    totalPagesChangedCallback.accept(FilterSortUtil.totalPages(totalFlips, pageSize));
                }
                rowsCallback.accept(flipManager.getPageFlips(page, pageSize, intervalStartTime, accountId));
            } else {
                slowLoadingCallback.accept(true);
                boolean cachedFlipsOutOfDate = queryChanged()
                        || !cachedIncludedStatuses.equals(includedStatuses);
                if (cachedFlipsOutOfDate || forceReload) {
                    rememberQuery();
                    cachedIncludedStatuses = includedStatuses.isEmpty()
                            ? EnumSet.noneOf(FlipStatus.class)
                            : EnumSet.copyOf(includedStatuses);
                    cachedFlips.clear();
                    var itemFilter = itemFilter();
                    Predicate<Flip> statusFilter = f -> includedStatuses.contains(f.status);
                    flipManager.aggregateFlips(intervalStartTime, accountId, includedStatuses.contains(FlipStatus.BUYING), (f) -> {
                        if(itemFilter.test(f) && statusFilter.test(f)) {
                            f.setCachedItemName(items.getItemName(f.itemId));
                            cachedFlips.add(f);
                        }
                    });
                    log.debug("loaded {} cached flips", cachedFlips.size());
                }

                publishPage(cachedFlips, FlipTableUtil.COMPARATORS,
                        totalPagesMaybeChanged || forceReload, cachedFlipsOutOfDate || forceReload);
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

        Consumer<Flip> c = f -> {
            try {
                writer.write("\n"+toCSVRow(f));
            } catch (IOException e) {
                throw new RuntimeException("writing flips CSV row", e);
            }
        };
        if (canUseFlipsManager()) { flipManager.aggregateFlips(intervalStartTime, accountId, false, c); } else {
            cachedFlips.forEach(c);
        }
    }

    private String toCSVRow(Flip f) {
        var accountIdToDisplayName = copilotLogin.get().accountIdToDisplayName;
        return String.join(",",
                formatTimestampISO(f.openedTime),
                formatTimestampISO(f.closedTime),
                escapeCSV(accountIdToDisplayName.getOrDefault(f.accountId, "Display name not loaded")),
                escapeCSV(f.cachedItemName),
                f.status.name(),
                String.valueOf(f.openedQuantity),
                String.valueOf(f.closedQuantity),
                String.valueOf(FlipTableUtil.averageBuy(f)),
                String.valueOf(FlipTableUtil.averageSell(f)),
                String.valueOf(f.taxPaid),
                String.valueOf(f.profit),
                String.valueOf(FlipTableUtil.profitEach(f))
        );
    }

    public static String formatTimestampISO(int timestamp) {
        if (timestamp == 0) { return ""; }
        return Instant.ofEpochSecond(timestamp).toString();
    }

    public static String escapeCSV(String value) {
        if (value == null) { return ""; }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
