package com.flippingcopilot.ui.graph;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.manager.PriceGraphConfigManager;
import com.flippingcopilot.model.FlipV2;

import javax.swing.table.DefaultTableModel;
import java.util.Collections;

public class FlipStatsPanel extends BaseStatsPanel {
    private static final String[] ROWS = {
            "First buy time", "Last sell time", "Status", "Bought", "Sold",
            "Avg. buy price", "Avg. sell price", "Tax", "Profit", "Profit ea.", "ROI"
    };

    // Set custom cell renderer for value column to color the Profit (row 8) and ROI (row 10) rows
    public FlipStatsPanel(PriceGraphConfigManager configManager, FlippingCopilotConfig copilotConfig) {
        super(configManager, ROWS, 450,
                new ValueRenderer(copilotConfig, "0.00%", Collections.singletonList(10), Collections.singletonList(8)));
    }

    public void populate(FlipV2 flip, ItemController itemController) {
        setItem(itemController, flip.getItemId(), true);

        long profitPerItem = flip.getClosedQuantity() > 0
                ? flip.getProfit() / flip.getClosedQuantity()
                : 0L;
        long closedCostBasis = flip.getOpenedQuantity() > 0
                ? (flip.getSpent() * flip.getClosedQuantity()) / flip.getOpenedQuantity()
                : 0L;
        String roi = closedCostBasis > 0
                ? String.format("%.2f%%", ((double) flip.getProfit() / (double) closedCostBasis) * 100.0d)
                : "Unknown";

        DefaultTableModel model = model();
        model.setValueAt(formatTimestamp(flip.getOpenedTime()), 0, 1);
        model.setValueAt(formatTimestamp(flip.getClosedTime()), 1, 1);
        model.setValueAt(flip.getStatus().name(), 2, 1);
        model.setValueAt(formatNumber(flip.getOpenedQuantity()), 3, 1);
        model.setValueAt(formatNumber(flip.getClosedQuantity()), 4, 1);
        model.setValueAt(formatNumber(flip.getAvgBuyPrice()), 5, 1);
        model.setValueAt(formatNumber(flip.getAvgSellPrice()), 6, 1);
        model.setValueAt(formatNumber(flip.getTaxPaid()), 7, 1);
        model.setValueAt(formatNumber(flip.getProfit()), 8, 1);
        model.setValueAt(formatNumber(profitPerItem), 9, 1);
        model.setValueAt(roi, 10, 1);
    }
}
