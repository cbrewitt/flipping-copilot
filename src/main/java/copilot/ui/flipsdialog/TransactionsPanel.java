package copilot.ui.flipsdialog;
import static javax.swing.JOptionPane.*;
import static javax.swing.SwingUtilities.*;

import copilot.ui.UIUtilities.*;
import java.util.function.*;
import copilot.ui.components.*;
import copilot.ui.*;
import copilot.controller.*;
import static net.runelite.client.ui.ColorScheme.*;
import copilot.config.*;
import copilot.model.*;
import copilot.rs.*;
import copilot.util.*;
import joptsimple.internal.*;
import lombok.extern.slf4j.*;

import javax.inject.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static copilot.ui.flipsdialog.FlipFilterAndSort.escapeCSV;
import static copilot.ui.flipsdialog.FlipFilterAndSort.formatTimestampISO;
import static copilot.util.DateUtil.formatEpoch;
import java.util.List;

@Slf4j
public class TransactionsPanel extends JPanel {

    private static final UUID ZERO_UUID = new UUID(0L, 0L);
    private static final int DEFAULT_PAGE_SIZE = 200;
    private static final String[] COLUMN_NAMES = {
            "Timestamp", "Account", "Side", "Item", "Quantity", "Paid/Received", "Tax", "Price ea.", "Part of Flip"
    };

    // dependencies
    private final CopilotLogin copilotLogin;
    private final Items itemController;
    private final ExecutorService executor;
    private final ApiClient api;
    private final PlayerLogin login;
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
    private volatile int pageSize = DEFAULT_PAGE_SIZE, currentPage = 1;
    private volatile Integer selectedAccountId;

    public TransactionsPanel(CopilotLogin copilotLogin,
                             Items itemController,
                             @Named("copilotExecutor") ExecutorService executor,
                             ApiClient api,
                             PlayerLogin login,
                             CopilotConfig config,
                             FlipManager flipManager) {
        this.copilotLogin = copilotLogin; this.itemController = itemController; this.executor = executor;
        this.api = api; this.login = login; this.flipManager = flipManager;

        setLayout(new BorderLayout());
        setBackground(DARK_GRAY_COLOR);

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

        tablePanel.installPageFooter(paginatorPanel, pageSize, newPageSize -> {
            if (newPageSize != pageSize) {
                pageSize = newPageSize;
                currentPage = 1;
                paginatorPanel.setPageNumber(1);
                applyFilters(true);
            }
        });

        add(tablePanel, BorderLayout.CENTER);
    }

    public void loadTransactionsIfNeeded() {
        if (!canLoadForCurrentPlayer()) {
            showLoginPrompt();
            return;
        }
        if (loadTransactionsTriggered.compareAndSet(false, true)) { loadTransactions(); }
    }

    private void setupControls() {
        // Create left panel with dropdowns
        var searchField = ItemSearchMultiSelect.itemsFilter(this, itemController,
                () -> new HashSet<>(filteredItems),
                items -> {
                    if (!Objects.equals(items, filteredItems)) {
                        filteredItems = new HashSet<>(items);
                        currentPage = 1;
                        paginatorPanel.setPageNumber(1);
                        applyFilters(true);
                    }
                });

        // Account dropdown
        accountDropdown = DialogUi.accountDropdown(
                () -> copilotLogin.get().displayNameToAccountId,
                accountId -> {
                    if (!Objects.equals(accountId, selectedAccountId)) {
                        currentPage = 1;
                        paginatorPanel.setPageNumber(currentPage);
                        selectedAccountId = accountId;
                        applyFilters(true);
                    }
                });
        accountDropdown.refresh();

        tablePanel.leftControls().add(searchField);
        UIUtilities.addHorizontalGap(tablePanel.leftControls(), 3);
        tablePanel.leftControls().add(accountDropdown);

        var refreshButton = new JButton("Refresh");
        refreshButton.setBackground(DARKER_GRAY_COLOR); refreshButton.setFocusable(false);
        refreshButton.setToolTipText("Refresh transactions");
        refreshButton.addActionListener(e -> {
            loadTransactionsTriggered.set(false);
            loadTransactionsIfNeeded();
        });

        // Create right panel with download button
        var downloadButton = new JButton("Download");
        downloadButton.setBackground(DARKER_GRAY_COLOR); downloadButton.setFocusable(false);
        downloadButton.setToolTipText("Download transactions as CSV");
        downloadButton.addActionListener(e -> downloadTransactionsCSV());

        tablePanel.rightControls().add(refreshButton);
        UIUtilities.addHorizontalGap(tablePanel.rightControls(), 5);
        tablePanel.rightControls().add(downloadButton);
    }

