package copilot.ui;

import org.junit.Test;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class PreferencesOptionSelectorTest {
    @Test
    public void refreshDoesNotWritePreferencesButUserSelectionDoes() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<Number> changes = new ArrayList<>();
            PreferencesPanel.OptionSelector selector = new PreferencesPanel.OptionSelector(
                    new PreferencesPanel.Option[]{
                            new PreferencesPanel.Option("Auto", null),
                            new PreferencesPanel.Option("100K", 100_000L),
                            new PreferencesPanel.Option("200K", 200_000L)}, changes::add);
            selector.selectValue(200_000L, 0);
            assertEquals(2, selector.getSelectedIndex());
            selector.selectValue(42L, 1);
            assertEquals(1, selector.getSelectedIndex());
            selector.selectValue(null, 1);
            assertEquals(0, selector.getSelectedIndex());
            assertTrue(changes.isEmpty());
            selector.setSelectedIndex(1);
            selector.setSelectedIndex(0);
            assertEquals(java.util.Arrays.asList(100_000L, null), changes);
        });
    }

    @Test
    public void numericTypesRetainExistingEqualityRules() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PreferencesPanel.OptionSelector selector = new PreferencesPanel.OptionSelector(
                    new PreferencesPanel.Option[]{new PreferencesPanel.Option("Auto", null),
                            new PreferencesPanel.Option("2", 2)}, ignored -> fail("Refresh triggered setter"));
            selector.selectValue(2L, 0);
            assertEquals(0, selector.getSelectedIndex());
            selector.selectValue(2, 0);
            assertEquals(1, selector.getSelectedIndex());
        });
    }
    @Test public void generatedPresetsKeepLabelsValuesAndNumberTypes() throws Exception {
        String[] fields = {"MIN_PREDICTED_PROFIT_OPTIONS", "RESERVED_SLOTS_OPTIONS", "DUMP_ALERT_MIN_PROFIT_OPTIONS"};
        String[][] labels = {{"Auto", "20K", "50K", "100K", "200K", "500K", "1M"},
                {"Auto", "0", "1", "2", "3", "4", "5", "6", "7", "8"},
                {"Off", "100K+", "200K+", "500K+", "1M+", "2M+", "5M+"}};
        Number[][] values = {{null, 20000L, 50000L, 100000L, 200000L, 500000L, 1000000L},
                {null, 0, 1, 2, 3, 4, 5, 6, 7, 8},
                {null, 100000L, 200000L, 500000L, 1000000L, 2000000L, 5000000L}};
        for (int group = 0; group < fields.length; group++) {
            var field = PreferencesPanel.class.getDeclaredField(fields[group]);
            field.setAccessible(true);
            var options = (PreferencesPanel.Option[]) field.get(null);
            int row = group;
            SwingUtilities.invokeAndWait(() -> {
                List<Number> changes = new ArrayList<>();
                var selector = new PreferencesPanel.OptionSelector(options, changes::add);
                assertEquals(labels[row].length, selector.getItemCount());
                for (int j = 0; j < options.length; j++) {
                    assertEquals(labels[row][j], options[j].toString());
                    if (j > 0) {
                        selector.setSelectedIndex(j);
                        assertEquals(values[row][j], changes.get(changes.size() - 1));
                    }
                }
                selector.setSelectedIndex(0);
                assertNull(changes.get(changes.size() - 1));
            });
        }
    }
}
