package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.controller.ApiRequestHandler;
import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.model.*;
import com.flippingcopilot.rs.CopilotLoginRS;
import com.flippingcopilot.ui.Paginator;
import com.flippingcopilot.ui.components.AccountDropdown;
import com.flippingcopilot.ui.components.ItemSearchMultiSelect;
import com.flippingcopilot.util.ProfitCalculator;
import joptsimple.internal.Strings;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

import javax.inject.Named;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.flippingcopilot.ui.flipsdialog.FlipFilterAndSort.escapeCSV;
import static com.flippingcopilot.ui.flipsdialog.FlipFilterAndSort.formatTimestampISO;
import static com.flippingcopilot.util.DateUtil.formatEpoch;

@Slf4j
public class TransactionsPanel extends JPanel {

    private static final UUID ZERO_UUID = new UUID(0L, 0L);
    private static final Integer[] PAGE_SIZE_OPTIONS = {10, 25, 50, 100, 200, 500, 1000, 2000};
    private static final int DEFAULT_PAGE_SIZE = 200;
    private static final String[] COLUMN_NAMES = {
            "Timestamp", "Account", "Side", "Item", "Quantity", "Paid/Received", "Tax", "Price ea.", "Part of Flip"
    };

    // dependencies
    private final CopilotLoginRS copilotLoginRS;
    private final ItemController itemController;
    private final ExecutorService executorService;
    private final ApiRequestHandler apiRequestHandler;
    private final OsrsLoginManager osrsLoginManager;
    private final FlipManager flipManager;

    // ui components
    private final Paginator paginatorPanel;
    private final PaginatedTablePanel<AckedTransaction> tablePanel;
    private final AtomicBoolean loadTransactionsTriggered = new AtomicBoolean(false);
    private final JLabel loadingText;

    private AccountDropdown accountDropdown;
    private JLabel errorLabel;

    // state
    private TransactionDataWrapper transactionDataWrapper;
    private volatile Set<Integer> filteredItems = new HashSet<>();
    private volatile int pageSize = DEFAULT_PAGE_SIZE;
    private volatile int currentPage = 1;
    private volatile Integer selectedAccountId;

    public TransactionsPanel(CopilotLoginRS copilotLoginRS,
                             ItemController itemController,
                             @Named("copilotExecutor") ExecutorService executorService,
                             ApiRequestHandler apiRequestHandler,
                             OsrsLoginManager osrsLoginManager,
                             FlippingCopilotConfig config,
                             FlipManager flipManager) {
        this.copilotLoginRS = copilotLoginRS;
        this.itemController = itemController;
        this.executorService = executorService;
        this.apiRequestHandler = apiRequestHandler;
        this.osrsLoginManager = osrsLoginManager;
        this.flipManager = flipManager;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        paginatorPanel = new Paginator((n) -> {
            if (n != currentPage) {
                currentPage = n;
                applyFilters(false);
            }
        });
        tablePanel = new PaginatedTablePanel<>(COLUMN_NAMES, this::toRow);
        loadingText = tablePanel.setSpinnerText("Downloading transactions..");
        setupControls();
        setupTable(config);
        setupErrorOverlay();

        // Page size combo box
        JComboBox<Integer> pageSizeComboBox = new JComboBox<>(PAGE_SIZE_OPTIONS);
        pageSizeComboBox.setSelectedItem(pageSize);
        pageSizeComboBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        pageSizeComboBox.setFocusable(false);
        pageSizeComboBox.setToolTipText("Page size");
        pageSizeComboBox.addActionListener(e -> {
            int newPageSize = (Integer) pageSizeComboBox.getSelectedItem();
            if (newPageSize != pageSize) {
                pageSize = newPageSize;
                currentPage = 1;
                paginatorPanel.setPageNumber(1);
                applyFilters(true);
            }
        });
        tablePanel.installPageFooter(paginatorPanel, pageSizeComboBox);

        add(tablePanel, BorderLayout.CENTER);
    }

    public void loadTransactionsIfNeeded() {
        if (!canLoadForCurrentPlayer()) {
            setSpinnerVisible(false);
            errorLabel.setText("Log into the game to view account transactions");
            errorLabel.setVisible(true);
            return;
        }
        if (loadTransactionsTriggered.compareAndSet(false, true)) {
            loadTransactions();
        }
    }

