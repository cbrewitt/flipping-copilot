package com.flippingcopilot.ui.graph;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.manager.PriceGraphConfigManager;
import com.flippingcopilot.model.FlipV2;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.Component;

public class FlipStatsPanel extends BaseStatsPanel {
    private static final String[] ROWS = {
            "First buy time", "Last sell time", "Status", "Bought", "Sold",
            "Avg. buy price", "Avg. sell price", "Tax", "Profit", "Profit ea.", "ROI"
    };

    public FlipStatsPanel(PriceGraphConfigManager configManager, FlippingCopilotConfig copilotConfig) {
        super(configManager, copilotConfig, ROWS, 450, new ProfitRenderer(copilotConfig));
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

    private static class ProfitRenderer extends DefaultTableCellRenderer {
        private final FlippingCopilotConfig config;

        ProfitRenderer(FlippingCopilotConfig config) {
            this.config = config;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (row == 8 || row == 10) {
                String valueStr = value.toString();
                if (row == 10) {
                    colorPercent(c, table, valueStr);
                } else {
                    colorProfit(c, table, valueStr);
                }
            } else {
                c.setForeground(table.getForeground());
            }
            return c;
        }

        private void colorPercent(Component c, JTable table, String valueStr) {
            if (valueStr.contains("-")) {
                c.setForeground(config.lossAmountColor());
            } else if (!valueStr.equals("0.00%")) {
                c.setForeground(config.profitAmountColor());
            } else {
                c.setForeground(table.getForeground());
            }
        }

        private void colorProfit(Component c, JTable table, String valueStr) {
            try {
                long profitValue = Long.parseLong(valueStr.replace(",", ""));
                if (profitValue < 0) {
                    c.setForeground(config.lossAmountColor());
                } else if (profitValue > 0) {
                    c.setForeground(config.profitAmountColor());
                } else {
                    c.setForeground(table.getForeground());
                }
            } catch (NumberFormatException e) {
                c.setForeground(table.getForeground());
            }
        }
    }
}
