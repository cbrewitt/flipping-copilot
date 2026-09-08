package copilot.ui.flipsdialog;

import copilot.model.*;
import lombok.*;

import java.util.*;
import java.util.function.*;
import java.util.concurrent.*;

/**
 * Filter/sort/pagination state shared by the paged flips-dialog tabs. Subclasses own the
 * actual query and its caching, and get told when it needs re-running.
 */
abstract class PagedFilterSort<T> {

    static final int DEFAULT_PAGE_SIZE = 50;

    // cached query inputs, compared by the subclass to decide whether its cache is stale
    protected Integer cachedAccountId = null;
    protected int cachedIntervalStartTime = Integer.MIN_VALUE;
    protected Set<Integer> cachedFilteredItems = new HashSet<>();

    protected int intervalStartTime = 1;
    protected Integer accountId = null;
    protected Set<Integer> filteredItems = new HashSet<>();
    protected int page = 1;
    @Getter
    protected int pageSize = DEFAULT_PAGE_SIZE;
    @Getter
    protected String sortColumn;
    @Getter
    protected SortDirection sortDirection;

    protected final Consumer<List<T>> rowsCallback;
    protected final Consumer<Integer> totalPagesChangedCallback;
    protected final Consumer<Boolean> slowLoadingCallback;
    protected final ExecutorService executor;
    private String cachedSortColumn = "";
    private SortDirection cachedSortDirection;

    PagedFilterSort(String sortColumn, SortDirection sortDirection, Consumer<List<T>> rowsCallback,
                   Consumer<Integer> totalPagesChangedCallback, Consumer<Boolean> slowLoadingCallback,
                   ExecutorService executor) {
        this.sortColumn = sortColumn; this.sortDirection = sortDirection; cachedSortDirection = sortDirection;
        this.rowsCallback = rowsCallback; this.totalPagesChangedCallback = totalPagesChangedCallback;
        this.slowLoadingCallback = slowLoadingCallback; this.executor = executor;
    }

    protected boolean queryChanged() {
        return !Objects.equals(cachedAccountId, accountId)
                || !cachedFilteredItems.equals(filteredItems)
                || cachedIntervalStartTime != intervalStartTime;
    }

    protected void rememberQuery() {
        cachedAccountId = accountId;
        cachedFilteredItems.clear();
        cachedFilteredItems.addAll(filteredItems);
        cachedIntervalStartTime = intervalStartTime;
    }

    protected Predicate<Flip> itemFilter() {
        return filteredItems.isEmpty() ? f -> true : f -> filteredItems.contains(f.itemId);
    }

    protected void publishPage(List<T> rows, Map<String, Comparator<T>> comparators,
                               boolean updateTotalPages, boolean dataChanged) {
        if (updateTotalPages) { totalPagesChangedCallback.accept(FilterSortUtil.totalPages(rows.size(), pageSize)); }
        if (dataChanged || !cachedSortColumn.equals(sortColumn) || !cachedSortDirection.equals(sortDirection)) {
            cachedSortColumn = sortColumn;
            cachedSortDirection = sortDirection;
            FilterSortUtil.sort(rows, comparators, sortColumn, sortDirection);
        }
        slowLoadingCallback.accept(false);
        rowsCallback.accept(FilterSortUtil.page(rows, page, pageSize));
    }

    protected abstract void reload(boolean totalPagesMaybeChanged);

    public synchronized void setInterval(IntervalTimeUnit timeUnit, Integer value) {
        intervalStartTime = FilterSortUtil.intervalStart(timeUnit, value);
        reload(true);
    }

    public synchronized void setAccountId(Integer accountId) {
        if (!Objects.equals(accountId, this.accountId)) {
            this.accountId = accountId;
            reload(true);
        }
    }

    public synchronized Set<Integer> getFilteredItems() { return new HashSet<>(filteredItems); }

    public synchronized void setFilteredItems(Set<Integer> filteredItems) {
        if (!Objects.equals(filteredItems, this.filteredItems)) {
            this.filteredItems = filteredItems;
            reload(true);
        }
    }

    public synchronized void setPageSize(int newSize) {
        if (newSize != pageSize) {
            pageSize = newSize;
            reload(true);
        }
    }

    public synchronized void setSortColumn(String sortColumn) {
        if (!sortColumn.equals(this.sortColumn)) {
            this.sortColumn = sortColumn;
            reload(false);
        }
    }

    public synchronized void setSortDirection(SortDirection sortDirection) {
        if (!Objects.equals(sortDirection, this.sortDirection)) {
            this.sortDirection = sortDirection;
            reload(false);
        }
    }

    public synchronized void setPage(int page) {
        if (page != this.page) {
            this.page = page;
            reload(false);
        }
    }
}
