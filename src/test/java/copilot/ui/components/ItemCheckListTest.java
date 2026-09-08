package copilot.ui.components;

import copilot.model.ItemIdName;
import org.junit.Test;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class ItemCheckListTest {
    @Test public void rowAndCheckboxClicksToggleItemsAndIgnoreEmptySpace() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicReference<Set<Integer>> selection = new AtomicReference<>(new HashSet<>(Arrays.asList(1)));
            List<Set<Integer>> changes = new ArrayList<>();
            ItemSearchMultiSelect.ItemCheckList list = new ItemSearchMultiSelect.ItemCheckList(
                    () -> new HashSet<>(selection.get()), value -> {
                        changes.add(value);
                        selection.set(value);
                    });
            list.setItems(Arrays.asList(new ItemIdName(1, "First"), new ItemIdName(2, "Second")));
            list.setSize(300, 100);
            click(list, 20, 10);
            assertEquals(Collections.emptySet(), changes.get(0));
            click(list, 290, 30);
            assertEquals(Collections.singleton(2), changes.get(1));
            assertTrue(changes.get(0).isEmpty()); // Callback values are independent snapshots.
            click(list, 20, 60);
            assertEquals(2, changes.size());
            list.setItems(Collections.emptyList());
            click(list, 20, 10);
            assertEquals(2, changes.size());
        });
    }

    @Test public void reusableRendererReflectsCurrentSelectionAndKeepsScrollGeometry() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Set<Integer> selected = new HashSet<>();
            ItemSearchMultiSelect.ItemCheckList list = new ItemSearchMultiSelect.ItemCheckList(
                    () -> selected, ignored -> {});
            List<ItemIdName> items = new ArrayList<>();
            for (int i = 0; i < 10000; i++) items.add(new ItemIdName(i, "Item " + i));
            list.setItems(items);
            list.setSize(300, 200000);
            assertEquals(200000, list.getPreferredSize().height);
            assertEquals(300, list.getCellBounds(0, 0).width);
            assertEquals(0, list.getFixedCellWidth());
            assertEquals(new Dimension(300, 300), list.getPreferredScrollableViewportSize());
            assertEquals(20, list.getScrollableUnitIncrement(new Rectangle(), SwingConstants.VERTICAL, 1));
            assertEquals(300, list.getScrollableBlockIncrement(new Rectangle(), SwingConstants.VERTICAL, 1));
            assertTrue(list.getScrollableTracksViewportWidth());
            assertFalse(list.getScrollableTracksViewportHeight());
            assertFalse(list.isFocusable());
            var renderer = list.getCellRenderer();
            JPanel first = (JPanel) renderer.getListCellRendererComponent(list, items.get(0), 0, false, false);
            assertEquals("Item 0", ((JLabel) first.getComponent(0)).getText());
            assertFalse(((JCheckBox) first.getComponent(1)).isSelected());
            selected.add(9999);
            list.repaint();
            Component last = renderer.getListCellRendererComponent(list, items.get(9999), 9999, true, false);
            assertSame(first, last);
            assertEquals("Item 9999", ((JLabel) first.getComponent(0)).getText());
            assertTrue(((JCheckBox) first.getComponent(1)).isSelected());
            assertEquals(2, first.getComponentCount());
        });
    }

    private static void click(JList<?> list, int x, int y) {
        list.dispatchEvent(new MouseEvent(list, MouseEvent.MOUSE_CLICKED, 0, 0, x, y, 1, false, MouseEvent.BUTTON1));
    }
}
