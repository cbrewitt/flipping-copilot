package copilot.ui.flipsdialog;

import copilot.model.Flip;
import copilot.model.SortDirection;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class PagedFilterSortTest {
    private static class Pages extends PagedFilterSort<Integer> {
        final List<Integer> data = new ArrayList<>(Arrays.asList(1, 3, 2));
        final Map<String, Comparator<Integer>> comparators = Collections.singletonMap("value", Comparator.naturalOrder());
        Pages(List<List<Integer>> results, List<Integer> totals, List<Boolean> loading) {
            super("value", SortDirection.DESC, rows -> results.add(new ArrayList<>(rows)), totals::add, loading::add, null);
            pageSize = 2;
        }
        @Override protected void reload(boolean updateTotals) {
            boolean changed = queryChanged();
            rememberQuery();
            publishPage(data, comparators, updateTotals, changed);
        }
    }

    @Test public void sortingAndPagingReuseCachedRows() {
        List<List<Integer>> results = new ArrayList<>();
        List<Integer> totals = new ArrayList<>();
        List<Boolean> loading = new ArrayList<>();
        Pages pages = new Pages(results, totals, loading);
        pages.reload(true);
        assertEquals(Arrays.asList(1, 2), results.get(0));
        pages.setPage(2);
        assertEquals(Collections.singletonList(3), results.get(1));
        pages.setSortDirection(SortDirection.ASC);
        assertEquals(Collections.singletonList(1), results.get(2));
        pages.setPage(1);
        assertEquals(Arrays.asList(3, 2), results.get(3));
        assertEquals(Collections.singletonList(2), totals);
        assertEquals(Arrays.asList(false, false, false, false), loading);
        pages.setPage(1);
        assertEquals(4, results.size());
    }

    @Test public void querySnapshotDetectsAccountTimeAndItemChanges() {
        Pages pages = new Pages(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        assertTrue(pages.queryChanged());
        pages.rememberQuery();
        assertFalse(pages.queryChanged());
        Flip flip = new Flip();
        flip.itemId = 42;
        assertTrue(pages.itemFilter().test(flip));
        pages.filteredItems.add(7);
        assertTrue(pages.queryChanged());
        assertFalse(pages.itemFilter().test(flip));
        pages.rememberQuery();
        assertFalse(pages.queryChanged());
        pages.filteredItems.add(42);
        assertTrue(pages.queryChanged());
        assertTrue(pages.itemFilter().test(flip));
        pages.rememberQuery();
        pages.accountId = 8;
        assertTrue(pages.queryChanged());
        pages.rememberQuery();
        pages.intervalStartTime = 99;
        assertTrue(pages.queryChanged());
    }
}
