package com.flippingcopilot.ui.graph;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.manager.PriceGraphConfigManager;
import com.flippingcopilot.ui.graph.model.Constants;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.Component;
import java.text.NumberFormat;
import java.util.Date;

public class StatsPanel extends BaseStatsPanel {
    private static final String[] ROWS = {
            "Daily Volume", "Last low time", "Last low price", "Last high time",
            "Last high price", "24h change", "Week change"
    };

    public StatsPanel(PriceGraphConfigManager configManager, FlippingCopilotConfig copilotConfig) {
        super(configManager, copilotConfig, ROWS, 400, new ChangeRenderer(copilotConfig));
    }

    public void populate(DataManager dataManager, ItemController itemController) {
        setItem(itemController, dataManager.data.itemId, false);

        DefaultTableModel model = model();
        model.setValueAt(formatNumber((long) dataManager.data.dailyVolume), 0, 1);
        model.setValueAt(dataManager.lastLowTime > 0 ? Constants.SECOND_DATE_FORMAT.format(new Date(dataManager.lastLowTime * 1000L)) : "n/a", 1, 1);
        model.setValueAt(dataManager.lastLowPrice > 0 ? formatNumber(dataManager.lastLowPrice) : "n/a", 2, 1);
        model.setValueAt(dataManager.lastHighTime > 0 ? Constants.SECOND_DATE_FORMAT.format(new Date(dataManager.lastHighTime * 1000L)) : "n/a", 3, 1);
        model.setValueAt(dataManager.lastHighPrice > 0 ? formatNumber(dataManager.lastHighPrice) : "n/a", 4, 1);
        model.setValueAt(formatPercentage((float) dataManager.priceChange24H), 5, 1);
        model.setValueAt(formatPercentage((float) dataManager.priceChangeWeek), 6, 1);
    }

    private String formatPercentage(float value) {
        NumberFormat format = NumberFormat.getPercentInstance();
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }

    private static class ChangeRenderer extends DefaultTableCellRenderer {
        private final FlippingCopilotConfig config;

        ChangeRenderer(FlippingCopilotConfig config) {
            this.config = config;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (row == 5 || row == 6) {
                String valueStr = value.toString();
                if (valueStr.contains("-")) {
                    c.setForeground(config.lossAmountColor());
                } else if (!valueStr.equals("0%")) {
                    c.setForeground(config.profitAmountColor());
                } else {
                    c.setForeground(table.getForeground());
                }
            } else {
                c.setForeground(table.getForeground());
            }
            return c;
        }
    }
}
