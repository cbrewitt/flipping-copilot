package copilot.ui.flipsdialog;

import copilot.model.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AggregateTotalsTest {
    @Test public void emptyAndSingleSignGroupsRetainZeroExtremes() {
        var totals = new FilterSortUtil.FlipTotals();
        AccountAggregate empty = totals.account(42, null);
        assertEquals(42, empty.accountId);
        assertEquals("Unknown", empty.accountName);
        assertEquals(0, empty.numberOfFlips);
        assertEquals(0, empty.biggestLoss);
        assertEquals(0, empty.biggestWin);
        assertEquals(0, totals.item("Empty").avgProfitEa);
        totals.accept(flip(42, 1, -30, 3));
        AccountAggregate loss = totals.account(42, "Player");
        assertEquals("Player", loss.accountName);
        assertEquals(-30, loss.biggestLoss);
        assertEquals(0, loss.biggestWin);
        assertEquals(-10, totals.item("Item").avgProfitEa);
        var positive = new FilterSortUtil.FlipTotals();
        positive.accept(flip(42, 1, 40, 0));
        assertEquals(0, positive.account(42, "Player").biggestLoss);
        assertEquals(40, positive.account(42, "Player").biggestWin);
        assertEquals(0, positive.item("Item").avgProfitEa);
    }

    @Test public void accountAndItemGroupingRespectFiltersAndIdentity() {
        var accounts = new AccountsAggregateFilterSort.Aggregator();
        accounts.accounts.put(99, new FilterSortUtil.FlipTotals());
        var items = new ItemAggregateFilterSort.Aggregator(flip -> flip.accountId == 1);
        for (Flip flip : new Flip[]{flip(1, 10, 100, 5), flip(1, 10, -40, 3),
                flip(1, 11, 20, 2), flip(2, 10, 500, 10)}) {
            accounts.accept(flip); items.accept(flip);
        }
        assertEquals(3, accounts.accounts.size());
        assertEquals(80, accounts.accounts.get(1).account(1, "One").totalProfit);
        assertEquals(500, accounts.accounts.get(2).account(2, "Two").totalProfit);
        assertEquals(0, accounts.accounts.get(99).account(99, "Empty").numberOfFlips);
        assertEquals(2, items.items.size());
        ItemAggregate item = items.items.get(10).item("Ten");
        assertEquals("Ten", item.itemName);
        assertEquals(2, item.numberOfFlips);
        assertEquals(8, item.totalQuantityFlipped);
        assertEquals(60, item.totalProfit);
        assertEquals(30, item.avgProfit);
        assertEquals(7, item.avgProfitEa);
        assertEquals(-40, item.biggestLoss);
        assertEquals(100, item.biggestWin);
    }

    @Test public void overflowAndLongExtremesKeepJavaArithmetic() {
        var totals = new FilterSortUtil.FlipTotals();
        totals.accept(flip(1, 10, Long.MAX_VALUE, Integer.MAX_VALUE));
        totals.accept(flip(1, 10, Long.MAX_VALUE, Integer.MAX_VALUE));
        ItemAggregate wrapped = totals.item("Overflow");
        assertEquals(-2, wrapped.totalProfit);
        assertEquals(-2, wrapped.totalQuantityFlipped);
        assertEquals(-1, wrapped.avgProfit);
        assertEquals(0, wrapped.avgProfitEa);
        totals.accept(flip(1, 10, Long.MIN_VALUE, 0));
        AccountAggregate extrema = totals.account(1, null);
        assertEquals(Long.MIN_VALUE, extrema.biggestLoss);
        assertEquals(Long.MAX_VALUE, extrema.biggestWin);
        assertEquals(Long.MAX_VALUE - 1, extrema.totalProfit);
    }

    private static Flip flip(int account, int item, long profit, int quantity) {
        Flip flip = new Flip();
        flip.accountId = account; flip.itemId = item; flip.profit = profit; flip.closedQuantity = quantity;
        return flip;
    }
}
