package copilot.ui.graph;

import copilot.controller.Items;
import copilot.manager.GraphSettings;
import copilot.model.*;
import copilot.ui.graph.model.Config;
import copilot.ui.graph.model.Data;
import org.junit.Test;
import javax.swing.*;
import java.util.*;
import java.util.List;
import static org.junit.Assert.*;

public class StatsValuesTest {
    private GraphSettings config() {
        return new GraphSettings(null, null) {
            @Override public synchronized Config getConfig() { return new Config(); }
        };
    }

    @Test public void flipValuesKeepFormattingAndOrderedCellEvents() throws Exception {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            SwingUtilities.invokeAndWait(() -> {
                FlipStatsPanel panel = new FlipStatsPanel(config(), null) {
                    @Override protected void setItem(Items controller, int id, boolean reload) {}
                };
                List<Integer> rows = trackRows(panel);
                Flip flip = new Flip(); flip.status = FlipStatus.FINISHED;
                flip.openedQuantity = 10; flip.closedQuantity = 4; flip.spent = 1000;
                flip.receivedPostTax = 600; flip.taxPaid = 10; flip.profit = 200;
                panel.populate(flip, null);
                assertValues(panel, rows, "n/a", "n/a", "FINISHED", "10", "4", "100", "152",
                        "10", "200", "50", "50.00%");
            });
        } finally { Locale.setDefault(original); }
    }

    @Test public void priceValuesKeepUnavailablePricesAndPercentages() throws Exception {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            SwingUtilities.invokeAndWait(() -> {
                StatsPanel panel = new StatsPanel(config(), null) {
                    @Override protected void setItem(Items controller, int id, boolean reload) {}
                };
                List<Integer> rows = trackRows(panel);
                Data data = new Data(); data.dailyVolume = 1234.9;
                DataManager manager = new DataManager(data, null);
                manager.lastLowPrice = -1; manager.lastHighPrice = 123;
                manager.priceChange24H = 0.125; manager.priceChangeWeek = -0.035;
                panel.populate(manager, null);
                assertValues(panel, rows, "1,234", "n/a", "n/a", "n/a", "123", "12.5%", "-3.5%");
            });
        } finally { Locale.setDefault(original); }
    }

    private List<Integer> trackRows(BaseStatsPanel panel) {
        List<Integer> rows = new ArrayList<>();
        panel.statsTable.getModel().addTableModelListener(event -> {
            assertEquals(1, event.getColumn());
            assertEquals(event.getFirstRow(), event.getLastRow());
            rows.add(event.getFirstRow());
        });
        return rows;
    }

    private void assertValues(BaseStatsPanel panel, List<Integer> rows, String... values) {
        assertEquals(values.length, rows.size());
        for (int i = 0; i < values.length; i++) {
            assertEquals(Integer.valueOf(i), rows.get(i));
            assertEquals(values[i], panel.statsTable.getValueAt(i, 1));
        }
    }
}
