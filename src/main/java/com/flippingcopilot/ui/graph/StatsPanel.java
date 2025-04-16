package com.flippingcopilot.ui.graph;

import com.flippingcopilot.manger.PriceGraphConfigManager;
import com.flippingcopilot.ui.graph.model.Config;
import com.flippingcopilot.ui.graph.model.Constants;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
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

    /**
     * Constructs a new StatsPanel to display item statistics.
     *
     * @param dataManager The data containing the item statistics
     */
    public StatsPanel(DataManager dataManager, PriceGraphConfigManager configManager) {
        this.dataManager = dataManager;
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

        // Configure consistent cell borders matching the header style
        statsTable.setShowGrid(true);
        statsTable.setIntercellSpacing(new Dimension(1, 1));
        statsTable.setGridColor(statsTable.getTableHeader().getBackground().darker());

        // Set column widths
        statsTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        statsTable.getColumnModel().getColumn(1).setPreferredWidth(120);

        // Hide the default table header
        statsTable.setTableHeader(null);

        // Create a custom header panel
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(UIManager.getColor("TableHeader.background"));
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("TableHeader.separatorColor")),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)
        ));

        // Add the "Item statistics" label to the header panel
        JLabel headerLabel = new JLabel("Item statistics", JLabel.CENTER);
        headerLabel.setFont(UIManager.getFont("TableHeader.font"));
        headerLabel.setForeground(UIManager.getColor("TableHeader.foreground"));
        headerPanel.add(headerLabel, BorderLayout.CENTER);

        // Add table to scroll pane
        JScrollPane scrollPane = new JScrollPane(statsTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        // Remove the default column header view
        scrollPane.setColumnHeaderView(null);

        // Add the custom header and scroll pane to the panel
        this.add(headerPanel, BorderLayout.NORTH);
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

        // Current margin (calculate even if values are zero)
//        int margin = dataManager.lastInstaBuyPrice - dataManager.lastInstaSellPrice;
//        float marginPercent = dataManager.lastInstaBuyPrice > 0 ? margin * 100f / dataManager.lastInstaBuyPrice : 0;
//        model.addRow(new Object[]{"Current Margin", formatNumber(margin) + " (" + formatPercentage(marginPercent) + ")"});
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