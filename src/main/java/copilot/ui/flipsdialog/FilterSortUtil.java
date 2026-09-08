package copilot.ui.flipsdialog;

import copilot.model.*;

import java.time.*;
import java.util.*;

final class FilterSortUtil {
    private FilterSortUtil() {
    }

    static int intervalStart(IntervalTimeUnit timeUnit, Integer value) {
        switch (timeUnit) {
            case ALL:
                return 1;
            case SESSION:
                // TODO: Get session start time from SessionManager
                return (int) Instant.now().getEpochSecond() - 3600; // Default to 1 hour ago
            default:
                return (int) (Instant.now().getEpochSecond() - (long) value * timeUnit.seconds);
        }
    }

    static int totalPages(int totalRows, int pageSize) { return 1 + totalRows / pageSize; }

    static <T> void sort(List<T> rows,
                         Map<String, Comparator<T>> comparators,
                         String sortColumn,
                         SortDirection sortDirection) {
        var comparator = comparators.get(sortColumn);
        if (comparator == null) { return; }
        if (sortDirection == SortDirection.ASC) { comparator = comparator.reversed(); }

        // Apply sorting
        rows.sort(comparator);
    }

    static <T> List<T> page(List<T> rows, int page, int pageSize) {
        int startIndex = (page - 1) * pageSize, endIndex = Math.min(startIndex + pageSize, rows.size());
        return rows.subList(startIndex, endIndex);
    }
    /** Shared totals for the account and item tables, including empty groups. */
    static class FlipTotals {
        private long profit, quantity, loss, win;
        private int count;

        void accept(Flip flip) {
            profit += flip.profit;
            quantity += flip.closedQuantity;
            count++;
            loss = Math.min(loss, flip.profit);
            win = Math.max(win, flip.profit);
        }

        AccountAggregate account(int id, String name) {
            return AccountAggregate.builder().accountId(id).accountName(name == null ? "Unknown" : name)
                    .numberOfFlips(count).biggestLoss(loss).biggestWin(win).totalProfit(profit).build();
        }

        ItemAggregate item(String name) {
            return ItemAggregate.builder().itemName(name).numberOfFlips(count)
                    .totalQuantityFlipped((int) quantity).biggestLoss(loss).biggestWin(win).totalProfit(profit)
                    .avgProfit(count == 0 ? 0 : profit / count)
                    .avgProfitEa(quantity == 0 ? 0 : profit / quantity).build();
        }
    }
}
