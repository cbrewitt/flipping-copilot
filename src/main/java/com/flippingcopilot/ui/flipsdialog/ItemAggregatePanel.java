package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.manager.CopilotLoginManager;
import com.flippingcopilot.model.*;
import com.flippingcopilot.ui.Paginator;
import com.flippingcopilot.ui.Spinner;
import com.flippingcopilot.ui.components.AccountDropdown;
import com.flippingcopilot.ui.components.IntervalDropdown;
import com.flippingcopilot.ui.components.ItemSearchMultiSelect;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

import javax.inject.Named;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Slf4j
public class ItemAggregatePanel extends JPanel {

    private static final Integer[] PAGE_SIZE_OPTIONS = {10, 25, 50, 100, 200, 500, 1000, 2000};
    private static final NumberFormat GP_FORMAT = NumberFormat.getNumberInstance(Locale.US);

    // dependencies
    private final CopilotLoginManager copilotLoginManager;

    // ui components
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final Paginator paginatorPanel;
    private final Spinner spinner;
    private final JScrollPane scrollPane;
    private ItemSearchMultiSelect searchField;
    private IntervalDropdown timeIntervalDropdown;
    private AccountDropdown accountDropdown;
    private JComboBox<Integer> pageSizeComboBox;

    // state
    private ItemAggregateFilterSort sortAndFilter;

    private final String[] columnNames = {
            "Item", "Number of flips", "Total quantity flipped", "Biggest loss", "Biggest win", "Total profit", "Avg profit", "Avg profit ea."
    };
    private JPanel spinnerOverlay;

