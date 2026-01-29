package com.flippingcopilot.ui.graph;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.manager.PriceGraphConfigManager;
import com.flippingcopilot.ui.graph.model.Constants;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.text.NumberFormat;
import java.util.Date;

@Slf4j
public class StatsPanel extends JPanel {
    private final JTable statsTable;

    private final JLabel itemIcon = new JLabel();
    private final JLabel itemNameLabel = new JLabel();

    public StatsPanel(PriceGraphConfigManager configManager, FlippingCopilotConfig copilotConfig) {

        this.setLayout(new BorderLayout());

        JPanel iconPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        iconPanel.setBackground(configManager.getConfig().backgroundColor);
        iconPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 15, 0));
        itemIcon.setBorder(null);
        iconPanel.add(itemIcon);
        itemNameLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        itemNameLabel.setFont(itemNameLabel.getFont().deriveFont(Font.BOLD, 16f));
        iconPanel.add(itemNameLabel);


        this.add(iconPanel, BorderLayout.NORTH);

        DefaultTableModel model = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        model.addColumn("Statistic");
        model.addColumn("Value");
        model.addRow(new Object[]{"Daily Volume",""});
        model.addRow(new Object[]{"Last low time", ""});
        model.addRow(new Object[]{"Last low price", ""});
        model.addRow(new Object[]{"Last high time", ""});
        model.addRow(new Object[]{"Last high price", ""});
        model.addRow(new Object[]{"24h change", ""});
        model.addRow(new Object[]{"Week change", ""});

        statsTable = new JTable(model);
        statsTable.setRowHeight(26);
        statsTable.getTableHeader().setReorderingAllowed(false);
        statsTable.getTableHeader().setResizingAllowed(true);
        statsTable.setBackground(configManager.getConfig().backgroundColor);
        statsTable.setShowGrid(false);

        statsTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        statsTable.getColumnModel().getColumn(1).setPreferredWidth(120);

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
                        c.setForeground(table.getForeground());
                    }
                } else {
                    c.setForeground(table.getForeground());
                }

                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(statsTable);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY),
                BorderFactory.createEmptyBorder(10,0,0,0)));

        scrollPane.setColumnHeaderView(null);

        this.add(scrollPane, BorderLayout.CENTER);
        this.setPreferredSize(new Dimension(280, 400));
    }

    public void populate(DataManager dataManager, ItemController itemController) {

        String itemName = itemController.getItemName(dataManager.data.itemId);
        if(!itemName.equals(itemNameLabel.getText())) {
            itemIcon.setVisible(false);
            itemController.loadImage(dataManager.data.itemId, (image) -> {
                if (image != null) {
                    image.addTo(itemIcon);
                    itemIcon.setVisible(true);
                }
            });
            itemNameLabel.setText(itemName);
        }

        DefaultTableModel model = (DefaultTableModel) statsTable.getModel();

        model.setValueAt(formatNumber((long) dataManager.data.dailyVolume), 0, 1);
        model.setValueAt(dataManager.lastLowTime > 0 ? Constants.SECOND_DATE_FORMAT.format(new Date(dataManager.lastLowTime* 1000L)) : "n/a", 1,1);
        model.setValueAt( dataManager.lastLowPrice > 0 ? formatNumber(dataManager.lastLowPrice) : "n/a", 2,1);
        model.setValueAt( dataManager.lastHighTime > 0 ? Constants.SECOND_DATE_FORMAT.format(new Date(dataManager.lastHighTime * 1000L)) : "n/a",3,1);
        model.setValueAt(dataManager.lastHighPrice > 0 ? formatNumber(dataManager.lastHighPrice) : "n/a",4,1);
        model.setValueAt(formatPercentage((float) dataManager.priceChange24H), 5,1);
        model.setValueAt(formatPercentage((float) dataManager.priceChangeWeek), 6,1);

//        // Copilot price and margin
//        model.addRow(new Object[]{"Copilot buy price", formatNumber(dataManager.data.buyPrice)});
//        model.addRow(new Object[]{"Copilot sell price", formatNumber(dataManager.data.sellPrice)});
//
//        model.addRow(new Object[]{"Margin", formatNumber(dataManager.margin)});
//        model.addRow(new Object[]{"Tax", formatNumber(dataManager.tax)});
//        model.addRow(new Object[]{"Profit", formatNumber(dataManager.profit)});
    }


    private String formatNumber(long number) {
        return NumberFormat.getNumberInstance().format(number);
    }

    private String formatPercentage(float value) {
        NumberFormat format = NumberFormat.getPercentInstance();
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }
}