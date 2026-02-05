package com.flippingcopilot.ui.graph;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.manager.PriceGraphConfigManager;
import com.flippingcopilot.model.FlipV2;
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
public class FlipStatsPanel extends JPanel {
    private final JTable statsTable;
    private final JLabel itemIcon = new JLabel();
    private final JLabel itemNameLabel = new JLabel();
    private final FlippingCopilotConfig copilotConfig;

    public FlipStatsPanel(PriceGraphConfigManager configManager, FlippingCopilotConfig copilotConfig) {
        this.copilotConfig = copilotConfig;
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
        model.addRow(new Object[]{"First buy time", ""});
        model.addRow(new Object[]{"Last sell time", ""});
        model.addRow(new Object[]{"Status", ""});
        model.addRow(new Object[]{"Bought", ""});
        model.addRow(new Object[]{"Sold", ""});
        model.addRow(new Object[]{"Avg. buy price", ""});
        model.addRow(new Object[]{"Avg. sell price", ""});
        model.addRow(new Object[]{"Tax", ""});
        model.addRow(new Object[]{"Profit", ""});
        model.addRow(new Object[]{"Profit ea.", ""});

        statsTable = new JTable(model);
        statsTable.setRowHeight(26);
        statsTable.getTableHeader().setReorderingAllowed(false);
        statsTable.getTableHeader().setResizingAllowed(true);
        statsTable.setBackground(configManager.getConfig().backgroundColor);
        statsTable.setShowGrid(false);

        statsTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        statsTable.getColumnModel().getColumn(1).setPreferredWidth(120);

        statsTable.setTableHeader(null);

        // Set custom cell renderer for value column to color profit/loss
        statsTable.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                // Apply color to profit rows (Profit and Profit ea.)
                if (row == 9 || row == 10) { // Index of Profit and Profit ea. rows
                    String valueStr = value.toString();
                    // Remove formatting characters and check if negative
                    String numStr = valueStr.replace(",", "");
                    try {
                        long profitValue = Long.parseLong(numStr);
                        if (profitValue < 0) {
                            c.setForeground(copilotConfig.lossAmountColor());
                        } else if (profitValue > 0) {
                            c.setForeground(copilotConfig.profitAmountColor());
                        } else {
                            c.setForeground(table.getForeground());
                        }
                    } catch (NumberFormatException e) {
                        c.setForeground(table.getForeground());
                    }
                } else {
                    c.setForeground(table.getForeground());
                }

                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(statsTable);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY),
                BorderFactory.createEmptyBorder(10, 0, 0, 0)));

        scrollPane.setColumnHeaderView(null);

        this.add(scrollPane, BorderLayout.CENTER);
        this.setPreferredSize(new Dimension(280, 450));
    }

    public void populate(FlipV2 flip, ItemController itemController) {
        itemIcon.setVisible(false);

        itemController.loadImage(flip.getItemId(), (image) -> {
            if (image != null) {
                image.addTo(itemIcon);
                itemIcon.setVisible(true);
            }
        });

        itemNameLabel.setText(itemController.getItemName(flip.getItemId()));

        DefaultTableModel model = (DefaultTableModel) statsTable.getModel();

        long profitPerItem = flip.getClosedQuantity() > 0 ?
                flip.getProfit() / flip.getClosedQuantity() : 0L;

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
    }

    private String formatNumber(long number) {
        return NumberFormat.getNumberInstance().format(number);
    }

    private String formatTimestamp(int timestamp) {
        if (timestamp == 0) {
            return "n/a";
        }
        return Constants.SECOND_DATE_FORMAT.format(new Date(timestamp * 1000L));
    }
}