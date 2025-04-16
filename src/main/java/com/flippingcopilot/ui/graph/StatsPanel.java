package com.flippingcopilot.ui.graph;

import com.flippingcopilot.ui.graph.model.Data;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Panel to display item statistics in a table format.
 */
@Slf4j
public class StatsPanel extends JPanel {
    private final Data data;
    private final JTable statsTable;

    /**
     * Constructs a new StatsPanel to display item statistics.
     *
     * @param data The data containing the item statistics
     */
    public StatsPanel(Data data) {
        this.data = data;
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
        statsTable.setBackground(Config.BACKGROUND_COLOR);

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

        // Item name
        model.addRow(new Object[]{"Item Name", data.name != null ? data.name : ""});

        // Daily volume
        model.addRow(new Object[]{"Daily Volume", formatNumber(data.dailyVolume)});

        // Last Instabuy (show even if zero)
        model.addRow(new Object[]{"Last Insta buy Price", formatNumber(data.lastInstaBuyPrice)});
        model.addRow(new Object[]{"Last Insta buy Time", data.lastInstaBuyTime > 0 ? formatTime(data.lastInstaBuyTime) : "None"});

        // Last Instasell (show even if zero)
        model.addRow(new Object[]{"Last Insta sell Price", formatNumber(data.lastInstaSellPrice)});
        model.addRow(new Object[]{"Last Insta sell Time", data.lastInstaSellTime > 0 ? formatTime(data.lastInstaSellTime) : "None"});

        // Price changes
        model.addRow(new Object[]{"Daily Price Change", formatPercentage(data.dailyPriceChange)});
        model.addRow(new Object[]{"Weekly Price Change", formatPercentage(data.weeklyPriceChange)});

        // Current margin (calculate even if values are zero)
        int margin = data.lastInstaBuyPrice - data.lastInstaSellPrice;
        float marginPercent = data.lastInstaBuyPrice > 0 ? margin * 100f / data.lastInstaBuyPrice : 0;
        model.addRow(new Object[]{"Current Margin", formatNumber(margin) + " (" + formatPercentage(marginPercent) + ")"});
    }

    /**
     * Formats a timestamp into a human-readable date/time
     *
     * @param timestamp Timestamp in milliseconds or seconds
     * @return Formatted date/time string
     */
    private String formatTime(int timestamp) {
        // Convert to milliseconds if in seconds
        long timeMs = timestamp;
        if (timeMs < 10000000000L) {
            timeMs *= 1000;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(new Date(timeMs));
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

    /**
     * Formats a float as a percentage
     *
     * @param value The value to format as percentage
     * @return Formatted percentage string
     */
    private String formatPercentage(float value) {
        NumberFormat format = NumberFormat.getPercentInstance();
        format.setMaximumFractionDigits(2);
        return format.format(value / 100);
    }
}