    public ItemAggregatePanel(FlipManager flipsManager,
                              ItemController itemController,
                              CopilotLoginManager copilotLoginManager,
                              @Named("copilotExecutor") ExecutorService executorService,
                              FlippingCopilotConfig config) {
        this.copilotLoginManager = copilotLoginManager;
        setFocusable(true);

        paginatorPanel = new Paginator((i) -> sortAndFilter.setPage(i));
        sortAndFilter = new ItemAggregateFilterSort(flipsManager, itemController, this::showAggregates,
                paginatorPanel::setTotalPages, this::setSpinnerVisible, executorService);

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        searchField = new ItemSearchMultiSelect(
                sortAndFilter::getFilteredItems,
                itemController::allItemIds,
                itemController::search,
                sortAndFilter::setFilteredItems,
                "Items filter...",
                SwingUtilities.getWindowAncestor(this));
        searchField.setMinimumSize(new Dimension(300, 0));
        searchField.setToolTipText("Search by item name");

        pageSizeComboBox = new JComboBox<>(PAGE_SIZE_OPTIONS);
        pageSizeComboBox.setSelectedItem(sortAndFilter.getPageSize());
        pageSizeComboBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        pageSizeComboBox.setFocusable(false);
        pageSizeComboBox.setToolTipText("Page size");
        pageSizeComboBox.addActionListener(e -> sortAndFilter.setPageSize((Integer) pageSizeComboBox.getSelectedItem()));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        topPanel.add(leftPanel, BorderLayout.WEST);
        topPanel.add(buttonPanel, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(tableModel);
        table.setBackground(ColorScheme.DARK_GRAY_COLOR);
        table.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        table.setSelectionBackground(ColorScheme.BRAND_ORANGE);
        table.setSelectionForeground(Color.WHITE);
        table.setGridColor(ColorScheme.MEDIUM_GRAY_COLOR);
        table.setRowHeight(25);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        table.setRowSorter(null);
        table.getTableHeader().setReorderingAllowed(false);
        table.setFocusable(false);

        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int columnIndex = table.getTableHeader().columnAtPoint(e.getPoint());
                if (columnIndex >= 0 && columnIndex < columnNames.length) {
                    String clickedColumn = columnNames[columnIndex];

                    SortDirection newDirection = SortDirection.DESC;
                    if (clickedColumn.equals(sortAndFilter.getSortColumn())) {
                        newDirection = sortAndFilter.getSortDirection() == SortDirection.DESC ?
                                SortDirection.ASC : SortDirection.DESC;
                    }

                    sortAndFilter.setSortColumn(clickedColumn);
                    sortAndFilter.setSortDirection(newDirection);
                }
            }
        });

        DefaultTableCellRenderer moneyRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value instanceof Long) {
                    setText(formatGp((Long) value));
                    setHorizontalAlignment(RIGHT);
                }
                return c;
            }
        };

        DefaultTableCellRenderer profitRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value instanceof Long) {
                    long amount = (Long) value;
                    setText(formatGp(amount));
                    setHorizontalAlignment(RIGHT);

                    // Color profit/loss only if not selected
                    if (!isSelected) {
                        if (amount > 0) {
                            setForeground(config.profitAmountColor());
                        } else if (amount < 0) {
                            setForeground(config.lossAmountColor());
                        } else {
                            setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                        }
                    }
                }
                return c;
            }
        };

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);

        table.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(2).setCellRenderer(moneyRenderer);
        table.getColumnModel().getColumn(3).setCellRenderer(moneyRenderer);
        table.getColumnModel().getColumn(4).setCellRenderer(profitRenderer);
        table.getColumnModel().getColumn(5).setCellRenderer(profitRenderer);
        table.getColumnModel().getColumn(6).setCellRenderer(profitRenderer);
        table.getColumnModel().getColumn(7).setCellRenderer(profitRenderer);

        setupDropdowns();

        leftPanel.add(searchField);
        leftPanel.add(Box.createRigidArea(new Dimension(3, 0)));
        leftPanel.add(timeIntervalDropdown);
        leftPanel.add(Box.createRigidArea(new Dimension(3, 0)));
        leftPanel.add(accountDropdown);

        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        layeredPane.setOpaque(true);

        spinner = new Spinner();
        spinner.show();
        spinnerOverlay = new JPanel(new GridBagLayout());
        spinnerOverlay.setBackground(ColorScheme.DARK_GRAY_COLOR);
        spinnerOverlay.setOpaque(true);
        spinnerOverlay.add(spinner);
        spinnerOverlay.setVisible(false); // Initially hidden

        scrollPane = new JScrollPane(table);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);

        layeredPane.setLayout(new OverlayLayout(layeredPane));
        layeredPane.add(spinnerOverlay, JLayeredPane.MODAL_LAYER);
        layeredPane.add(scrollPane, JLayeredPane.DEFAULT_LAYER);

        add(layeredPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JPanel pageSizePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        pageSizePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        pageSizePanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        JLabel pageSizeLabel = new JLabel("Page size:");
        pageSizePanel.add(pageSizeLabel);
        pageSizePanel.add(pageSizeComboBox);
        paginatorPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 0, 0, pageSizePanel.getPreferredSize().width),
                paginatorPanel.getBorder()));
        bottomPanel.add(pageSizePanel, BorderLayout.WEST);
        bottomPanel.add(paginatorPanel, BorderLayout.CENTER);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void setSpinnerVisible(boolean visible) {
        SwingUtilities.invokeLater(() -> {
            if (visible) {
                spinnerOverlay.setVisible(true);
                table.setEnabled(false);
            } else {
                spinnerOverlay.setVisible(false);
                table.setEnabled(true);
            }
        });
    }

    private void setupDropdowns() {
        accountDropdown = new AccountDropdown(
                copilotLoginManager::displayNameToAccountIdMap,
                sortAndFilter::setAccountId,
                AccountDropdown.ALL_ACCOUNTS_DROPDOWN_OPTION
        );
        accountDropdown.setPreferredSize(new Dimension(120, accountDropdown.getPreferredSize().height));
        accountDropdown.setToolTipText("Select account");
        accountDropdown.refresh();

        timeIntervalDropdown = new IntervalDropdown(sortAndFilter::setInterval, IntervalDropdown.ALL_TIME, false);
        timeIntervalDropdown.setPreferredSize(new Dimension(150, timeIntervalDropdown.getPreferredSize().height));
        timeIntervalDropdown.setToolTipText("Select time interval");
    }

    private void showAggregates(List<ItemAggregate> aggregates) {
        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0);
            for (ItemAggregate aggregate : aggregates) {
                Object[] row = {
                        aggregate.getItemName(),
                        aggregate.getNumberOfFlips(),
                        aggregate.getTotalQuantityFlipped(),
                        aggregate.getBiggestLoss(),
                        aggregate.getBiggestWin(),
                        aggregate.getTotalProfit(),
                        aggregate.getAvgProfit(),
                        aggregate.getAvgProfitEa()
                };
                tableModel.addRow(row);
            }

            for (int i = 0; i < table.getColumnCount(); i++) {
                resizeColumnWidth(table, i);
            }
        });
    }

    private void resizeColumnWidth(JTable table, int column) {
        TableColumn tableColumn = table.getColumnModel().getColumn(column);
        int preferredWidth = tableColumn.getMinWidth();
        int maxWidth = tableColumn.getMaxWidth();

        Component comp = table.getTableHeader().getDefaultRenderer()
                .getTableCellRendererComponent(table, tableColumn.getHeaderValue(), false, false, 0, column);
        preferredWidth = Math.max(comp.getPreferredSize().width + 10, preferredWidth);

        for (int row = 0; row < table.getRowCount(); row++) {
            comp = table.getCellRenderer(row, column)
                    .getTableCellRendererComponent(table, table.getValueAt(row, column), false, false, row, column);
            preferredWidth = Math.max(comp.getPreferredSize().width + 10, preferredWidth);
        }

        preferredWidth = Math.min(preferredWidth, maxWidth);
        tableColumn.setPreferredWidth(preferredWidth);
    }

    private String formatGp(long amount) {
        return GP_FORMAT.format(amount);
    }

    public void onTabShown() {
        sortAndFilter.reloadAggregates(true);
        accountDropdown.refresh();
    }
}