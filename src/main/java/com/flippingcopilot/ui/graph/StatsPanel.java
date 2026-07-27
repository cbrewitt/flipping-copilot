package com.flippingcopilot.ui.graph;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.manager.PriceGraphConfigManager;

import javax.swing.table.DefaultTableModel;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Collections;

public class StatsPanel extends BaseStatsPanel {
    private static final String[] ROWS = {
            "Daily Volume", "Last low time", "Last low price", "Last high time",
            "Last high price", "24h change", "Week change"
    };

    // Set custom cell renderer for value column to color the change percentages (rows 5 and 6)
    public StatsPanel(PriceGraphConfigManager configManager, FlippingCopilotConfig copilotConfig) {
        super(configManager, ROWS, 400,
                new ValueRenderer(copilotConfig, "0%", Arrays.asList(5, 6), Collections.emptyList()));
    }

    public void populate(DataManager dataManager, ItemController itemController) {
        setItem(itemController, dataManager.data.itemId, false);

        DefaultTableModel model = model();
        model.setValueAt(formatNumber((long) dataManager.data.dailyVolume), 0, 1);
        model.setValueAt(formatTimestamp(dataManager.lastLowTime), 1, 1);
        model.setValueAt(dataManager.lastLowPrice > 0 ? formatNumber(dataManager.lastLowPrice) : "n/a", 2, 1);
        model.setValueAt(formatTimestamp(dataManager.lastHighTime), 3, 1);
        model.setValueAt(dataManager.lastHighPrice > 0 ? formatNumber(dataManager.lastHighPrice) : "n/a", 4, 1);
        model.setValueAt(formatPercentage((float) dataManager.priceChange24H), 5, 1);
        model.setValueAt(formatPercentage((float) dataManager.priceChangeWeek), 6, 1);
    }

    private String formatPercentage(float value) {
        NumberFormat format = NumberFormat.getPercentInstance();
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }
}
