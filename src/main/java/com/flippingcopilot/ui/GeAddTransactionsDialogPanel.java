package com.flippingcopilot.ui;

import com.flippingcopilot.controller.ApiRequestHandler;
import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.model.AckedTransaction;
import com.flippingcopilot.model.Transaction;
import com.flippingcopilot.model.OfferStatus;
import com.flippingcopilot.model.TransactionManager;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class GeAddTransactionsDialogPanel extends JPanel {

    private static final String CARD_LOADING = "loading";
    private static final String CARD_CONTENT = "content";
    private static final String CARD_ERROR = "error";

    private final TransactionManager transactionManager;
    private final CardLayout cardLayout;
    private final JPanel cardPanel;
    private JPanel transactionListPanel;
    private final List<TransactionCheckbox> checkboxes;
    private JButton addButton;
    private JButton cancelButton;
    private JLabel successLabel;
    private final JLabel errorCardLabel;
    private final ApiRequestHandler apiRequestHandler;
    private final ItemController itemController;
    private final String displayName;
    private final Runnable onClose;

    private List<AckedTransaction> serverTransactions;
    private List<Transaction> geTransactions;

    public GeAddTransactionsDialogPanel(
            ApiRequestHandler apiRequestHandler,
            ItemController itemController,
            String displayName,
            TransactionManager transactionManager,
            Runnable onClose,
            List<Transaction> geTransactions) {

        this.geTransactions = geTransactions;
        this.apiRequestHandler = apiRequestHandler;
        this.itemController = itemController;
        this.displayName = displayName;
        this.transactionManager = transactionManager;
        this.onClose = onClose;
        this.checkboxes = new ArrayList<>();

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setPreferredSize(new Dimension(500, 400));

        // Setup cards
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        cardPanel.add(createLoadingPanel(), CARD_LOADING);
        cardPanel.add(createContentPanel(), CARD_CONTENT);
        JPanel errorPanel = new JPanel(new GridBagLayout());
        errorPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        errorCardLabel = new JLabel();
        errorCardLabel.setForeground(Color.RED);
        errorCardLabel.setFont(FontManager.getRunescapeBoldFont());
        errorPanel.add(errorCardLabel);
        cardPanel.add(errorPanel, CARD_ERROR);
        add(cardPanel, BorderLayout.CENTER);

        // show initial card
        if (this.geTransactions == null || this.geTransactions.isEmpty()) {
            showError("Failed to extract GE transactions.");
        } else {
            cardLayout.show(cardPanel, CARD_LOADING);
            this.apiRequestHandler.asyncLoadRecentAccountTransactions(
                    this.displayName,
                    (int) (Instant.now().getEpochSecond()),
                    loadedTransactions -> SwingUtilities.invokeLater(() -> {
                        this.serverTransactions = loadedTransactions;
                        markAlreadyAdded(new ArrayList<>(serverTransactions), this.geTransactions);
                        populateTransactionList();
                        cardLayout.show(cardPanel, CARD_CONTENT);
                    }),
                    error -> SwingUtilities.invokeLater(() -> {
                        showError("Error checking server transactions: " + error);
                    })
            );
        }
    }

    private JPanel createLoadingPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 10, 0);
        Spinner spinner = new Spinner();
        spinner.show();
        panel.add(spinner, gbc);
        gbc.gridy = 1;
        JLabel loadingLabel = new JLabel("Checking recent transactions...");
        loadingLabel.setForeground(Color.WHITE);
        loadingLabel.setFont(FontManager.getRunescapeFont());
        panel.add(loadingLabel, gbc);
        return panel;
    }

    private JPanel createContentPanel() {
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        headerPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel titleLabel = new JLabel("Select transactions to add:");
        titleLabel.setFont(FontManager.getRunescapeFont());
        titleLabel.setForeground(Color.WHITE);
        headerPanel.add(titleLabel, BorderLayout.NORTH);
        contentPanel.add(headerPanel, BorderLayout.NORTH);

        transactionListPanel = new JPanel();
        transactionListPanel.setLayout(new BoxLayout(transactionListPanel, BoxLayout.Y_AXIS));
        transactionListPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JScrollPane scrollPane = new JScrollPane(transactionListPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        JLabel warningLabel = new JLabel("<html><div style='text-align: left; width: 400px;'>" +
                "Only add transactions not recorded by Copilot. It is your responsibility not to add duplicates." +
                "</div></html>");
        warningLabel.setFont(FontManager.getRunescapeFont());
        warningLabel.setForeground(Color.RED);
        warningLabel.setHorizontalAlignment(SwingConstants.CENTER);
        warningLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
        centerPanel.add(warningLabel, BorderLayout.SOUTH);

        contentPanel.add(centerPanel, BorderLayout.CENTER);

        // Button panel
        contentPanel.add(createButtonPanel(), BorderLayout.SOUTH);

        return contentPanel;
    }

    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        buttonPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        successLabel = new JLabel();
        successLabel.setForeground(Color.GREEN);
        successLabel.setFont(FontManager.getRunescapeFont());
        successLabel.setVisible(false);
        successLabel.setName("successLabel");
        buttonPanel.add(successLabel, BorderLayout.WEST);
        JPanel rightButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightButtonPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        cancelButton = new JButton("Cancel");
        cancelButton.setFont(FontManager.getRunescapeFont());
        cancelButton.addActionListener(e -> onClose.run());
        addButton = new JButton("Add Selected");
        addButton.setFont(FontManager.getRunescapeFont());
        addButton.setEnabled(false);
        addButton.addActionListener(e -> handleAddSelected());
        rightButtonPanel.add(cancelButton);
        rightButtonPanel.add(Box.createHorizontalStrut(10));
        rightButtonPanel.add(addButton);
        buttonPanel.add(rightButtonPanel, BorderLayout.EAST);
        return buttonPanel;
    }

    private void handleAddSelected() {
        List<Transaction> selectedTransactions = checkboxes.stream()
                .filter(tc -> tc.checkbox.isSelected())
                .map(tc -> tc.transaction)
                .collect(Collectors.toList());

        Collections.reverse(selectedTransactions); // ensure oldest to newest
        selectedTransactions.forEach(transaction -> {
            transactionManager.addTransaction(transaction, displayName);
            transaction.setGeTransactionAlreadyAdded(true);
        });
        successLabel.setText("Transactions queued to server");
        successLabel.setVisible(true);
        populateTransactionList();
    }

    private void showError(String errorMessage) {
        errorCardLabel.setText(errorMessage);
        cardLayout.show(cardPanel, CARD_ERROR);
    }

    private void markAlreadyAdded(List<AckedTransaction> serverTransactions, List<Transaction> geTransactions) {
        log.debug("Checking already added transactions. Server txs: {}, GE txs: {}",
                serverTransactions.size(), geTransactions.size());
        for (Transaction geTransaction : geTransactions) {
            int geQuantity = geTransaction.getType() == OfferStatus.BUY
                    ? geTransaction.getQuantity()
                    : -geTransaction.getQuantity();

            for (int i = 0; i < serverTransactions.size(); i++) {
                AckedTransaction serverTx = serverTransactions.get(i);
                if (serverTx.getItemId() == geTransaction.getItemId() &&
                        serverTx.getQuantity() == geQuantity &&
                        serverTx.getPrice() == geTransaction.getPrice()) {
                    geTransaction.setGeTransactionAlreadyAdded(true);
                    serverTransactions.remove(i);
                    break;
                }
            }
        }
    }

    private void populateTransactionList() {
        transactionListPanel.removeAll();
        checkboxes.clear();
        for (Transaction transaction : geTransactions) {
            if(!transaction.isGeTransactionAlreadyAdded()) {
                TransactionCheckbox checkbox = createTransactionRow(transaction);
                checkboxes.add(checkbox);
                transactionListPanel.add(checkbox.panel);
                transactionListPanel.add(Box.createRigidArea(new Dimension(0, 5)));
            }
        }
        transactionListPanel.revalidate();
        transactionListPanel.repaint();
    }

    private TransactionCheckbox createTransactionRow(Transaction transaction) {
        JPanel rowPanel = new JPanel(new BorderLayout());
        rowPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        rowPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR, 1),
                new EmptyBorder(5, 10, 5, 10)
        ));
        rowPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        JCheckBox checkbox = new JCheckBox();
        checkbox.setBackground(ColorScheme.DARK_GRAY_COLOR);
        checkbox.addActionListener(e -> updateAddButtonState());
        rowPanel.add(checkbox, BorderLayout.WEST);
        rowPanel.add(createTransactionDetailsPanel(transaction), BorderLayout.CENTER);
        return new TransactionCheckbox(checkbox, rowPanel, transaction);
    }

    private JPanel createTransactionDetailsPanel(Transaction transaction) {
        JPanel detailsPanel = new JPanel(new GridBagLayout());
        detailsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 10, 2, 10);

        // Item icon
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridheight = 2;
        JLabel iconLabel = new JLabel();
        itemController.loadImage(transaction.getItemId(), i -> i.addTo(iconLabel));
        detailsPanel.add(iconLabel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridheight = 1;
        gbc.weightx = 1.0;
        String itemName = itemController.getItemName(transaction.getItemId());
        JLabel nameLabel = new JLabel(itemName + " x" + transaction.getQuantity());
        nameLabel.setFont(FontManager.getRunescapeBoldFont());
        nameLabel.setForeground(Color.WHITE);
        detailsPanel.add(nameLabel, gbc);

        gbc.gridy = 1;
        String typeText = transaction.getType() == OfferStatus.BUY ? "Bought" : "Sold";
        String priceText = String.format("%s for %,d gp each", typeText, transaction.getPrice());
        JLabel priceLabel = new JLabel(priceText);
        priceLabel.setFont(FontManager.getRunescapeSmallFont());
        priceLabel.setForeground(Color.LIGHT_GRAY);
        detailsPanel.add(priceLabel, gbc);

        return detailsPanel;
    }

    private void updateAddButtonState() {
        boolean anySelected = checkboxes.stream().anyMatch(tc -> tc.checkbox.isSelected());
        addButton.setEnabled(anySelected);
    }

    @AllArgsConstructor
    private static class TransactionCheckbox {
        final JCheckBox checkbox;
        final JPanel panel;
        final Transaction transaction;
    }
}