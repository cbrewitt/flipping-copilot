package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.model.IntervalTimeUnit;
import com.flippingcopilot.model.SortDirection;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class FilterSortUtil {
    private FilterSortUtil() {
    }

    static int intervalStart(IntervalTimeUnit timeUnit, Integer value) {
        switch (timeUnit) {
            case ALL:
                return 1;
            case SESSION:
                return (int) Instant.now().getEpochSecond() - 3600;
            default:
                return (int) (Instant.now().getEpochSecond() - (long) value * timeUnit.getSeconds());
        }
    }

    static int totalPages(int totalRows, int pageSize) {
        return 1 + totalRows / pageSize;
    }

    static <T> void sort(List<T> rows,
                         Map<String, Comparator<T>> comparators,
                         String sortColumn,
                         SortDirection sortDirection) {
        Comparator<T> comparator = comparators.get(sortColumn);
        if (comparator == null) {
            return;
        }
        if (sortDirection == SortDirection.ASC) {
            comparator = comparator.reversed();
        }
        rows.sort(comparator);
    }

    static <T> List<T> page(List<T> rows, int page, int pageSize) {
        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, rows.size());
        return rows.subList(startIndex, endIndex);
    }
}
