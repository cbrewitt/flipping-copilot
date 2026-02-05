package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.controller.ApiRequestHandler;
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
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.flippingcopilot.util.DateUtil.formatEpoch;

@Slf4j
public class FlipsPanel extends JPanel {


    private static final Integer[] PAGE_SIZE_OPTIONS = {10, 25, 50, 100, 200, 500, 1000, 2000};
    public static final NumberFormat GP_FORMAT = NumberFormat.getNumberInstance(Locale.US);
    public static final String[] COLUMN_NAMES = {
            "First buy time", "Last sell time", "Account", "Item", "Status", "Bought", "Sold",
            "Avg. buy price", "Avg. sell price", "Tax", "Profit", "Profit ea."
    };

    // dependencies
    private final FlipManager flipsManager;
    private final CopilotLoginManager copilotLoginManager;
    private final ApiRequestHandler apiRequestHandler;

    // ui components
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final Paginator paginatorPanel;
    private final Spinner spinner;
    private final JScrollPane scrollPane;
    private final JPanel spinnerOverlay;
    private final ItemSearchMultiSelect searchField;
    private final JCheckBox showOpeningFlipsCheckbox;
    private final Consumer<FlipV2> onVisualizeFlip;
    private IntervalDropdown timeIntervalDropdown;
    private AccountDropdown accountDropdown;
    private final JComboBox<Integer> pageSizeComboBox;

    // state
    private List<FlipV2> currentFlips = new ArrayList<>();
    public FlipFilterAndSort sortAndFilter;


    public FlipsPanel(FlipManager flipsManager,
                      ItemController itemController,
                      CopilotLoginManager copilotLoginManager,
                      @Named("copilotExecutor") ExecutorService executorService,
                      FlippingCopilotConfig config,
                      ApiRequestHandler apiRequestHandler,
                      Consumer<FlipV2> onVisualizeFlip) {
        this.onVisualizeFlip = onVisualizeFlip;
        this.copilotLoginManager = copilotLoginManager;
        this.apiRequestHandler = apiRequestHandler;

        // Initialize pagination first (before loadFlips is called)
        paginatorPanel = new Paginator((i) -> sortAndFilter.setPage(i));
        sortAndFilter = new FlipFilterAndSort(flipsManager, this::showFlips, paginatorPanel::setTotalPages, this::setSpinnerVisible, executorService, copilotLoginManager, itemController);
        this.flipsManager = flipsManager;
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

        showOpeningFlipsCheckbox= new JCheckBox("Show buying flips", true);
        showOpeningFlipsCheckbox.setBackground(ColorScheme.DARK_GRAY_COLOR);
        showOpeningFlipsCheckbox.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        showOpeningFlipsCheckbox.setFocusable(false);
        showOpeningFlipsCheckbox.addActionListener(e -> sortAndFilter.setIncludeBuyingFlips(showOpeningFlipsCheckbox.isSelected()));

        pageSizeComboBox = new JComboBox<>(PAGE_SIZE_OPTIONS);
        pageSizeComboBox.setSelectedItem(sortAndFilter.getPageSize());
        pageSizeComboBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        pageSizeComboBox.setFocusable(false);
        pageSizeComboBox.setToolTipText("Page size");
        pageSizeComboBox.addActionListener(e -> sortAndFilter.setPageSize((Integer) pageSizeComboBox.getSelectedItem()));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JButton downloadButton = createDownloadButton();
        buttonPanel.add(downloadButton);

        topPanel.add(leftPanel, BorderLayout.WEST);
        topPanel.add(buttonPanel, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(COLUMN_NAMES, 0) {
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

        // Disable default table sorting and set up custom header click handling
        table.setRowSorter(null);
        table.getTableHeader().setReorderingAllowed(false);
        table.setFocusable(false);


        // Add custom header click listener for sorting
        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int columnIndex = table.getTableHeader().columnAtPoint(e.getPoint());
                if (columnIndex >= 0 && columnIndex < COLUMN_NAMES.length) {
                    String clickedColumn = COLUMN_NAMES[columnIndex];

                    // Toggle sort direction if clicking the same column, otherwise default to DESC
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
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0 && row < table.getRowCount()) {
                    table.setRowSelectionInterval(row, row);
                    showFlipMenu(e, row);
                }
            }
        });

