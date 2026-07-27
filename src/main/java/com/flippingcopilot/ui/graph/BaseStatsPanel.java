package com.flippingcopilot.ui.graph;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.manager.PriceGraphConfigManager;
import com.flippingcopilot.ui.graph.model.Constants;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.text.NumberFormat;
import java.util.Date;
import java.util.List;

abstract class BaseStatsPanel extends JPanel {
    protected final JTable statsTable;
    protected final JLabel itemIcon = new JLabel();
    protected final JLabel itemNameLabel = new JLabel();

    BaseStatsPanel(PriceGraphConfigManager configManager,
                   String[] rows,
                   int height,
                   TableCellRenderer valueRenderer) {
        setLayout(new BorderLayout());

        JPanel iconPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        iconPanel.setBackground(configManager.getConfig().backgroundColor);
        iconPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 15, 0));
        itemIcon.setBorder(null);
        iconPanel.add(itemIcon);
        itemNameLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        itemNameLabel.setFont(itemNameLabel.getFont().deriveFont(Font.BOLD, 16f));
        iconPanel.add(itemNameLabel);
        add(iconPanel, BorderLayout.NORTH);

        DefaultTableModel model = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        model.addColumn("Statistic");
        model.addColumn("Value");
        for (String row : rows) {
            model.addRow(new Object[]{row, ""});
        }

        statsTable = new JTable(model);
        statsTable.setRowHeight(26);
        statsTable.getTableHeader().setReorderingAllowed(false);
        statsTable.getTableHeader().setResizingAllowed(true);
        statsTable.setBackground(configManager.getConfig().backgroundColor);
        statsTable.setShowGrid(false);
        statsTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        statsTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        statsTable.setTableHeader(null);
        statsTable.getColumnModel().getColumn(1).setCellRenderer(valueRenderer);

        JScrollPane scrollPane = new JScrollPane(statsTable);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY),
                BorderFactory.createEmptyBorder(10, 0, 0, 0)));
        scrollPane.setColumnHeaderView(null);

        add(scrollPane, BorderLayout.CENTER);
        setPreferredSize(new Dimension(280, height));
    }

    protected void setItem(ItemController itemController, int itemId, boolean reloadSameItem) {
        String itemName = itemController.getItemName(itemId);
        if (reloadSameItem || !itemName.equals(itemNameLabel.getText())) {
            itemIcon.setVisible(false);
            itemController.loadImage(itemId, image -> {
                if (image != null) {
                    image.addTo(itemIcon);
                    itemIcon.setVisible(true);
                }
            });
            itemNameLabel.setText(itemName);
        }
    }

    protected DefaultTableModel model() {
        return (DefaultTableModel) statsTable.getModel();
    }

    protected String formatNumber(long number) {
        return NumberFormat.getNumberInstance().format(number);
    }

    protected String formatTimestamp(int timestamp) {
        return timestamp == 0 ? "n/a" : Constants.SECOND_DATE_FORMAT.format(new Date(timestamp * 1000L));
    }

    /**
     * Colours the value column: percentage rows green/red unless they equal the (format specific)
     * zero literal, plain number rows green/red by sign, everything else left at the table default.
     */
    static class ValueRenderer extends DefaultTableCellRenderer {
        private final FlippingCopilotConfig config;
        private final String percentZero;
        private final List<Integer> percentRows;
        private final List<Integer> numberRows;

        ValueRenderer(FlippingCopilotConfig config, String percentZero, List<Integer> percentRows, List<Integer> numberRows) {
            this.config = config;
            this.percentZero = percentZero;
            this.percentRows = percentRows;
            this.numberRows = numberRows;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (percentRows.contains(row)) {
                String valueStr = value.toString();
                if (valueStr.contains("-")) {
                    c.setForeground(config.lossAmountColor());
                } else if (!valueStr.equals(percentZero)) {
                    c.setForeground(config.profitAmountColor());
                } else {
                    c.setForeground(table.getForeground());
                }
            } else if (numberRows.contains(row)) {
                c.setForeground(numberColor(table, value.toString()));
            } else {
                c.setForeground(table.getForeground());
            }
            return c;
        }

        private Color numberColor(JTable table, String valueStr) {
            try {
                long value = Long.parseLong(valueStr.replace(",", ""));
                if (value < 0) {
                    return config.lossAmountColor();
                }
                if (value > 0) {
                    return config.profitAmountColor();
                }
            } catch (NumberFormatException ignored) {
                // fall through to the default colour
            }
            return table.getForeground();
        }
    }
}
