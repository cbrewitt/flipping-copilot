package copilot.model;

import org.junit.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class InventoryTotalsTest {
    @Test public void totalsIncludeEveryMatchingStackAndKeepLongArithmetic() {
        var inventory = new Inventory();
        inventory.add(new RSItem(100, 3));
        inventory.add(new RSItem(200, 9));
        inventory.add(new RSItem(100, 4));
        assertEquals(7, inventory.getTotalAmount(100));
        assertEquals(9, inventory.getTotalAmount(200));
        assertEquals(0, inventory.getTotalAmount(300));
        inventory.add(new RSItem(400, Long.MAX_VALUE));
        inventory.add(new RSItem(400, 1));
        assertEquals(Long.MIN_VALUE, inventory.getTotalAmount(400));
    }

    @Test public void missingCollectionIgnoresNonpositiveAmountsAndStopsAtFirstShortfall() {
        var inventory = new Inventory();
        inventory.add(new RSItem(100, 3));
        inventory.add(new RSItem(100, 4));
        assertFalse(inventory.missingJustCollected(Map.of(100, 7L, 200, 0L, 300, -1L)));
        assertTrue(inventory.missingJustCollected(Map.of(100, 8L)));
        assertTrue(inventory.missingJustCollected(Map.of(200, 1L)));
        var quantities = new LinkedHashMap<Integer, Long>();
        quantities.put(100, 8L);
        quantities.put(200, null);
        assertTrue(inventory.missingJustCollected(quantities));
    }
}
