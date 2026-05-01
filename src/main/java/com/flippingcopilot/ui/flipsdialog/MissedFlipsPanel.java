package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.controller.ApiRequestHandler;
import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.model.FlipManager;
import com.flippingcopilot.model.FlipStatus;
import com.flippingcopilot.model.FlipV2;
import com.flippingcopilot.model.HttpResponseException;
import com.flippingcopilot.model.OfferStatus;
import com.flippingcopilot.model.SortDirection;
import com.flippingcopilot.model.Transaction;
import com.flippingcopilot.rs.CopilotLoginRS;
import com.flippingcopilot.rs.OsrsLoginRS;
import com.flippingcopilot.ui.Spinner;
import com.flippingcopilot.ui.components.ItemSearchMultiSelect;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.flippingcopilot.util.DateUtil.formatEpoch;

@Slf4j
public class MissedFlipsPanel extends JPanel {

    private static final NumberFormat GP_FORMAT = NumberFormat.getNumberInstance(Locale.US);
    private static final String[] COLUMN_NAMES = {
            "First buy time", "Last sell time", "Item", "Status", "Bought", "Sold",
            "Avg. buy price", "Avg. sell price", "Tax", "Profit", "Profit ea."
    };

    private static final Map<String, Comparator<FlipV2>> SORT_COMPARATORS = new HashMap<>();
    static {
        SORT_COMPARATORS.put("First buy time", Comparator.comparing(FlipV2::getOpenedTime));
        SORT_COMPARATORS.put("Last sell time", Comparator.comparing(FlipV2::lastTransactionTime).reversed());
        SORT_COMPARATORS.put("Item", Comparator.comparing(f -> f.getCachedItemName() != null ? f.getCachedItemName() : ""));
        SORT_COMPARATORS.put("Status", Comparator.comparing(FlipV2::getStatus));
        SORT_COMPARATORS.put("Bought", Comparator.comparing(FlipV2::getOpenedQuantity));
        SORT_COMPARATORS.put("Sold", Comparator.comparing(FlipV2::getClosedQuantity));
        SORT_COMPARATORS.put("Avg. buy price", Comparator.comparing(FlipV2::getSpent));
        SORT_COMPARATORS.put("Avg. sell price", Comparator.comparing(FlipV2::getReceivedPostTax));
        SORT_COMPARATORS.put("Tax", Comparator.comparing(FlipV2::getTaxPaid));
        SORT_COMPARATORS.put("Profit", Comparator.comparing(FlipV2::getProfit));
        SORT_COMPARATORS.put("Profit ea.", Comparator.comparing(f -> f.getClosedQuantity() > 0 ? f.getProfit() / f.getClosedQuantity() : 0L));
    }

    private final FlipManager flipsManager;
    private final ItemController itemController;
    private final CopilotLoginRS copilotLoginRS;
    private final OsrsLoginRS osrsLoginRS;
    private final ApiRequestHandler apiRequestHandler;
    private final ExecutorService executorService;
    private final Consumer<FlipV2> onVisualizeFlip;

    private final DefaultTableModel tableModel;
    private final JTable table;
    private final Spinner spinner;
    private final JPanel spinnerOverlay;
    private final ItemSearchMultiSelect searchField;
    private final JLabel emptyStateLabel;

    private List<FlipV2> currentFlips = new ArrayList<>();
    private Set<Integer> filteredItems = new HashSet<>();
    private String sortColumn = "Last sell time";
    private SortDirection sortDirection = SortDirection.DESC;

