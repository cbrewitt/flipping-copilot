package copilot.ui.graph;

import copilot.ui.UIUtilities.*;
import static javax.swing.BorderFactory.*;
import copilot.ui.*;
import copilot.config.*;
import copilot.controller.*;
import copilot.manager.*;
import copilot.ui.graph.model.Constants;
import net.runelite.client.ui.*;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.text.*;
import java.util.*;
import java.util.List;

abstract class BaseStatsPanel extends JPanel {
    protected final JTable statsTable;
    protected final JLabel itemIcon = new JLabel(), itemNameLabel = new JLabel();

    BaseStatsPanel(GraphSettings configManager, String[] rows, int height, TableCellRenderer valueRenderer) {
        setLayout(new BorderLayout());

        var iconPanel = UIUtilities.darkPanel(new FlowLayout(FlowLayout.LEFT, 5, 0), configManager.getConfig().backgroundColor);
        iconPanel.setBorder(createEmptyBorder(5, 5, 15, 0));
        itemIcon.setBorder(null);
        iconPanel.add(itemIcon);
        itemNameLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        itemNameLabel.setFont(itemNameLabel.getFont().deriveFont(Font.BOLD, 16f));
        iconPanel.add(itemNameLabel);
        add(iconPanel, BorderLayout.NORTH);

        var model = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
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
        statsTable.setBackground(configManager.getConfig().backgroundColor); statsTable.setShowGrid(false);
        statsTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        statsTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        statsTable.setTableHeader(null);
        statsTable.getColumnModel().getColumn(1).setCellRenderer(valueRenderer);

        var scrollPane = new JScrollPane(statsTable);
        scrollPane.setBorder(createCompoundBorder(
                createMatteBorder(1, 0, 0, 0, Color.GRAY),
                createEmptyBorder(10, 0, 0, 0)));
        scrollPane.setColumnHeaderView(null);

        add(scrollPane, BorderLayout.CENTER);
        setPreferredSize(new Dimension(280, height));
    }

    protected void setItem(Items itemController, int itemId, boolean reloadSameItem) {
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

    protected void setValues(Object... values) {
        for (int row = 0; row < values.length; row++) { statsTable.setValueAt(values[row], row, 1); }
    }

    protected String formatNumber(long number) { return NumberFormat.getNumberInstance().format(number); }

    protected String formatTimestamp(int timestamp) {
        return timestamp == 0 ? "n/a" : Constants.SECOND_DATE_FORMAT.format(new Date(timestamp * 1000L));
    }

    /**
     * Colours the value column: percentage rows green/red unless they equal the (format specific)
     * zero literal, plain number rows green/red by sign, everything else left at the table default.
     */
    @lombok.RequiredArgsConstructor(access = lombok.AccessLevel.PACKAGE)
    static class ValueRenderer extends StyledRenderer {
        private final CopilotConfig config;
        private final String percentZero;
        private final List<Integer> percentRows, numberRows;


        @Override
        protected void style(JTable table, Object value, boolean isSelected, int row) {
            if (percentRows.contains(row)) {
                String valueStr = value.toString();
                if (valueStr.contains("-")) { setForeground(config.lossAmountColor()); } else if (!valueStr.equals(percentZero)) {
                    setForeground(config.profitAmountColor());
                } else {
                    setForeground(table.getForeground());
                }
            } else if (numberRows.contains(row)) {
                setForeground(numberColor(table, value.toString()));
            } else {
                setForeground(table.getForeground());
            }
        }

        private Color numberColor(JTable table, String valueStr) {
            try {
                long value = Long.parseLong(valueStr.replace(",", ""));
                if (value < 0) { return config.lossAmountColor(); }
                if (value > 0) { return config.profitAmountColor(); }
            } catch (NumberFormatException ignored) {
                // fall through to the default colour
            }
            return table.getForeground();
        }
    }
}
