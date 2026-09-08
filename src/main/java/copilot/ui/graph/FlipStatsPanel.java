package copilot.ui.graph;

import copilot.config.*;
import copilot.controller.*;
import copilot.manager.*;
import copilot.model.*;

import java.util.*;

public class FlipStatsPanel extends BaseStatsPanel {
    private static final String[] ROWS = {
            "First buy time", "Last sell time", "Status", "Bought", "Sold",
            "Avg. buy price", "Avg. sell price", "Tax", "Profit", "Profit ea.", "ROI"
    };

    // Set custom cell renderer for value column to color the Profit (row 8) and ROI (row 10) rows
    public FlipStatsPanel(GraphSettings configManager, CopilotConfig copilotConfig) {
        super(configManager, ROWS, 450,
                new ValueRenderer(copilotConfig, "0.00%", Collections.singletonList(10), Collections.singletonList(8)));
    }

    public void populate(Flip flip, Items itemController) {
        setItem(itemController, flip.itemId, true);

        long profitPerItem = flip.closedQuantity > 0
                ? flip.profit / flip.closedQuantity
                : 0L;
        long closedCostBasis = flip.openedQuantity > 0
                ? (flip.spent * flip.closedQuantity) / flip.openedQuantity
                : 0L;
        String roi = closedCostBasis > 0
                ? String.format("%.2f%%", ((double) flip.profit / (double) closedCostBasis) * 100.0d)
                : "Unknown";

        setValues(
                formatTimestamp(flip.openedTime),
                formatTimestamp(flip.closedTime),
                flip.status.name(),
                formatNumber(flip.openedQuantity),
                formatNumber(flip.closedQuantity),
                formatNumber(flip.getAvgBuyPrice()),
                formatNumber(flip.getAvgSellPrice()),
                formatNumber(flip.taxPaid),
                formatNumber(flip.profit),
                formatNumber(profitPerItem),
                roi);
    }
}