    private void setupControls() {
        // Create left panel with dropdowns
        ItemSearchMultiSelect searchField = new ItemSearchMultiSelect(
                () -> new HashSet<>(filteredItems),
                itemController::allItemIds,
                itemController::search,
                items -> {
                    if (!Objects.equals(items, filteredItems)) {
                        filteredItems = new HashSet<>(items);
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
                () -> copilotLoginRS.get().displayNameToAccountId,
                accountId -> {
                    if (!Objects.equals(accountId, selectedAccountId)) {
                        currentPage = 1;
                        paginatorPanel.setPageNumber(currentPage);
                        selectedAccountId = accountId;
                        applyFilters(true);
                    }
                },
                AccountDropdown.ALL_ACCOUNTS_DROPDOWN_OPTION
        );
        accountDropdown.setPreferredSize(new Dimension(120, accountDropdown.getPreferredSize().height));
        accountDropdown.setToolTipText("Select account");
        accountDropdown.refresh();

        tablePanel.leftControls().add(searchField);
        tablePanel.leftControls().add(Box.createRigidArea(new Dimension(3, 0)));
        tablePanel.leftControls().add(accountDropdown);

        JButton refreshButton = new JButton("Refresh");
        refreshButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        refreshButton.setFocusable(false);
        refreshButton.setToolTipText("Refresh transactions");
        refreshButton.addActionListener(e -> {
            loadTransactionsTriggered.set(false);
            loadTransactionsIfNeeded();
        });

        // Create right panel with download button
        JButton downloadButton = new JButton("Download");
        downloadButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        downloadButton.setFocusable(false);
        downloadButton.setToolTipText("Download transactions as CSV");
        downloadButton.addActionListener(e -> downloadTransactionsCSV());

        tablePanel.rightControls().add(refreshButton);
        tablePanel.rightControls().add(Box.createRigidArea(new Dimension(5, 0)));
        tablePanel.rightControls().add(downloadButton);
    }

    private void setupTable(FlippingCopilotConfig config) {
        // Create table
        tablePanel.installPopupHandler(this::showTransactionMenu);

        // Setup renderers
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
        tablePanel.centerColumns(1, 2, 4); // Account, Side, Quantity
        tablePanel.moneyColumns(FlipsPanel.GP_FORMAT, 5, 6, 7); // Paid/Received, Tax, Price ea.
        tablePanel.setRenderer(booleanRenderer, 8); // Part of Flip
    }

    private void setupErrorOverlay() {
        // Create error label
        errorLabel = new JLabel("Error loading transactions from server", SwingConstants.CENTER);
        errorLabel.setFont(errorLabel.getFont().deriveFont(14f));
        errorLabel.setVisible(false);
        tablePanel.addOverlay(errorLabel, JLayeredPane.PALETTE_LAYER);
    }

    private void loadTransactions() {
        if (!canLoadForCurrentPlayer()) {
            setSpinnerVisible(false);
            errorLabel.setText("Log into the game to view account transactions");
            errorLabel.setVisible(true);
            return;
        }

        String displayName = osrsLoginManager.getPlayerDisplayName();
        if (Strings.isNullOrEmpty(displayName)) {
            setSpinnerVisible(false);
            errorLabel.setText("Log into the game to view account transactions");
            errorLabel.setVisible(true);
            return;
        }

        setSpinnerVisible(true);
        errorLabel.setVisible(false);
        apiRequestHandler.asyncLoadTransactionsData(
                displayName,
                transactionsData -> SwingUtilities.invokeLater(() -> {
                    setSpinnerVisible(false);
                    transactionDataWrapper = new TransactionDataWrapper(transactionsData);
                    applyFilters(true);
                }),
                error -> SwingUtilities.invokeLater(() -> {
                    setSpinnerVisible(false);
                    errorLabel.setVisible(true);
                    log.error("Failed to load transactions: {}", error);
                })
        );
    }

    private boolean canLoadForCurrentPlayer() {
        return osrsLoginManager.isValidLoginState() && !Strings.isNullOrEmpty(osrsLoginManager.getPlayerDisplayName());
    }

    private void setSpinnerVisible(boolean visible) {
        tablePanel.setSpinnerVisible(visible);
    }

    private void applyFilters(boolean updateTotalPages) {
        executorService.submit(() -> {
            synchronized (this) {
                try {
                    if (updateTotalPages) {
                        int n = transactionDataWrapper.totalRecords(filteredItems, selectedAccountId);
                        int totalPages = (int) Math.ceil((double) n / (double) pageSize);
                        paginatorPanel.setTotalPagesWithoutEffect(totalPages);
                    }
                    List<AckedTransaction> txs = transactionDataWrapper.getPage(filteredItems, selectedAccountId, currentPage, pageSize);
                    tablePanel.setRows(txs);
                } catch (Exception e) {
                    errorLabel.setText("Error decoding transaction data.");
                    errorLabel.setVisible(true);
                    log.error("loading transaction page", e);
                }
            }
        });
    }

    private Object[] toRow(AckedTransaction tx) {
        Map<Integer, String> accountIdToDisplayName = copilotLoginRS.get().accountIdToDisplayName;
        int absQuantity = Math.abs(tx.getQuantity());
        long paidReceived = Math.abs(tx.getAmountSpent());
        long priceEa = tx.getPrice();
        return new Object[]{
                formatEpoch(tx.getTime()),
                accountIdToDisplayName.getOrDefault(tx.getAccountId(), "Unknown"),
                tx.getQuantity() > 0 ? "BUY" : "SELL",
                itemController.getItemName(tx.getItemId()),
                absQuantity,
                paidReceived,
                calculateTax(tx),
                priceEa,
                isPartOfFlip(tx),
        };
    }

    private boolean isPartOfFlip(AckedTransaction tx) {
        if (ZERO_UUID.equals(tx.getClientFlipId())) {
            return false;
        }
        return !flipManager.isGhostFlip(tx.getAccountId(), tx.getClientFlipId());
    }

    private long calculateTax(AckedTransaction tx) {
        if (tx.getQuantity() < 0) {
            int pricePerItem = tx.getAmountSpent() / tx.getQuantity();
            int pricePostTax = ProfitCalculator.getPostTaxPrice(tx.getItemId(), pricePerItem);
            return (long) (pricePerItem - pricePostTax) * tx.getQuantity();
        }
        return 0;
    }

    private void showTransactionMenu(MouseEvent e, int row) {
        AckedTransaction transaction = tablePanel.row(row);
        JPopupMenu menu = new JPopupMenu();

        if (isPartOfFlip(transaction)) {
            menu.add(transactionMenuItem(
                    transaction,
                    "Remove from flip",
                    "Are you sure you want to remove the transaction from its flip? The flip and any profit will also be updated. This operation cannot be undone.",
                    "Failed to update transaction. Please try again.",
                    "orphaning",
                    apiRequestHandler::asyncOrphanTransaction,
                    transactionDataWrapper::update));
        }

        menu.add(transactionMenuItem(
                transaction,
                "Delete transaction",
                "Are you sure you want to delete this transaction? Any flip it is part of will also be updated.",
                "Failed to delete transaction. Please try again.",
                "deleting",
                apiRequestHandler::asyncDeleteTransaction,
                tx -> transactionDataWrapper.deleteOne(i -> tx.getId().equals(i.getId()))));
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private JMenuItem transactionMenuItem(AckedTransaction transaction, String label, String confirmMessage,
                                          String errorMessage, String logAction,
                                          TransactionRequest request, Consumer<AckedTransaction> localUpdate) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(evt -> {
            int result = JOptionPane.showConfirmDialog(this,
                    confirmMessage,
                    "Confirm Action",
                    JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                loadingText.setText("");
                setSpinnerVisible(true);
                log.info("{} transaction with ID: {}", logAction, transaction.getId());

                BiConsumer<Integer, List<FlipV2>> onSuccess = (userId, flips) -> {
                    flipManager.mergeFlips(flips, userId);
                    setSpinnerVisible(false);
                    transaction.setClientFlipId(ZERO_UUID);
                    localUpdate.accept(transaction);
                    applyFilters(false);
                };

                Runnable onFailure = () -> {
                    setSpinnerVisible(false);
                    JOptionPane.showMessageDialog(this,
                            errorMessage,
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                };

                request.send(transaction, onSuccess, onFailure);
            }
        });
        return item;
    }

    @FunctionalInterface
    private interface TransactionRequest {
        void send(AckedTransaction transaction, BiConsumer<Integer, List<FlipV2>> onSuccess, Runnable onFailure);
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
                    writer.write(Strings.join(COLUMN_NAMES, ","));

                    // Use the stream method to write all matching transactions
                    Map<Integer, String> accountIdToDisplayName = copilotLoginRS.get().accountIdToDisplayName;
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
                                            isPartOfFlip(tx) ? "YES" : "NO"
                                    );
                                    writer.write("\n" + row);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });

                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                            "Transactions exported successfully to " + file.getName(),
                            "Export Complete",
                            JOptionPane.INFORMATION_MESSAGE));
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                            "Error exporting transactions: " + e.getMessage(),
                            "Export Error",
                            JOptionPane.ERROR_MESSAGE));
                    log.error("Error exporting transactions to CSV", e);
                }
            });
        }
    }
}