    private void setupTable(CopilotConfig config) {
        // Create table
        tablePanel.installPopupHandler(this::showTransactionMenu);

        // Setup renderers
        var booleanRenderer = new StyledRenderer() {
            @Override
            protected void style(JTable table, Object value, boolean isSelected, int row) {
                if (value instanceof Boolean) {
                    setText((Boolean) value ? "Yes" : "No");
                    setHorizontalAlignment(CENTER);
                }
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
        errorLabel.setFont(errorLabel.getFont().deriveFont(14f)); errorLabel.setVisible(false);
        tablePanel.addOverlay(errorLabel, JLayeredPane.PALETTE_LAYER);
    }

    private void showLoginPrompt() {
        setSpinnerVisible(false);
        errorLabel.setText("Log into the game to view account transactions"); errorLabel.setVisible(true);
    }

    private void loadTransactions() {
        String displayName = login.getPlayerDisplayName();
        if (!login.isValidLoginState() || Strings.isNullOrEmpty(displayName)) {
            showLoginPrompt();
            return;
        }
        setSpinnerVisible(true);
        errorLabel.setVisible(false);
        api.asyncLoadTransactionsData(
                displayName,
                transactionsData -> invokeLater(() -> {
                    setSpinnerVisible(false);
                    transactionDataWrapper = new TransactionDataWrapper(transactionsData);
                    applyFilters(true);
                }),
                error -> invokeLater(() -> {
                    setSpinnerVisible(false);
                    errorLabel.setVisible(true);
                    log.error("Failed to load transactions: {}", error);
                })
        );
    }

    private boolean canLoadForCurrentPlayer() {
        return login.isValidLoginState() && !Strings.isNullOrEmpty(login.getPlayerDisplayName());
    }

    private void setSpinnerVisible(boolean visible) { tablePanel.setSpinnerVisible(visible); }

    private void applyFilters(boolean updateTotalPages) {
        executor.submit(() -> {
            synchronized (this) {
                try {
                    if (updateTotalPages) {
                        int n = transactionDataWrapper.totalRecords(filteredItems, selectedAccountId);
                        int totalPages = (int) Math.ceil((double) n / (double) pageSize);
                        paginatorPanel.setTotalPagesWithoutEffect(totalPages);
                    }
                    var txs = transactionDataWrapper.getPage(filteredItems, selectedAccountId, currentPage, pageSize);
                    tablePanel.setRows(txs);
                } catch (Exception e) {
                    errorLabel.setText("Error decoding transaction data."); errorLabel.setVisible(true);
                    log.error("loading transaction page", e);
                }
            }
        });
    }

    private Object[] toRow(AckedTransaction tx) {
        var accountIdToDisplayName = copilotLogin.get().accountIdToDisplayName;
        int absQuantity = Math.abs(tx.quantity);
        long paidReceived = Math.abs(tx.amountSpent), priceEa = tx.price;
        return new Object[]{
                formatEpoch(tx.time),
                accountIdToDisplayName.getOrDefault(tx.accountId, "Unknown"),
                tx.quantity > 0 ? "BUY" : "SELL",
                itemController.getItemName(tx.itemId),
                absQuantity,
                paidReceived,
                calculateTax(tx),
                priceEa,
                isPartOfFlip(tx),
        };
    }

    private boolean isPartOfFlip(AckedTransaction tx) {
        if (ZERO_UUID.equals(tx.clientFlipId)) { return false; }
        return !flipManager.isGhostFlip(tx.accountId, tx.clientFlipId);
    }

    private long calculateTax(AckedTransaction tx) {
        if (tx.quantity < 0) {
            long pricePerItem = tx.amountSpent / tx.quantity;
            long pricePostTax = ProfitCalculator.getPostTaxPrice(tx.itemId, pricePerItem);
            return (pricePerItem - pricePostTax) * tx.quantity;
        }
        return 0;
    }

    private void showTransactionMenu(MouseEvent e, int row) {
        var transaction = tablePanel.row(row);
        var menu = new JPopupMenu();

        if (isPartOfFlip(transaction)) {
            menu.add(transactionMenuItem(
                    transaction,
                    "Remove from flip",
                    "Are you sure you want to remove the transaction from its flip? The flip and any profit will also be updated. This operation cannot be undone.",
                    "Failed to update transaction. Please try again.",
                    "orphaning",
                    api::asyncOrphanTransaction,
                    transactionDataWrapper::update));
        }

        menu.add(transactionMenuItem(
                transaction,
                "Delete transaction",
                "Are you sure you want to delete this transaction? Any flip it is part of will also be updated.",
                "Failed to delete transaction. Please try again.",
                "deleting",
                api::asyncDeleteTransaction,
                tx -> transactionDataWrapper.deleteOne(i -> tx.id.equals(i.id))));
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private JMenuItem transactionMenuItem(AckedTransaction transaction, String label, String confirmMessage,
                                          String errorMessage, String logAction,
                                          TransactionRequest request, Consumer<AckedTransaction> localUpdate) {
        var item = new JMenuItem(label);
        item.addActionListener(evt -> {
            int result = showConfirmDialog(this, confirmMessage, "Confirm Action", YES_NO_OPTION);
            if (result == YES_OPTION) {
                loadingText.setText("");
                setSpinnerVisible(true);
                log.info("{} transaction with ID: {}", logAction, transaction.id);

                BiConsumer<Integer, List<Flip>> onSuccess = (userId, flips) -> {
                    flipManager.mergeFlips(flips, userId);
                    setSpinnerVisible(false);
                    transaction.setClientFlipId(ZERO_UUID);
                    localUpdate.accept(transaction);
                    applyFilters(false);
                };

                Runnable onFailure = () -> {
                    setSpinnerVisible(false);
                    showMessageDialog(this, errorMessage, "Error", ERROR_MESSAGE);
                };

                request.send(transaction, onSuccess, onFailure);
            }
        });
        return item;
    }

    @FunctionalInterface
    private interface TransactionRequest {
        void send(AckedTransaction transaction, BiConsumer<Integer, List<Flip>> onSuccess, Runnable onFailure);
    }

    private void downloadTransactionsCSV() {
        if (transactionDataWrapper == null) {
            showMessageDialog(this, "No transaction data available to download.", "Download Error", WARNING_MESSAGE);
            return;
        }

        var fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Transactions CSV"); fileChooser.setSelectedFile(new File("transactions.csv"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            executor.submit(() -> {
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write(Strings.join(COLUMN_NAMES, ","));

                    // Use the stream method to write all matching transactions
                    var accountIdToDisplayName = copilotLogin.get().accountIdToDisplayName;
                    transactionDataWrapper.stream(filteredItems, selectedAccountId)
                            .forEach(tx -> {
                                try {
                                    int absQuantity = Math.abs(tx.quantity);
                                    long paidReceived = Math.abs(tx.amountSpent);
                                    long priceEa = tx.price, tax = calculateTax(tx);
                                    String row = String.join(",",
                                            formatTimestampISO(tx.time),
                                            escapeCSV(accountIdToDisplayName.getOrDefault(tx.accountId, "Unknown")),
                                            tx.quantity > 0 ? "BUY" : "SELL",
                                            escapeCSV(itemController.getItemName(tx.itemId)),
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

                    invokeLater(() -> showMessageDialog(this,
                            "Transactions exported successfully to " + file.getName(),
                            "Export Complete",
                            INFORMATION_MESSAGE));
                } catch (Exception e) {
                    invokeLater(() -> showMessageDialog(this,
                            "Error exporting transactions: " + e.getMessage(),
                            "Export Error",
                            ERROR_MESSAGE));
                    log.error("Error exporting transactions to CSV", e);
                }
            });
        }
    }
}