    public MissedFlipsPanel(OsrsLoginRS osrsLoginRS,
                            FlipManager flipsManager,
                            ItemController itemController,
                            CopilotLoginRS copilotLoginRS,
                            ExecutorService executorService,
                            FlippingCopilotConfig config,
                            ApiRequestHandler apiRequestHandler,
                            Consumer<FlipV2> onVisualizeFlip) {
        this.osrsLoginRS = osrsLoginRS;
        this.flipsManager = flipsManager;
        this.itemController = itemController;
        this.copilotLoginRS = copilotLoginRS;
        this.executorService = executorService;
        this.apiRequestHandler = apiRequestHandler;
        this.onVisualizeFlip = onVisualizeFlip;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        searchField = new ItemSearchMultiSelect(
                () -> new HashSet<>(filteredItems),
                itemController::allItemIds,
                itemController::search,
                this::setFilteredItems,
                "Items filter...",
                SwingUtilities.getWindowAncestor(this));
        searchField.setMinimumSize(new Dimension(300, 0));
        searchField.setToolTipText("Search by item name");

        leftPanel.add(searchField);

        topPanel.add(leftPanel, BorderLayout.WEST);
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
        table.setRowSorter(null);
        table.getTableHeader().setReorderingAllowed(false);
        table.setFocusable(false);

        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int columnIndex = table.getTableHeader().columnAtPoint(e.getPoint());
                if (columnIndex >= 0 && columnIndex < COLUMN_NAMES.length) {
                    String clickedColumn = COLUMN_NAMES[columnIndex];
                    SortDirection newDirection = SortDirection.DESC;
                    if (clickedColumn.equals(sortColumn)) {
                        newDirection = sortDirection == SortDirection.DESC ? SortDirection.ASC : SortDirection.DESC;
                    }
                    sortColumn = clickedColumn;
                    sortDirection = newDirection;
                    refresh();
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

        // Column indices: 0 First buy, 1 Last sell, 2 Item, 3 Status, 4 Bought, 5 Sold,
        //                 6 Avg buy, 7 Avg sell, 8 Tax, 9 Profit, 10 Profit ea.
        table.getColumnModel().getColumn(6).setCellRenderer(moneyRenderer);
        table.getColumnModel().getColumn(7).setCellRenderer(moneyRenderer);
        table.getColumnModel().getColumn(8).setCellRenderer(moneyRenderer);
        table.getColumnModel().getColumn(9).setCellRenderer(profitRenderer);
        table.getColumnModel().getColumn(10).setCellRenderer(moneyRenderer);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        table.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(4).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(5).setCellRenderer(centerRenderer);

        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        layeredPane.setOpaque(true);

        spinner = new Spinner();
        spinner.show();
        spinnerOverlay = new JPanel(new GridBagLayout());
        spinnerOverlay.setBackground(ColorScheme.DARK_GRAY_COLOR);
        spinnerOverlay.setOpaque(true);
        spinnerOverlay.add(spinner);
        spinnerOverlay.setVisible(false);

        emptyStateLabel = new JLabel("Log in to a copilot account to view missed flips.", JLabel.CENTER);
        emptyStateLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        emptyStateLabel.setVisible(false);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);

        layeredPane.setLayout(new OverlayLayout(layeredPane));
        layeredPane.add(spinnerOverlay, JLayeredPane.MODAL_LAYER);
        layeredPane.add(emptyStateLabel, JLayeredPane.PALETTE_LAYER);
        layeredPane.add(scrollPane, JLayeredPane.DEFAULT_LAYER);

        add(layeredPane, BorderLayout.CENTER);
    }

    private void setFilteredItems(Set<Integer> items) {
        filteredItems = items == null ? new HashSet<>() : new HashSet<>(items);
        refresh();
    }

    public void onTabShown() {
        refresh();
    }

    private Integer resolveAccountId() {
        if (osrsLoginRS == null || osrsLoginRS.get() == null || !osrsLoginRS.get().loggedIn) {
            return null;
        }
        String displayName = osrsLoginRS.get().displayName;
        if (displayName == null) {
            return null;
        }
        Map<String, Integer> map = copilotLoginRS.get().displayNameToAccountId;
        if (map == null) {
            return null;
        }
        return map.get(displayName);
    }

    private void refresh() {
        executorService.submit(() -> {
            Integer accountId = resolveAccountId();
            List<FlipV2> flips;
            if (accountId == null) {
                flips = Collections.emptyList();
            } else {
                flips = new ArrayList<>(flipsManager.getDisappearedFlipsForAccount(accountId));
                if (!filteredItems.isEmpty()) {
                    flips.removeIf(f -> !filteredItems.contains(f.getItemId()));
                }
                Comparator<FlipV2> comparator = SORT_COMPARATORS.get(sortColumn);
                if (comparator != null) {
                    if (sortDirection == SortDirection.ASC) {
                        comparator = comparator.reversed();
                    }
                    flips.sort(comparator);
                }
            }
            boolean showEmptyState = accountId == null;
            showFlips(flips, showEmptyState);
        });
    }