        DefaultTableCellRenderer moneyRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value instanceof Long) {
                    setText(GP_FORMAT.format((long) (Long) value));
                    setHorizontalAlignment(RIGHT);
                } else if (value instanceof String) {
                    setHorizontalAlignment(CENTER);
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
                    setText(GP_FORMAT.format(amount));
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

        table.getColumnModel().getColumn(7).setCellRenderer(moneyRenderer); // Avg. buy price
        table.getColumnModel().getColumn(8).setCellRenderer(moneyRenderer); // Avg. sell price
        table.getColumnModel().getColumn(9).setCellRenderer(moneyRenderer); // Tax
        table.getColumnModel().getColumn(10).setCellRenderer(profitRenderer); // Profit (with color)
        table.getColumnModel().getColumn(11).setCellRenderer(moneyRenderer); // Profit ea.

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        table.getColumnModel().getColumn(2).setCellRenderer(centerRenderer); // Account
        table.getColumnModel().getColumn(4).setCellRenderer(centerRenderer); // Status
        table.getColumnModel().getColumn(5).setCellRenderer(centerRenderer); // Bought
        table.getColumnModel().getColumn(6).setCellRenderer(centerRenderer); // Sold

        accountDropdown = new AccountDropdown(
                this.copilotLoginManager::displayNameToAccountIdMap,
                sortAndFilter::setAccountId,
                AccountDropdown.ALL_ACCOUNTS_DROPDOWN_OPTION
        );
        accountDropdown.setPreferredSize(new Dimension(120, accountDropdown.getPreferredSize().height));
        accountDropdown.setToolTipText("Select account");

        timeIntervalDropdown = new IntervalDropdown(sortAndFilter::setInterval, IntervalDropdown.ALL_TIME, false);
        timeIntervalDropdown.setPreferredSize(new Dimension(150, timeIntervalDropdown.getPreferredSize().height));
        timeIntervalDropdown.setToolTipText("Select time interval");


        leftPanel.add(searchField);
        leftPanel.add(Box.createRigidArea(new Dimension(3,0)));
        leftPanel.add(timeIntervalDropdown);
        leftPanel.add(Box.createRigidArea(new Dimension(3,0)));
        leftPanel.add(accountDropdown);
        leftPanel.add(Box.createRigidArea(new Dimension(3,0)));
        leftPanel.add(showOpeningFlipsCheckbox);

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
        pageSizePanel.setBorder(BorderFactory.createEmptyBorder(4,0, 0,0));
        JLabel pageSizeLabel = new JLabel("Page size:");
        pageSizePanel.add(pageSizeLabel);
        pageSizePanel.add(pageSizeComboBox);
        paginatorPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(0,0, 0,pageSizePanel.getPreferredSize().width ), paginatorPanel.getBorder()));
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

    private JButton createDownloadButton() {
        JButton button = new JButton();
        button.setToolTipText("Download as CSV");
        button.setFocusable(false);
        button.setText("Download");
        button.addActionListener(e -> downloadAsCSV());
        return button;
    }

    private void showFlipMenu(MouseEvent e, int row) {
        FlipV2 flip = currentFlips.get(row);

        JPopupMenu menu = new JPopupMenu();
        JMenuItem visualizeFlip = new JMenuItem("Visualize flip");
        visualizeFlip.addActionListener(evt -> {
            onVisualizeFlip.accept(flip);
        });
        menu.add(visualizeFlip);
        
        JMenuItem deleteItem = new JMenuItem("Delete flip");
        deleteItem.addActionListener(evt -> {
            int result = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to delete this flip?",
                    "Confirm Delete",
                    JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                setSpinnerVisible(true);
                log.info("deleting flip with ID: {}", flip.getId());
                Consumer<FlipV2> onSuccess = (f) -> {
                    flipsManager.mergeFlips(Collections.singletonList(f),copilotLoginManager.getCopilotUserId());
                    setSpinnerVisible(false);
                    sortAndFilter.reloadFlips(true, true);
                };
                apiRequestHandler.asyncDeleteFlip(flip, onSuccess, () -> setSpinnerVisible(false));
            }
        });
        menu.add(deleteItem);

        String displayName = copilotLoginManager.getDisplayName(flip.getAccountId());
        if (displayName != null && !FlipStatus.FINISHED.equals(flip.getStatus())) {
            JMenuItem missedSellTransaction = new JMenuItem("Add missed sell transaction");
            missedSellTransaction.addActionListener(evt -> {
                int qty = flip.getOpenedQuantity() - flip.getClosedQuantity();
                int suggestedPrice = (int) (flip.getAvgBuyPrice() * 1.02);

                JPanel dialogPanel = new JPanel(new GridBagLayout());
                GridBagConstraints gbc = new GridBagConstraints();
                gbc.insets = new Insets(5, 5, 5, 5);
                gbc.anchor = GridBagConstraints.WEST;
                gbc.gridx = 0; gbc.gridy = 0;
                dialogPanel.add(new JLabel("Item:"), gbc);
                gbc.gridx = 1;
                dialogPanel.add(new JLabel(flip.getCachedItemName()), gbc);
                gbc.gridx = 0; gbc.gridy = 1;
                dialogPanel.add(new JLabel("Quantity:"), gbc);
                gbc.gridx = 1;
                dialogPanel.add(new JLabel(String.valueOf(qty)), gbc);
                gbc.gridx = 0; gbc.gridy = 2;
                dialogPanel.add(new JLabel("Sell Price:"), gbc);
                gbc.gridx = 1;
                JTextField priceField = new JTextField(String.valueOf(suggestedPrice), 10);
                dialogPanel.add(priceField, gbc);

                int result = JOptionPane.showConfirmDialog(this,
                        dialogPanel,
                        "Add Missed Sell Transaction",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.PLAIN_MESSAGE);

                if (result == JOptionPane.YES_OPTION) {
                    try {
                        int price = Integer.parseInt(priceField.getText().trim());
                        if (price <= 0) {
                            JOptionPane.showMessageDialog(this,
                                    "Price must be a positive number.",
                                    "Invalid Price",
                                    JOptionPane.ERROR_MESSAGE);
                            return;
                        }

                        setSpinnerVisible(true);
                        log.info("Adding missed sell transaction for flip with ID: {}", flip.getId());

                        Transaction t = new Transaction();
                        t.setId(UUID.randomUUID());
                        t.setType(OfferStatus.SELL);
                        t.setItemId(flip.getItemId());
                        t.setPrice(price);
                        t.setQuantity(qty);
                        t.setBoxId(0);
                        t.setAmountSpent(price * qty);
                        t.setTimestamp(Instant.now());
                        t.setCopilotPriceUsed(true);
                        t.setWasCopilotSuggestion(true);
                        t.setOfferTotalQuantity(qty);

                        long profit = flipsManager.estimateTransactionProfit(flip.getAccountId(), t);
 
                        if (!validateProfit(profit, flip, price)) {
                            setSpinnerVisible(false);
                            return;
                        }
 
                        BiConsumer<Integer, List<FlipV2>> onSuccess = (userId, flips) -> {
                            flipsManager.mergeFlips(flips, userId);
                            setSpinnerVisible(false);
                            sortAndFilter.reloadFlips(true, true);
                        };
                        Consumer<HttpResponseException> onFailure = (r) -> {
                            setSpinnerVisible(false);
                            JOptionPane.showMessageDialog(this,
                                    "Failed to add sell transaction. Please try again.",
                                    "Transaction Error",
                                    JOptionPane.ERROR_MESSAGE);
                        };
                        apiRequestHandler.sendTransactionsAsync(List.of(t), displayName, onSuccess, onFailure);

                    } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(this,
                                "Please enter a valid number for the price.",
                                "Invalid Price",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
            });
            menu.add(missedSellTransaction);
        }
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private boolean validateProfit(long profit, FlipV2 flip, int price) {
        long absProfit = Math.abs(profit);
        long avgBuyPrice = flip.getAvgBuyPrice();
        if (absProfit > 10_000_000L || (avgBuyPrice > 0 && price > avgBuyPrice * 5L)) {
            JOptionPane.showMessageDialog(this,
                    "The estimated profit/loss (" + GP_FORMAT.format(absProfit) + " gp) is too large. " +
                            "Please double-check the sell price.",
                    "Profit Too Large",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    private void downloadAsCSV() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File("flips.csv"));
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try (FileWriter writer = new FileWriter(file)) {
                sortAndFilter.writeCsvRecords(writer);
                JOptionPane.showMessageDialog(this, "Flips exported successfully!",
                        "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                log.error("Error exporting flips", ex);
                JOptionPane.showMessageDialog(this, "Error exporting flips: " + ex.getMessage(),
                        "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showFlips(List<FlipV2> flips) {
        SwingUtilities.invokeLater(() -> {
            currentFlips = flips;
            tableModel.setRowCount(0);
            Map<Integer, String> accountIdToDisplayName = copilotLoginManager.accountIDToDisplayNameMap();
            for (FlipV2 flip : flips) {
                long profitPerItem = flip.getClosedQuantity() > 0 ? flip.getProfit() / flip.getClosedQuantity() : 0L;

                Object[] row = {
                        formatTimestamp(flip.getOpenedTime()),
                        formatTimestamp(flip.getClosedTime()),
                        accountIdToDisplayName.getOrDefault(flip.getAccountId(), "Display name not loaded"),
                        flip.getCachedItemName(),
                        flip.getStatus().name(),
                        flip.getOpenedQuantity(),
                        flip.getClosedQuantity(),
                        flip.getSpent() / flip.getOpenedQuantity(),
                        flip.getClosedQuantity() ==0 ? 0 : (flip.getReceivedPostTax() + flip.getTaxPaid()) / flip.getClosedQuantity(),
                        flip.getTaxPaid(),
                        flip.getProfit(),
                        profitPerItem
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

    private String formatTimestamp(int epochSeconds) {
        if (epochSeconds == 0) {
            return "N/A";
        }
        return formatEpoch(epochSeconds);
    }

    public void onTabShown() {
        sortAndFilter.reloadFlips(true, true);
        accountDropdown.refresh();
    }
}