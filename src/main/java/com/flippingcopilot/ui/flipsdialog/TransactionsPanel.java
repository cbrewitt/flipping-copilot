//package com.flippingcopilot.ui.flipsdialog;
//
//import com.flippingcopilot.manager.AckedTransactionManager;
//import com.flippingcopilot.model.OfferStatus;
//import com.flippingcopilot.model.Transaction;
//import com.flippingcopilot.model.TransactionManager;
//import com.flippingcopilot.ui.Paginator;
//import com.flippingcopilot.util.GeTax;
//import lombok.extern.slf4j.Slf4j;
//import net.runelite.client.ui.ColorScheme;
//
//import javax.swing.*;
//import javax.swing.table.DefaultTableCellRenderer;
//import javax.swing.table.DefaultTableModel;
//import javax.swing.table.TableColumn;
//import javax.swing.table.TableRowSorter;
//import java.awt.*;
//import java.awt.event.MouseAdapter;
//import java.awt.event.MouseEvent;
//import java.io.File;
//import java.io.FileWriter;
//import java.io.IOException;
//import java.text.NumberFormat;
//import java.time.ZoneId;
//import java.time.format.DateTimeFormatter;
//import java.util.List;
//import java.util.Locale;
//
//@Slf4j
//public class TransactionsPanel extends JPanel {
//
//    private static final int PAGE_SIZE = 50;
//    private static final NumberFormat GP_FORMAT = NumberFormat.getNumberInstance(Locale.US);
//    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
//            .withZone(ZoneId.systemDefault());
//
//    private final AckedTransactionManager transactionManager;
//    private final DefaultTableModel tableModel;
//    private final JTable table;
//    private final Paginator paginator;
//    private List<Transaction> currentTransactions;
//
//    private final String[] columnNames = {
//            "Timestamp", "Account", "Side", "Item", "Quantity", "Paid/Received", "Tax",
//            "Price ea.", "Copilot Price", "Part of Flip"
//    };
//
//    public TransactionsPanel(AckedTransactionManager transactionManager) {
//        this.transactionManager = transactionManager;
//        setLayout(new BorderLayout());
//        setBackground(ColorScheme.DARK_GRAY_COLOR);
//
//        // Create top panel with buttons
//        JPanel topPanel = new JPanel(new BorderLayout());
//        topPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
//        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
//
//        // Create button panel
//        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
//        buttonPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
//
//        // Create add transaction button
//        JButton addTransactionButton = createAddTransactionButton();
//        buttonPanel.add(addTransactionButton);
//
//        // Create download button
//        JButton downloadButton = createDownloadButton();
//        buttonPanel.add(downloadButton);
//
//        topPanel.add(buttonPanel, BorderLayout.EAST);
//        add(topPanel, BorderLayout.NORTH);
//
//        // Create table model
//        tableModel = new DefaultTableModel(columnNames, 0) {
//            @Override
//            public boolean isCellEditable(int row, int column) {
//                return false;
//            }
//
//            @Override
//            public Class<?> getColumnClass(int columnIndex) {
//                switch (columnIndex) {
//                    case 4: // Quantity
//                        return Integer.class;
//                    case 5: case 6: case 7: // Money values
//                        return Long.class;
//                    case 8: case 9: // Boolean columns
//                        return Boolean.class;
//                    default:
//                        return String.class;
//                }
//            }
//        };
//
//        // Create table
//        table = new JTable(tableModel);
//        table.setBackground(ColorScheme.DARK_GRAY_COLOR);
//        table.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
//        table.setSelectionBackground(ColorScheme.BRAND_ORANGE);
//        table.setSelectionForeground(Color.WHITE);
//        table.setGridColor(ColorScheme.MEDIUM_GRAY_COLOR);
//        table.setRowHeight(25);
//        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
//
//        // Set up sorting
//        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
//        table.setRowSorter(sorter);
//        table.setFocusable(false);
//
//        // Add mouse listener for right-click menu
//        table.addMouseListener(new MouseAdapter() {
//            @Override
//            public void mousePressed(MouseEvent e) {
//                if (e.isPopupTrigger()) {
//                    showPopup(e);
//                }
//            }
//
//            @Override
//            public void mouseReleased(MouseEvent e) {
//                if (e.isPopupTrigger()) {
//                    showPopup(e);
//                }
//            }
//
//            private void showPopup(MouseEvent e) {
//                int row = table.rowAtPoint(e.getPoint());
//                if (row >= 0 && row < table.getRowCount()) {
//                    table.setRowSelectionInterval(row, row);
//                    showTransactionMenu(e, row);
//                }
//            }
//        });
//
//        // Custom renderer for money columns
//        DefaultTableCellRenderer moneyRenderer = new DefaultTableCellRenderer() {
//            @Override
//            public Component getTableCellRendererComponent(JTable table, Object value,
//                                                           boolean isSelected, boolean hasFocus, int row, int column) {
//                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
//                if (value instanceof Long) {
//                    setText(formatGp((Long) value));
//                    setHorizontalAlignment(RIGHT);
//                } else if (value instanceof String) {
//                    setHorizontalAlignment(CENTER);
//                }
//                return c;
//            }
//        };
//
//        // Custom renderer for side column
//        DefaultTableCellRenderer sideRenderer = new DefaultTableCellRenderer() {
//            @Override
//            public Component getTableCellRendererComponent(JTable table, Object value,
//                                                           boolean isSelected, boolean hasFocus, int row, int column) {
//                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
//                if (!isSelected) {
//                    if ("BUY".equals(value)) {
//                        setForeground(new Color(200, 0, 0));
//                    } else if ("SELL".equals(value)) {
//                        setForeground(new Color(0, 200, 0));
//                    }
//                }
//                setHorizontalAlignment(CENTER);
//                return c;
//            }
//        };
//
//        // Custom renderer for boolean columns
//        DefaultTableCellRenderer booleanRenderer = new DefaultTableCellRenderer() {
//            @Override
//            public Component getTableCellRendererComponent(JTable table, Object value,
//                                                           boolean isSelected, boolean hasFocus, int row, int column) {
//                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
//                if (value instanceof Boolean) {
//                    setText((Boolean) value ? "Yes" : "No");
//                    setHorizontalAlignment(CENTER);
//                    if (!isSelected) {
//                        setForeground((Boolean) value ? new Color(0, 200, 0) : ColorScheme.LIGHT_GRAY_COLOR);
//                    }
//                }
//                return c;
//            }
//        };
//
//        // Apply renderers
//        table.getColumnModel().getColumn(2).setCellRenderer(sideRenderer);
//        table.getColumnModel().getColumn(5).setCellRenderer(moneyRenderer);
//        table.getColumnModel().getColumn(6).setCellRenderer(moneyRenderer);
//        table.getColumnModel().getColumn(7).setCellRenderer(moneyRenderer);
//        table.getColumnModel().getColumn(8).setCellRenderer(booleanRenderer);
//        table.getColumnModel().getColumn(9).setCellRenderer(booleanRenderer);
//
//        // Center align quantity column
//        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
//        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
//        table.getColumnModel().getColumn(4).setCellRenderer(centerRenderer);
//
//        // Add to scroll pane
//        JScrollPane scrollPane = new JScrollPane(table);
//        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
//        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
//        add(scrollPane, BorderLayout.CENTER);
//
//        // Create pagination panel
//        paginator = new Paginator((i) -> loadTransactions());
//        add(paginator, BorderLayout.SOUTH);
//
//        // Load initial data
//        loadTransactions();
//    }
//
//    private JButton createAddTransactionButton() {
//        JButton button = new JButton();
//        button.setToolTipText("Add transaction");
//        button.setText("Add transaction");
//        button.setFocusable(false);
//        button.addActionListener(e -> {
//            // TODO: Implement add transaction dialog
//            log.info("Add transaction button clicked");
//        });
//
//        return button;
//    }
//
//    private JButton createDownloadButton() {
//        JButton button = new JButton();
//        button.setToolTipText("Download as CSV");
//        button.setFocusable(false);
//        button.setText("Download");
//        button.addActionListener(e -> downloadAsCSV());
//        return button;
//    }
//
//    private void showTransactionMenu(MouseEvent e, int row) {
//        Transaction transaction = currentTransactions.get(row);
//
//        JPopupMenu menu = new JPopupMenu();
//
//        JMenuItem reportItem = new JMenuItem("Report as erroneous");
//        reportItem.addActionListener(evt -> {
//            log.info("Reporting transaction {} as erroneous", transaction.getId());
//            // TODO: Implement error reporting
//        });
//
//        JMenuItem flipItem = new JMenuItem(transaction.isConsistent() ? "Mark as not part of flip" : "Mark as part of flip");
//        flipItem.addActionListener(evt -> {
//            transaction.setConsistent(!transaction.isConsistent());
//            loadTransactions(); // Refresh table
//            log.info("Toggled flip status for transaction {}", transaction.getId());
//        });
//
//        JMenuItem copilotItem = new JMenuItem(transaction.isCopilotPriceUsed() ? "I didn't use Copilot price" : "I used Copilot price");
//        copilotItem.addActionListener(evt -> {
//            transaction.setCopilotPriceUsed(!transaction.isCopilotPriceUsed());
//            loadTransactions(); // Refresh table
//            log.info("Toggled Copilot price for transaction {}", transaction.getId());
//        });
//
//        menu.add(reportItem);
//        menu.add(flipItem);
//        menu.add(copilotItem);
//
//        menu.show(e.getComponent(), e.getX(), e.getY());
//    }
//
//    private void downloadAsCSV() {
//        JFileChooser fileChooser = new JFileChooser();
//        fileChooser.setSelectedFile(new File("transactions.csv"));
//
//        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
//            File file = fileChooser.getSelectedFile();
//            try (FileWriter writer = new FileWriter(file)) {
//                // Write headers
//                writer.write("Timestamp,Account,Side,Item ID,Quantity,Paid/Received,Tax,Price per Item,Copilot Price,Part of Flip\n");
//
//                // Write all transactions (not just current page)
//                int totalTransactions = transactionManager.getTotalTransactions();
//                int totalPages = (totalTransactions + PAGE_SIZE - 1) / PAGE_SIZE;
//
//                for (int page = 0; page < totalPages; page++) {
//                    List<Transaction> transactions = transactionManager.getPageTransactions(page, PAGE_SIZE);
//                    for (Transaction t : transactions) {
//                        writer.write(String.format("%s,%s,%s,%d,%d,%d,%s,%d,%s,%s\n",
//                                DATE_FORMAT.format(t.getTimestamp()),
//                                "", // Account column - empty for now
//                                t.getType(),
//                                t.getItemId(),
//                                t.getQuantity(),
//                                t.getAmountSpent(),
//                                t.getType() == OfferStatus.BUY ? "n/a" : String.valueOf(calculateTax(t)),
//                                t.getAmountSpent() / t.getQuantity(),
//                                t.isCopilotPriceUsed() ? "Yes" : "No",
//                                t.isConsistent() ? "Yes" : "No"
//                        ));
//                    }
//                }
//
//                JOptionPane.showMessageDialog(this, "Transactions exported successfully!",
//                        "Export Complete", JOptionPane.INFORMATION_MESSAGE);
//            } catch (IOException ex) {
//                log.error("Error exporting transactions", ex);
//                JOptionPane.showMessageDialog(this, "Error exporting transactions: " + ex.getMessage(),
//                        "Export Error", JOptionPane.ERROR_MESSAGE);
//            }
//        }
//    }
//
//    private void loadTransactions() {
//        try {
//            int page = paginator.getPageNumber() - 1; // Paginator uses 1-based indexing
//            currentTransactions = transactionManager.getPageTransactions(page, PAGE_SIZE);
//            int totalTransactions = transactionManager.getTotalTransactions();
//            int totalPages = Math.max(1, (totalTransactions + PAGE_SIZE - 1) / PAGE_SIZE);
//
//            paginator.setTotalPages(totalPages);
//
//            // Clear existing data
//            tableModel.setRowCount(0);
//
//            // Add new data
//            for (Transaction transaction : currentTransactions) {
//                Object taxValue;
//                if (transaction.getType() == OfferStatus.BUY) {
//                    taxValue = "n/a";
//                } else {
//                    taxValue = calculateTax(transaction);
//                }
//
//                long pricePerItem = transaction.getAmountSpent() / transaction.getQuantity();
//
//                Object[] row = {
//                        DATE_FORMAT.format(transaction.getTimestamp()),
//                        "", // Account column - empty for now
//                        transaction.getType().toString(),
//                        "Item ID: " + transaction.getItemId(), // You might want to get item name from ItemManager
//                        transaction.getQuantity(),
//                        (long) transaction.getAmountSpent(),
//                        taxValue,
//                        pricePerItem,
//                        transaction.isCopilotPriceUsed(),
//                        transaction.isConsistent()
//                };
//                tableModel.addRow(row);
//            }
//
//            // Auto-resize columns based on content
//            SwingUtilities.invokeLater(() -> {
//                for (int i = 0; i < table.getColumnCount(); i++) {
//                    resizeColumnWidth(table, i);
//                }
//            });
//
//        } catch (Exception e) {
//            log.error("Error loading transactions", e);
//            JOptionPane.showMessageDialog(this, "Error loading transactions: " + e.getMessage(),
//                    "Error", JOptionPane.ERROR_MESSAGE);
//        }
//    }
//
//    private void resizeColumnWidth(JTable table, int column) {
//        TableColumn tableColumn = table.getColumnModel().getColumn(column);
//        int preferredWidth = tableColumn.getMinWidth();
//        int maxWidth = tableColumn.getMaxWidth();
//
//        // Check header width
//        Component comp = table.getTableHeader().getDefaultRenderer()
//                .getTableCellRendererComponent(table, tableColumn.getHeaderValue(), false, false, 0, column);
//        preferredWidth = Math.max(comp.getPreferredSize().width + 10, preferredWidth);
//
//        // Check cell widths
//        for (int row = 0; row < table.getRowCount(); row++) {
//            comp = table.getCellRenderer(row, column)
//                    .getTableCellRendererComponent(table, table.getValueAt(row, column), false, false, row, column);
//            preferredWidth = Math.max(comp.getPreferredSize().width + 10, preferredWidth);
//        }
//
//        preferredWidth = Math.min(preferredWidth, maxWidth);
//        tableColumn.setPreferredWidth(preferredWidth);
//    }
//
//    private long calculateTax(Transaction transaction) {
//        if (transaction.getType() == OfferStatus.SELL) {
//            int pricePerItem = transaction.getAmountSpent() / transaction.getQuantity();
//            int pricePostTax = GeTax.getPostTaxPrice(transaction.getItemId(), pricePerItem);
//            return (long)(pricePerItem - pricePostTax) * transaction.getQuantity();
//        }
//        return 0;
//    }
//
//    private String formatGp(long amount) {
//        return GP_FORMAT.format(amount) + " gp";
//    }
//}