package com.flippingcopilot.ui.graph;

import com.flippingcopilot.controller.FlippingCopilotConfig;
import com.flippingcopilot.manger.PriceGraphConfigManager;
import com.flippingcopilot.ui.graph.model.Config;
import com.flippingcopilot.ui.graph.model.Constants;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.NumberFormat;
import java.util.Date;

/**
 * Panel to display item statistics in a table format.
 */
@Slf4j
public class StatsPanel extends JPanel {
    private final DataManager dataManager;
    private final JTable statsTable;
    private final FlippingCopilotConfig copilotConfig;

    /**
     * Constructs a new StatsPanel to display item statistics.
     *
     * @param dataManager The data containing the item statistics
     */
    public StatsPanel(DataManager dataManager, PriceGraphConfigManager configManager, FlippingCopilotConfig copilotConfig) {
        this.dataManager = dataManager;
        this.copilotConfig = copilotConfig;
        this.setLayout(new BorderLayout());

        // Create table model with two columns and no row editing
        DefaultTableModel model = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        model.addColumn("Statistic");
        model.addColumn("Value");

        // Create the table
        statsTable = new JTable(model);
        statsTable.setFillsViewportHeight(true);
        statsTable.setRowHeight(24);
        statsTable.getTableHeader().setReorderingAllowed(false);
        statsTable.getTableHeader().setResizingAllowed(true);
        statsTable.setBackground(configManager.getConfig().backgroundColor);

        // Remove cell borders
        statsTable.setShowGrid(false);
        statsTable.setIntercellSpacing(new Dimension(0, 0));

        // Set column widths
        statsTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        statsTable.getColumnModel().getColumn(1).setPreferredWidth(120);

        // Hide the default table header
        statsTable.setTableHeader(null);

        // Set custom cell renderer for value column to color the change percentages
        statsTable.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                // Apply color to the price change rows (24h change and week change)
                if (row == 5 || row == 6) { // Index of 24h change and week change rows
                    String valueStr = value.toString();
                    // Check if the percentage is negative (contains '-' character)
                    if (valueStr.contains("-")) {
                        c.setForeground(copilotConfig.lossAmountColor());
                    } else if (!valueStr.equals("0%")) {
                        c.setForeground(copilotConfig.profitAmountColor());
                    } else {
                        c.setForeground(table.getForeground()); // Default color for 0%
                    }
                } else {
                    c.setForeground(table.getForeground()); // Default color for other rows
                }

                return c;
            }
        });

        // Add a single top border to the table
        statsTable.setBorder(new MatteBorder(1, 0, 0, 0, Color.GRAY));

        // Add table to scroll pane
        JScrollPane scrollPane = new JScrollPane(statsTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        // Remove the default column header view
        scrollPane.setColumnHeaderView(null);

        this.add(scrollPane, BorderLayout.CENTER);

        // Populate table with data
        populateTable();

        // Set preferred size for the panel
        this.setPreferredSize(new Dimension(280, 400));
    }

    /**
     * Populates the stats table with data
     */
    private void populateTable() {
        DefaultTableModel model = (DefaultTableModel) statsTable.getModel();

        // Daily volume
        model.addRow(new Object[]{"Daily Volume", formatNumber((long) dataManager.data.dailyVolume)});

        // Last Instabuy (show even if zero)
        model.addRow(new Object[]{"Last low time", dataManager.lastLowTime > 0 ? Constants.SECOND_DATE_FORMAT.format(new Date(dataManager.lastLowTime* 1000L)) : "n/a"});
        model.addRow(new Object[]{"Last low price", dataManager.lastLowPrice > 0 ? formatNumber(dataManager.lastLowPrice) : "n/a"});

        // Last Instasell (show even if zero)
        model.addRow(new Object[]{"Last high time", dataManager.lastHighTime > 0 ? Constants.SECOND_DATE_FORMAT.format(new Date(dataManager.lastHighTime * 1000L)) : "n/a"});
        model.addRow(new Object[]{"Last high price", dataManager.lastHighPrice > 0 ? formatNumber(dataManager.lastHighPrice) : "n/a"});

        // Price changes
        model.addRow(new Object[]{"24h change", formatPercentage((float) dataManager.priceChange24H)});
        model.addRow(new Object[]{"Week change", formatPercentage((float) dataManager.priceChangeWeek)});
//
//        // Copilot price and margin
//        model.addRow(new Object[]{"Copilot buy price", formatNumber(dataManager.data.buyPrice)});
//        model.addRow(new Object[]{"Copilot sell price", formatNumber(dataManager.data.sellPrice)});
//
//        model.addRow(new Object[]{"Margin", formatNumber(dataManager.margin)});
//        model.addRow(new Object[]{"Tax", formatNumber(dataManager.tax)});
//        model.addRow(new Object[]{"Profit", formatNumber(dataManager.profit)});
    }

    /**
     * Formats a number with thousand separators
     *
     * @param number The number to format
     * @return Formatted number string
     */
    private String formatNumber(long number) {
        return NumberFormat.getNumberInstance().format(number);
    }

    private String formatPercentage(float value) {
        NumberFormat format = NumberFormat.getPercentInstance();
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }
}