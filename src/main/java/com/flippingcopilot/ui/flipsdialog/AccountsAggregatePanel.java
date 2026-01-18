package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.controller.ApiRequestHandler;
import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.manager.CopilotLoginManager;
import com.flippingcopilot.model.*;
import com.flippingcopilot.ui.Spinner;
import com.flippingcopilot.ui.components.IntervalDropdown;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

import javax.inject.Named;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Slf4j
public class AccountsAggregatePanel extends JPanel {

    private static final NumberFormat GP_FORMAT = NumberFormat.getNumberInstance(Locale.US);
    // dependencies
    private final CopilotLoginManager copilotLoginManager;
    private final ApiRequestHandler apiRequestHandler;
    private final FlipManager flipManager;
    private final ExecutorService executorService;

    // ui components
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final Spinner spinner;
    private final JScrollPane scrollPane;
    private IntervalDropdown timeIntervalDropdown;

    // state
    private AccountsAggregateFilterSort sortAndFilter;
    private List<AccountAggregate> currentItems;

    private final String[] columnNames = {
            "Account", "Number of flips", "Biggest loss", "Biggest win", "Total profit"
    };
    private JPanel spinnerOverlay;

    public AccountsAggregatePanel(FlipManager flipsManager,
                                  CopilotLoginManager copilotLoginManager,
                                  @Named("copilotExecutor") ExecutorService executorService,
                                  FlippingCopilotConfig config, ApiRequestHandler apiRequestHandler, FlipManager flipManager) {
        this.copilotLoginManager = copilotLoginManager;
        this.apiRequestHandler = apiRequestHandler;
        this.flipManager = flipManager;
        this.executorService = executorService;

        // Initialize sort and filter
        sortAndFilter = new AccountsAggregateFilterSort(flipsManager, copilotLoginManager,
                this::showAggregates, this::setSpinnerVisible, executorService);

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Create top panel with all controls
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        timeIntervalDropdown = new IntervalDropdown(sortAndFilter::setInterval, IntervalDropdown.ALL_TIME, false);
        timeIntervalDropdown.setPreferredSize(new Dimension(150, timeIntervalDropdown.getPreferredSize().height));
        timeIntervalDropdown.setToolTipText("Select time interval");

        leftPanel.add(timeIntervalDropdown);

        topPanel.add(leftPanel, BorderLayout.WEST);
        add(topPanel, BorderLayout.NORTH);

        // Create table model
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        // Create table
        table = new JTable(tableModel);
        table.setBackground(ColorScheme.DARK_GRAY_COLOR);
        table.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        table.setSelectionBackground(ColorScheme.BRAND_ORANGE);
        table.setSelectionForeground(Color.WHITE);
        table.setGridColor(ColorScheme.MEDIUM_GRAY_COLOR);
        table.setRowHeight(25);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        // Enable built-in table sorting
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);
        table.getTableHeader().setReorderingAllowed(false);
        table.setFocusable(false);

        // Custom renderer for money columns
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

        // Custom renderer for profit columns (with color)
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

        // Center align for count column
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);

        // Apply renderers
        table.getColumnModel().getColumn(1).setCellRenderer(centerRenderer); // Number of flips
        table.getColumnModel().getColumn(2).setCellRenderer(moneyRenderer); // Biggest loss
        table.getColumnModel().getColumn(3).setCellRenderer(moneyRenderer); // Biggest win
        table.getColumnModel().getColumn(4).setCellRenderer(profitRenderer); // Total profit (with color)
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopup(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopup(e);
                }
            }

            private void showPopup(MouseEvent e) {
                int viewRow = table.rowAtPoint(e.getPoint());
                if (viewRow >= 0 && viewRow < table.getRowCount()) {
                    table.setRowSelectionInterval(viewRow, viewRow);
                    int modelRow = table.convertRowIndexToModel(viewRow);
                    showAccountMenu(e, modelRow);
                }
            }
        });

        // Create layered pane for spinner overlay
        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        layeredPane.setOpaque(true);

        // Create spinner with semi-transparent background
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
    }

    private void setSpinnerVisible(boolean visible) {
        SwingUtilities.invokeLater(() -> {
            spinnerOverlay.setVisible(visible);
        });
    }

    private void showAggregates(List<AccountAggregate> aggregates) {
        SwingUtilities.invokeLater(() -> {
            currentItems = aggregates;
            tableModel.setRowCount(0);
            for (AccountAggregate aggregate : aggregates) {
                Object[] row = {
                        aggregate.getAccountName(),
                        aggregate.getNumberOfFlips(),
                        aggregate.getBiggestLoss(),
                        aggregate.getBiggestWin(),
                        aggregate.getTotalProfit(),
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

        // Check header width
        Component comp = table.getTableHeader().getDefaultRenderer()
                .getTableCellRendererComponent(table, tableColumn.getHeaderValue(), false, false, 0, column);
        preferredWidth = Math.max(comp.getPreferredSize().width + 10, preferredWidth);

        // Check cell widths
        for (int row = 0; row < table.getRowCount(); row++) {
            comp = table.getCellRenderer(row, column)
                    .getTableCellRendererComponent(table, table.getValueAt(row, column), false, false, row, column);
            preferredWidth = Math.max(comp.getPreferredSize().width + 10, preferredWidth);
        }

        preferredWidth = Math.min(preferredWidth, maxWidth);
        tableColumn.setPreferredWidth(preferredWidth);
    }

    private void showAccountMenu(MouseEvent e, int row) {
        AccountAggregate a = currentItems.get(row);
        JPopupMenu menu = new JPopupMenu();
        JMenuItem deleteItem = new JMenuItem("Delete account");
        deleteItem.addActionListener(evt -> {
            int result = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to delete " + a.getAccountName() + "?",
                    "Confirm Delete",
                    JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                setSpinnerVisible(true);
                log.info("Deleting account: {}", a.getAccountId());
                Runnable onSuccess = () -> {
                    copilotLoginManager.removeAccount(a.getAccountId());
                    executorService.submit(() -> flipManager.deleteAccount(a.getAccountId()));
                    setSpinnerVisible(false);
                    sortAndFilter.reloadAggregates(true);
                };
                apiRequestHandler.asyncDeleteAccount(a.getAccountId(), onSuccess, () -> setSpinnerVisible(false));
            }
        });
        menu.add(deleteItem);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private String formatGp(long amount) {
        return GP_FORMAT.format(amount);
    }

    public void onTabShown() {
        sortAndFilter.reloadAggregates(true);
    }
}