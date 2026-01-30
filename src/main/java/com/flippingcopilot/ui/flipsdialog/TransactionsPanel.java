package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.controller.ApiRequestHandler;
import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.manager.CopilotLoginManager;
import com.flippingcopilot.model.*;
import com.flippingcopilot.ui.Paginator;
import com.flippingcopilot.ui.Spinner;
import com.flippingcopilot.ui.components.AccountDropdown;
import com.flippingcopilot.ui.components.ItemSearchMultiSelect;
import com.flippingcopilot.util.GeTax;
import joptsimple.internal.Strings;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

import javax.inject.Named;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import static com.flippingcopilot.ui.flipsdialog.FlipFilterAndSort.escapeCSV;
import static com.flippingcopilot.ui.flipsdialog.FlipFilterAndSort.formatTimestampISO;
import static com.flippingcopilot.util.DateUtil.formatEpoch;

@Slf4j
public class TransactionsPanel extends JPanel {

    private static final UUID ZERO_UUID = new UUID(0L, 0L);
    private static final Integer[] PAGE_SIZE_OPTIONS = {10, 25, 50, 100, 200, 500, 1000, 2000};
    private static final int DEFAULT_PAGE_SIZE = 200;

    private final String[] columnNames = {
            "Timestamp", "Account", "Side", "Item", "Quantity", "Paid/Received", "Tax", "Price ea.", "Part of Flip"
    };

    // dependencies
    private final CopilotLoginManager copilotLoginManager;
    private final ItemController itemController;
    private final ExecutorService executorService;
    private final ApiRequestHandler apiRequestHandler;
    private final FlipManager flipManager;

    // ui components
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final Paginator paginatorPanel;
    private final Spinner spinner;
    private final JScrollPane scrollPane;
    private final JPanel spinnerOverlay;
    private final ItemSearchMultiSelect searchField;
    private final JComboBox<Integer> pageSizeComboBox;
    private final JLabel loadingText;
    private AccountDropdown accountDropdown;
    private JLabel errorLabel;
    private JButton downloadButton;

    // state
    private final AtomicBoolean loadTransactionsTriggered = new AtomicBoolean(false);
    private TransactionDataWrapper transactionDataWrapper;
    private volatile Set<Integer> filteredItems = new HashSet<>();
    private volatile int pageSize = DEFAULT_PAGE_SIZE;
    private volatile int totalPages = 1;
    private volatile int currentPage = 1;
    private volatile Integer selectedAccountId;
    private volatile List<AckedTransaction> currentTransactions = new ArrayList<>();