    private void showFlips(List<FlipV2> flips, boolean showEmptyState) {
        SwingUtilities.invokeLater(() -> {
            currentFlips = flips;
            tableModel.setRowCount(0);
            for (FlipV2 flip : flips) {
                long profitPerItem = flip.getClosedQuantity() > 0 ? flip.getProfit() / flip.getClosedQuantity() : 0L;
                long avgBuy = flip.getOpenedQuantity() > 0 ? flip.getSpent() / flip.getOpenedQuantity() : 0L;
                long avgSell = flip.getClosedQuantity() == 0 ? 0L : (flip.getReceivedPostTax() + flip.getTaxPaid()) / flip.getClosedQuantity();
                Object[] row = {
                        formatTimestamp(flip.getOpenedTime()),
                        formatTimestamp(flip.getClosedTime()),
                        flip.getCachedItemName(),
                        flip.getStatus().name(),
                        flip.getOpenedQuantity(),
                        flip.getClosedQuantity(),
                        avgBuy,
                        avgSell,
                        flip.getTaxPaid(),
                        flip.getProfit(),
                        profitPerItem
                };
                tableModel.addRow(row);
            }
            for (int i = 0; i < table.getColumnCount(); i++) {
                resizeColumnWidth(table, i);
            }
            emptyStateLabel.setVisible(showEmptyState);
            table.setVisible(!showEmptyState);
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

    private void setSpinnerVisible(boolean visible) {
        SwingUtilities.invokeLater(() -> {
            spinnerOverlay.setVisible(visible);
            table.setEnabled(!visible);
        });
    }

    private void showFlipMenu(MouseEvent e, int row) {
        FlipV2 flip = currentFlips.get(row);

        JPopupMenu menu = new JPopupMenu();
        JMenuItem visualizeFlip = new JMenuItem("Visualize flip");
        visualizeFlip.addActionListener(evt -> onVisualizeFlip.accept(flip));
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
                    flipsManager.mergeFlips(Collections.singletonList(f), copilotLoginRS.get().getUserId());
                    setSpinnerVisible(false);
                    refresh();
                };
                apiRequestHandler.asyncDeleteFlip(flip, onSuccess, () -> setSpinnerVisible(false));
            }
        });
        menu.add(deleteItem);

        String flipOsrsDisplayName = copilotLoginRS.get().getDisplayName(flip.getAccountId());
        if (canAddMissedSell(flipOsrsDisplayName, flip)) {
            JMenuItem missedSellTransaction = new JMenuItem("Add missed sell transaction");
            missedSellTransaction.addActionListener(evt -> promptAndSubmitMissedSell(flip, flipOsrsDisplayName));
            menu.add(missedSellTransaction);
        }
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private boolean canAddMissedSell(String flipOsrsDisplayName, FlipV2 flip) {
        if (flipOsrsDisplayName == null) {
            return false;
        }
        if (FlipStatus.FINISHED.equals(flip.getStatus())) {
            return false;
        }
        if (flip.getOpenedQuantity() - flip.getClosedQuantity() <= 0) {
            return false;
        }
        return osrsLoginRS.get().loggedIn && Objects.equals(flipOsrsDisplayName, osrsLoginRS.get().displayName);
    }

    private void promptAndSubmitMissedSell(FlipV2 flip, String flipOsrsDisplayName) {
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

        if (result != JOptionPane.YES_OPTION) {
            return;
        }
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
            t.setAmountSpent((long) price * qty);
            t.setTimestamp(Instant.now());
            t.setCopilotPriceUsed(true);
            t.setWasCopilotSuggestion(true);
            t.setOfferTotalQuantity(qty);

            Long profit = flipsManager.estimateTransactionProfit(flip.getAccountId(), t);
            if (profit == null || !validateProfit(profit, flip, price)) {
                setSpinnerVisible(false);
                return;
            }

            BiConsumer<Integer, List<FlipV2>> onSuccess = (userId, flips) -> {
                flipsManager.mergeFlips(flips, userId);
                setSpinnerVisible(false);
                refresh();
            };
            Consumer<HttpResponseException> onFailure = (r) -> {
                setSpinnerVisible(false);
                JOptionPane.showMessageDialog(this,
                        "Failed to add sell transaction. Please try again.",
                        "Transaction Error",
                        JOptionPane.ERROR_MESSAGE);
            };
            apiRequestHandler.sendTransactionsAsync(List.of(t), flipOsrsDisplayName, onSuccess, onFailure);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    "Please enter a valid number for the price.",
                    "Invalid Price",
                    JOptionPane.ERROR_MESSAGE);
        }
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
}
