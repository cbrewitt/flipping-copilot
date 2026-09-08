package copilot.ui.graph;

import java.util.*;
import copilot.config.*;
import copilot.controller.*;
import copilot.manager.*;

import java.text.*;

public class StatsPanel extends BaseStatsPanel {
    private static final String[] ROWS = {
            "Daily Volume", "Last low time", "Last low price", "Last high time",
            "Last high price", "24h change", "Week change"
    };

    // Set custom cell renderer for value column to color the change percentages (rows 5 and 6)
    public StatsPanel(GraphSettings configManager, CopilotConfig copilotConfig) {
        super(configManager, ROWS, 400,
                new ValueRenderer(copilotConfig, "0%", Arrays.asList(5, 6), Collections.emptyList()));
    }

    public void populate(DataManager dataManager, Items itemController) {
        setItem(itemController, dataManager.data.itemId, false);

        setValues(
                formatNumber((long) dataManager.data.dailyVolume),
                formatTimestamp(dataManager.lastLowTime),
                dataManager.lastLowPrice > 0 ? formatNumber(dataManager.lastLowPrice) : "n/a",
                formatTimestamp(dataManager.lastHighTime),
                dataManager.lastHighPrice > 0 ? formatNumber(dataManager.lastHighPrice) : "n/a",
                formatPercentage((float) dataManager.priceChange24H),
                formatPercentage((float) dataManager.priceChangeWeek));
    }

    private String formatPercentage(float value) {
        var format = NumberFormat.getPercentInstance();
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }
}