    public TransactionsPanel(CopilotLoginManager copilotLoginManager,
                             ItemController itemController,
                             @Named("copilotExecutor") ExecutorService executorService,
                             ApiRequestHandler apiRequestHandler,
                             FlippingCopilotConfig config,
                             FlipManager flipManager) {
        this.copilotLoginManager = copilotLoginManager;
        this.itemController = itemController;
        this.executorService = executorService;
        this.apiRequestHandler = apiRequestHandler;
        this.flipManager = flipManager;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        paginatorPanel = new Paginator((n) -> {
            if(n != currentPage) {
                currentPage = n;
                applyFilters(false);
            }
        });

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Create left panel with dropdowns
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        searchField = new ItemSearchMultiSelect(
                () -> new HashSet<>(filteredItems),
                this.itemController::allItemIds,
                this.itemController::search,
                items -> {
                    if (!Objects.equals(items, this.filteredItems)) {
                        this.filteredItems = new HashSet<>(items);
                        currentPage = 1;
                        paginatorPanel.setPageNumber(1);
                        applyFilters(true);
                    }
                },
                "Items filter...",
                SwingUtilities.getWindowAncestor(this));
        searchField.setMinimumSize(new Dimension(300, 0));
        searchField.setToolTipText("Search by item name");

        // Account dropdown
        accountDropdown = new AccountDropdown(
                this.copilotLoginManager::displayNameToAccountIdMap,
                accountId -> {
                    if (!Objects.equals(accountId, this.selectedAccountId)) {
                        currentPage = 1;
                        paginatorPanel.setPageNumber(currentPage);
                        this.selectedAccountId = accountId;
                        applyFilters(true);
                    }
                },
                AccountDropdown.ALL_ACCOUNTS_DROPDOWN_OPTION
        );
        accountDropdown.setPreferredSize(new Dimension(120, accountDropdown.getPreferredSize().height));
        accountDropdown.setToolTipText("Select account");
        accountDropdown.refresh();

        // Page size combo box
        pageSizeComboBox = new JComboBox<>(PAGE_SIZE_OPTIONS);
        pageSizeComboBox.setSelectedItem(pageSize);
        pageSizeComboBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        pageSizeComboBox.setFocusable(false);
        pageSizeComboBox.setToolTipText("Page size");
        pageSizeComboBox.addActionListener(e -> {
            int newPageSize = (Integer) pageSizeComboBox.getSelectedItem();
            if (newPageSize != this.pageSize) {
                this.pageSize = newPageSize;
                currentPage = 1;
                paginatorPanel.setPageNumber(1);
                applyFilters(true);
            }
        });

        leftPanel.add(searchField);
        leftPanel.add(Box.createRigidArea(new Dimension(3, 0)));
        leftPanel.add(accountDropdown);

        // Create right panel with download button
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        downloadButton = new JButton("Download");
        downloadButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        downloadButton.setFocusable(false);
        downloadButton.setToolTipText("Download transactions as CSV");
        downloadButton.addActionListener(e -> downloadTransactionsCSV());

        JButton refreshButton = new JButton("Refresh");
        refreshButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        refreshButton.setFocusable(false);
        refreshButton.setToolTipText("Refresh transactions");
        refreshButton.addActionListener(e -> {
            loadTransactionsTriggered.set(false);
            loadTransactionsIfNeeded();
        });

        rightPanel.add(refreshButton);
        rightPanel.add(Box.createRigidArea(new Dimension(5, 0)));
        rightPanel.add(downloadButton);

        topPanel.add(leftPanel, BorderLayout.WEST);
        topPanel.add(rightPanel, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // Create table
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(tableModel);
        setupTable(config);

        // Create layered pane for spinner overlay
        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        layeredPane.setOpaque(true);

        // Create error label
        errorLabel = new JLabel("Error loading transactions from server", SwingConstants.CENTER);
        errorLabel.setFont(errorLabel.getFont().deriveFont(14f));
        errorLabel.setVisible(false);

        spinner = new Spinner();
        spinner.show();

        // Create spinner overlay with loading text
        spinnerOverlay = new JPanel(new GridBagLayout());
        spinnerOverlay.setBackground(ColorScheme.DARK_GRAY_COLOR);
        spinnerOverlay.setOpaque(true);

        // Create a panel to hold the loading text and spinner horizontally
        JPanel loadingPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        loadingPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        loadingPanel.setOpaque(false);

        // Create loading text label
        loadingText = new JLabel("Downloading transactions..");
        loadingText.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        loadingText.setFont(loadingText.getFont().deriveFont(14f));

        // Add text and spinner to the loading panel
        loadingPanel.add(loadingText);
        loadingPanel.add(spinner);

        // Add the loading panel to the spinner overlay
        spinnerOverlay.add(loadingPanel);
        spinnerOverlay.setVisible(false); // Initially hidden until first access

        scrollPane = new JScrollPane(table);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);

        layeredPane.setLayout(new OverlayLayout(layeredPane));
        layeredPane.add(spinnerOverlay, JLayeredPane.MODAL_LAYER);
        layeredPane.add(errorLabel, JLayeredPane.PALETTE_LAYER);
        layeredPane.add(scrollPane, JLayeredPane.DEFAULT_LAYER);

        add(layeredPane, BorderLayout.CENTER);

        // Create bottom panel with pagination
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JPanel pageSizePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        pageSizePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        pageSizePanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        JLabel pageSizeLabel = new JLabel("Page size:");
        pageSizeLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        pageSizePanel.add(pageSizeLabel);
        pageSizePanel.add(pageSizeComboBox);

        // Adjust paginator border to account for page size panel width
        paginatorPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 0, 0, pageSizePanel.getPreferredSize().width),
                paginatorPanel.getBorder()));

        bottomPanel.add(pageSizePanel, BorderLayout.WEST);
        bottomPanel.add(paginatorPanel, BorderLayout.CENTER);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    /**
     * Load transactions when the panel is first shown
     */
    public void loadTransactionsIfNeeded() {
        if (loadTransactionsTriggered.compareAndSet(false, true)) {
            loadTransactions();
        }
    }

    private void setupTable(FlippingCopilotConfig config) {
        table.setBackground(ColorScheme.DARK_GRAY_COLOR);
        table.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        table.setSelectionBackground(ColorScheme.BRAND_ORANGE);
        table.setSelectionForeground(Color.WHITE);
        table.setGridColor(ColorScheme.MEDIUM_GRAY_COLOR);
        table.setRowHeight(25);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setFocusable(false);

        // Disable sorting
        table.setRowSorter(null);
        table.getTableHeader().setReorderingAllowed(false);

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
                    showTransactionMenu(e, row);
                }
            }
        });

        // Setup renderers
        DefaultTableCellRenderer moneyRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value instanceof Long) {
                    setText(FlipsPanel.GP_FORMAT.format(value));
                    setHorizontalAlignment(RIGHT);
                }
                return c;
            }
        };

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);

        DefaultTableCellRenderer booleanRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value instanceof Boolean) {
                    setText((Boolean) value ? "Yes" : "No");
                    setHorizontalAlignment(CENTER);
                }
                return c;
            }
        };

        // Apply renderers to columns
        table.getColumnModel().getColumn(1).setCellRenderer(centerRenderer); // Account
        table.getColumnModel().getColumn(2).setCellRenderer(centerRenderer); // Side
        table.getColumnModel().getColumn(4).setCellRenderer(centerRenderer); // Quantity
        table.getColumnModel().getColumn(5).setCellRenderer(moneyRenderer); // Paid/Received
        table.getColumnModel().getColumn(6).setCellRenderer(moneyRenderer); // Tax
        table.getColumnModel().getColumn(7).setCellRenderer(moneyRenderer); // Price ea.
        table.getColumnModel().getColumn(8).setCellRenderer(booleanRenderer); // Part of Flip
    }

    private void loadTransactions() {
        setSpinnerVisible(true);
        errorLabel.setVisible(false);
        apiRequestHandler.asyncLoadTransactionsData(
                transactionsData -> {
                    SwingUtilities.invokeLater(() -> {
                        setSpinnerVisible(false);
                        transactionDataWrapper = new TransactionDataWrapper(transactionsData);
                        applyFilters(true);
                    });
                },
                error -> {
                    SwingUtilities.invokeLater(() -> {
                        setSpinnerVisible(false);
                        errorLabel.setVisible(true);
                        log.error("Failed to load transactions: {}", error);
                    });
                }
        );
    }

    private void setSpinnerVisible(boolean visible) {
        if (visible) {
            spinnerOverlay.setVisible(true);
            table.setEnabled(false);
        } else {
            spinnerOverlay.setVisible(false);
            table.setEnabled(true);
        }
    }

    private void applyFilters(boolean updateTotalPages) {
        executorService.submit(() -> {
            synchronized (this) {
                try {
                    if (updateTotalPages) {
                        int n = transactionDataWrapper.totalRecords(filteredItems, selectedAccountId);
                        totalPages = (int) Math.ceil((double) n / (double) pageSize);
                        paginatorPanel.setTotalPagesWithoutEffect(totalPages);
                    }
                    List<AckedTransaction> txs = transactionDataWrapper.getPage(filteredItems, selectedAccountId, currentPage, pageSize);
                    SwingUtilities.invokeLater(() -> updateTable(txs));
                } catch (Exception e) {
                    errorLabel.setText("Error decoding transaction data.");
                    errorLabel.setVisible(true);
                    log.error("loading transaction page", e);
                }
            }
        });
    }

    private void updateTable(List<AckedTransaction> txs) {
        currentTransactions = txs;
        tableModel.setRowCount(0);
        Map<Integer, String> accountIdToDisplayName = copilotLoginManager.accountIDToDisplayNameMap();

        for (AckedTransaction tx : txs) {
            int absQuantity = Math.abs(tx.getQuantity());
            long paidReceived = Math.abs(tx.getAmountSpent());
            long priceEa = tx.getPrice();
            Object[] row = {
                    formatEpoch(tx.getTime()),
                    accountIdToDisplayName.getOrDefault(tx.getAccountId(), "Unknown"),
                    tx.getQuantity() > 0 ? "BUY" : "SELL",
                    itemController.getItemName(tx.getItemId()),
                    absQuantity,
                    paidReceived,
                    calculateTax(tx),
                    priceEa,
                    !ZERO_UUID.equals(tx.getClientFlipId()),
            };
            tableModel.addRow(row);
        }
    }

    private long calculateTax(AckedTransaction tx) {
        if (tx.getQuantity() < 0) {
            int pricePerItem = tx.getAmountSpent() / tx.getQuantity();
            int pricePostTax = GeTax.getPostTaxPrice(tx.getItemId(), pricePerItem);
            return (long)(pricePerItem - pricePostTax) * tx.getQuantity();
        }
        return 0;
    }

    private void showTransactionMenu(MouseEvent e, int row) {
        if (row >= currentTransactions.size()) {
            return;
        }
        AckedTransaction transaction = currentTransactions.get(row);
        JPopupMenu menu = new JPopupMenu();

        if (!ZERO_UUID.equals(transaction.getClientFlipId())) {
            JMenuItem orphanItem = new JMenuItem("Remove from flip");
            orphanItem.addActionListener(evt -> {
                int result = JOptionPane.showConfirmDialog(this,
                        "Are you sure you want to remove the transaction from its flip? The flip and any profit will also be updated. This operation cannot be undone.",
                        "Confirm Action",
                        JOptionPane.YES_NO_OPTION);
                if (result == JOptionPane.YES_OPTION) {
                    loadingText.setText("");
                    setSpinnerVisible(true);
                    log.info("orphaning transaction with ID: {}", transaction.getId());

                    BiConsumer<Integer, List<FlipV2>> onSuccess = (userId, flips) -> {
                        flipManager.mergeFlips(flips, userId);
                        setSpinnerVisible(false);
                        transaction.setClientFlipId(ZERO_UUID);
                        transactionDataWrapper.update(transaction);
                        applyFilters(false);
                    };

                    Runnable onFailure = () -> {
                        setSpinnerVisible(false);
                        JOptionPane.showMessageDialog(this,
                                "Failed to update transaction. Please try again.",
                                "Error",
                                JOptionPane.ERROR_MESSAGE);
                    };

                    apiRequestHandler.asyncOrphanTransaction(transaction, onSuccess, onFailure);
                }
            });
            menu.add(orphanItem);
        }
        JMenuItem deleteItem = new JMenuItem("Delete transaction");
        deleteItem.addActionListener(evt -> {
            int result = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to delete this transaction? Any flip it is part of will also be updated.",
                    "Confirm Action",
                    JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                loadingText.setText("");
                setSpinnerVisible(true);
                log.info("deleting transaction with ID: {}", transaction.getId());

                BiConsumer<Integer, List<FlipV2>> onSuccess = (userId, flips) -> {
                    flipManager.mergeFlips(flips, userId);
                    setSpinnerVisible(false);
                    transaction.setClientFlipId(ZERO_UUID);
                    transactionDataWrapper.deleteOne(i -> transaction.getId().equals(i.getId()));
                    applyFilters(false);
                };

                Runnable onFailure = () -> {
                    setSpinnerVisible(false);
                    JOptionPane.showMessageDialog(this,
                            "Failed to delete transaction. Please try again.",
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                };

                apiRequestHandler.asyncDeleteTransaction(transaction, onSuccess, onFailure);
            }
        });
        menu.add(deleteItem);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private void downloadTransactionsCSV() {
        if (transactionDataWrapper == null) {
            JOptionPane.showMessageDialog(this,
                    "No transaction data available to download.",
                    "Download Error",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Transactions CSV");
        fileChooser.setSelectedFile(new File("transactions.csv"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            executorService.submit(() -> {
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write(Strings.join(columnNames, ","));

                    Map<Integer, String> accountIdToDisplayName = copilotLoginManager.accountIDToDisplayNameMap();

                    // Use the stream method to write all matching transactions
                    transactionDataWrapper.stream(filteredItems, selectedAccountId)
                            .forEach(tx -> {
                                try {
                                    int absQuantity = Math.abs(tx.getQuantity());
                                    long paidReceived = Math.abs(tx.getAmountSpent());
                                    long priceEa = tx.getPrice();
                                    long tax = calculateTax(tx);
                                    String row = String.join(",",
                                            formatTimestampISO(tx.getTime()),
                                            escapeCSV(accountIdToDisplayName.getOrDefault(tx.getAccountId(), "Unknown")),
                                            tx.getQuantity() > 0 ? "BUY" : "SELL",
                                            escapeCSV(itemController.getItemName(tx.getItemId())),
                                            String.valueOf(absQuantity),
                                            String.valueOf(paidReceived),
                                            String.valueOf(tax),
                                            String.valueOf(priceEa),
                                            !ZERO_UUID.equals(tx.getClientFlipId()) ? "YES" : "NO"
                                    );
                                    writer.write( "\n"+ row);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });

                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this,
                                "Transactions exported successfully to " + file.getName(),
                                "Export Complete",
                                JOptionPane.INFORMATION_MESSAGE);
                    });

                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this,
                                "Error exporting transactions: " + e.getMessage(),
                                "Export Error",
                                JOptionPane.ERROR_MESSAGE);
                    });
                    log.error("Error exporting transactions to CSV", e);
                }
            });
        }
    }
}