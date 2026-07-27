package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.model.IntervalTimeUnit;
import com.flippingcopilot.model.SortDirection;
import lombok.Getter;

import java.util.*;

/**
 * Filter/sort/pagination state shared by the paged flips-dialog tabs. Subclasses own the
 * actual query and its caching, and get told when it needs re-running.
 */
abstract class PagedFilterSort {

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

    PagedFilterSort(String sortColumn, SortDirection sortDirection) {
        this.sortColumn = sortColumn;
        this.sortDirection = sortDirection;
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

    public synchronized Set<Integer> getFilteredItems() {
        return new HashSet<>(filteredItems);
    }

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
