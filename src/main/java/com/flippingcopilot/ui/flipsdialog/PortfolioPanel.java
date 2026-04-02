package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.controller.ApiRequestHandler;
import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.model.PortfolioItemCardData;
import com.flippingcopilot.model.PortfolioState;
import com.flippingcopilot.model.PortfolioSummaryData;
import com.flippingcopilot.model.Suggestion;
import com.flippingcopilot.model.SuggestionManager;
import com.flippingcopilot.model.ToggleItemPortfolioRequest;
import com.flippingcopilot.rs.CopilotLoginRS;
import com.flippingcopilot.rs.BankStateRS;
import com.flippingcopilot.rs.OsrsLoginRS;
import com.flippingcopilot.rs.PortfolioStateRS;
import com.flippingcopilot.ui.UIUtilities;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class PortfolioPanel extends JPanel {
    private static final NumberFormat GP_FORMAT = NumberFormat.getNumberInstance(Locale.US);
    private static final String CONTENT_CARD = "content";
    private static final String LOGIN_PROMPT_CARD = "login";
    private static final String[] COLUMN_NAMES = {
            "Item", "Market value", "Quantities", "Avg buy price", "Open flips", "Time held", "Unrealized Profit", "Unrealized ROI"
    };

    private final ItemController itemController;
    private final FlippingCopilotConfig config;
    private final ApiRequestHandler apiRequestHandler;
    private final SuggestionManager suggestionManager;
    private final CopilotLoginRS copilotLoginRS;
    private final OsrsLoginRS osrsLoginRS;
    private final PortfolioStateRS portfolioStateRS;
    private final BankStateRS bankStateRS;
    private final ClientThread clientThread;
    private final CardLayout cardLayout;
    private final JPanel cardPanel;
    private final JPanel summaryTablePanel;
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JLabel autoSyncInfoLabel;
    private final Map<Integer, ImageIcon> itemIconCache = new ConcurrentHashMap<>();

    public PortfolioPanel(ItemController itemController,
                          FlippingCopilotConfig config,
                          ApiRequestHandler apiRequestHandler,
                          SuggestionManager suggestionManager,
                          CopilotLoginRS copilotLoginRS,
                          OsrsLoginRS osrsLoginRs,
                          PortfolioStateRS portfolioStateRS,
                          BankStateRS bankStateRS,
                          ClientThread clientThread) {
        this.itemController = itemController;
        this.config = config;
        this.apiRequestHandler = apiRequestHandler;
        this.suggestionManager = suggestionManager;
        this.copilotLoginRS = copilotLoginRS;
        this.osrsLoginRS = osrsLoginRs;
        this.portfolioStateRS = portfolioStateRS;
        this.bankStateRS = bankStateRS;
        this.clientThread = clientThread;

        setLayout(new BorderLayout(0, 12));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(10, 10, 10, 10));

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.setOpaque(false);

        JPanel contentPanel = new JPanel(new BorderLayout(0, 12));
        contentPanel.setOpaque(false);
        contentPanel.setBorder(new EmptyBorder(8, 0, 0, 0));

        JPanel summarySection = new JPanel(new BorderLayout(28, 0));
        summarySection.setOpaque(false);
        summarySection.setBorder(new EmptyBorder(0, 0, 10, 0));

        summaryTablePanel = new JPanel(new GridLayout(0, 2, 24, 10));
        summaryTablePanel.setOpaque(false);
        summaryTablePanel.setBorder(new EmptyBorder(4, 0, 4, 0));
        summarySection.add(summaryTablePanel, BorderLayout.WEST);

        JPanel rightControlsPanel = new JPanel();
        rightControlsPanel.setOpaque(false);
        rightControlsPanel.setLayout(new BorderLayout());
        rightControlsPanel.setBorder(new EmptyBorder(0, 12, 0, 0));

        JPanel bottomRightWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bottomRightWrap.setOpaque(false);
        autoSyncInfoLabel = new JLabel();
        autoSyncInfoLabel.setForeground(ColorScheme.BRAND_ORANGE);
        autoSyncInfoLabel.setFont(FontManager.getRunescapeFont());
        autoSyncInfoLabel.setHorizontalAlignment(SwingConstants.LEFT);
        bottomRightWrap.add(autoSyncInfoLabel);

        rightControlsPanel.add(bottomRightWrap, BorderLayout.SOUTH);
        summarySection.add(rightControlsPanel, BorderLayout.CENTER);

        contentPanel.add(summarySection, BorderLayout.NORTH);

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
        table.setRowHeight(92);
        table.setRowSorter(null);
        table.getTableHeader().setReorderingAllowed(false);
        table.setFocusable(false);

        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(JLabel.RIGHT);
        table.getColumnModel().getColumn(2).setCellRenderer(rightRenderer);
        table.getColumnModel().getColumn(3).setCellRenderer(rightRenderer);
        table.getColumnModel().getColumn(4).setCellRenderer(rightRenderer);
        table.getColumnModel().getColumn(5).setCellRenderer(rightRenderer);
        table.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value instanceof Long) {
                    setText(formatGp((Long) value, false));
                }
                setHorizontalAlignment(RIGHT);
                return c;
            }
        });
        table.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value instanceof Long) {
                    setText(formatGp((Long) value, false));
                } else if (value == null) {
                    setText("Unknown");
                }
                setHorizontalAlignment(RIGHT);
                return c;
            }
        });
        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value instanceof ItemCell) {
                    ItemCell itemCell = (ItemCell) value;
                    label.setText(itemCell.name);
                    ImageIcon cachedIcon = itemIconCache.get(itemCell.itemId);
                    if (cachedIcon != null) {
                        label.setIcon(cachedIcon);
                    } else {
                        label.setIcon(null);
                        itemController.loadImage(itemCell.itemId, image -> {
                            if (image != null) {
                                itemIconCache.put(itemCell.itemId, new ImageIcon(image));
                                SwingUtilities.invokeLater(table::repaint);
                            }
                        });
                    }
                } else {
                    label.setIcon(null);
                }
                return label;
            }
        });

        DefaultTableCellRenderer profitRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value instanceof Long) {
                    long amount = (Long) value;
                    setText(formatGp(amount, true));
                    setHorizontalAlignment(RIGHT);
                    if (!isSelected) {
                        setForeground(UIUtilities.getProfitColor(amount, config));
                    }
                }
                return c;
            }
        };
        table.getColumnModel().getColumn(6).setCellRenderer(profitRenderer);

        DefaultTableCellRenderer roiRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(RIGHT);
                if (value instanceof Double) {
                    double roi = (Double) value;
                    setText(String.format("%.2f%%", roi * 100.0d));
                    if (!isSelected) {
                        setForeground(UIUtilities.getProfitColor(roi, config));
                    }
                } else {
                    setText(value == null ? "Unknown" : value.toString());
                }
                return c;
            }
        };
        table.getColumnModel().getColumn(7).setCellRenderer(roiRenderer);
        installRowContextMenu();

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        contentPanel.add(scrollPane, BorderLayout.CENTER);

        cardPanel.add(contentPanel, CONTENT_CARD);
        cardPanel.add(buildLoginPromptPanel(), LOGIN_PROMPT_CARD);
        add(cardPanel, BorderLayout.CENTER);

        portfolioStateRS.registerListener(state -> SwingUtilities.invokeLater(() -> {
            if (osrsLoginRS.get().loggedIn) {
                renderFromState(state);
            }
        }));
        osrsLoginRS.registerListener(state -> SwingUtilities.invokeLater(this::refresh));
        bankStateRS.registerListener(state -> SwingUtilities.invokeLater(this::refreshAutoSyncLabel));

        refresh();
    }

    public void onTabShown() {
        refresh();
    }

    private void refresh() {
        if (!osrsLoginRS.get().loggedIn) {
            cardLayout.show(cardPanel, LOGIN_PROMPT_CARD);
            revalidate();
            repaint();
            return;
        }
        cardLayout.show(cardPanel, CONTENT_CARD);
        refreshAutoSyncLabel();
        renderFromState(portfolioStateRS.get());
    }

    private void refreshAutoSyncLabel() {
        String labelText = bankStateRS.get().isLoaded()
                ? "Bank loaded. Full quantity syncing enabled. Note: items are excluded from syncing whilst active in one of your Grand Exchange slots."
                : "Please open your bank once to enable more accurate quantity syncing.";
        autoSyncInfoLabel.setText(String.format("<html><div style='width: 560px; text-align: left;'>%s</div></html>", labelText));
    }

    private List<PortfolioItemCardData> filterInPortfolioItems(List<PortfolioItemCardData> items) {
        List<PortfolioItemCardData> filteredItems = new ArrayList<>();
        for (PortfolioItemCardData item : items) {
            if (item.isInPortfolio()) {
                filteredItems.add(item);
            }
        }
        return filteredItems;
    }

    private void renderFromState(PortfolioState state) {
        List<PortfolioItemCardData> items = new ArrayList<>(state.getItemCardDataByItemId().values());
        List<PortfolioItemCardData> inPortfolioItems = filterInPortfolioItems(items);
        PortfolioSummaryData sm = state.getSummaryData();
        renderSummary(sm, inPortfolioItems.size());
        renderTable(inPortfolioItems);
        revalidate();
        repaint();
    }

    private void renderSummary(PortfolioSummaryData data, int totalItemsInPortfolio) {
        summaryTablePanel.removeAll();
        if (data == null) {
            return;
        }
        addSummaryRow("Portfolio Market Value", formatGp(data.getPortfolioMarketValue(), false));
        addSummaryRow("Unrealised Profit", formatGp(data.getUnrealizedProfit(), true), UIUtilities.getProfitColor(data.getUnrealizedProfit(), config));
        addSummaryRow("Cash Value", formatGp(data.getCashValue(), false));
        addSummaryRow("Assets Value", formatGp(data.getAssetsValue(), false));
        addSummaryRow("Total items in portfolio", NumberFormat.getIntegerInstance(Locale.US).format(totalItemsInPortfolio));
    }

    private void addSummaryRow(String label, String value) {
        addSummaryRow(label, value, ColorScheme.LIGHT_GRAY_COLOR);
    }

    private void addSummaryRow(String label, String value, Color valueColor) {
        JLabel keyLabel = new JLabel(label);
        keyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        keyLabel.setFont(FontManager.getRunescapeFont());

        JLabel valueLabel = new JLabel(value, SwingConstants.RIGHT);
        valueLabel.setForeground(valueColor);
        valueLabel.setFont(FontManager.getRunescapeBoldFont());

        summaryTablePanel.add(keyLabel);
        summaryTablePanel.add(valueLabel);
    }

    private void renderTable(List<PortfolioItemCardData> items) {
        tableModel.setRowCount(0);
        List<PortfolioItemCardData> sortedItems = new ArrayList<>(items);
        sortedItems.sort((a, b) -> Long.compare(
                b.getPostTaxSellUnitPrice() * b.getOpenFlipsQuantity(),
                a.getPostTaxSellUnitPrice() * a.getOpenFlipsQuantity()
        ));

        for (PortfolioItemCardData item : sortedItems) {
            long avgBuyPrice = item.getUnitBuyPrice();
            String quantitySummary = NumberFormat.getIntegerInstance(Locale.US).format(item.getOpenFlipsQuantity())
                    + " (inv: " + NumberFormat.getIntegerInstance(Locale.US).format(item.getRuneliteInventoryQuantity())
                    + " bank: " + NumberFormat.getIntegerInstance(Locale.US).format(item.getSuggestionBankQuantity())
                    + " GE: " + NumberFormat.getIntegerInstance(Locale.US).format(item.getRuneliteGeQuantity())
                    + ")";
            tableModel.addRow(new Object[]{
                    new ItemCell(item.getItemId(), item.getItemName()),
                    item.getPostTaxSellUnitPrice() * item.getOpenFlipsQuantity(),
                    quantitySummary,
                    avgBuyPrice > 0 ? avgBuyPrice : null,
                    item.getOpenFlipsCount(),
                    UIUtilities.formatDurationMinutes(item.getHeldMinutes()),
                    item.flipsUnrealizedProfit(),
                    calculateUnrealizedRoi(item)
            });
        }
    }

    private void installRowContextMenu() {
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem removeFromPortfolioItem = new JMenuItem("Remove from portfolio");
        removeFromPortfolioItem.addActionListener(e -> removeSelectedRowFromPortfolio());
        popupMenu.add(removeFromPortfolioItem);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                showPopupIfNeeded(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showPopupIfNeeded(e);
            }

            private void showPopupIfNeeded(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                int row = table.rowAtPoint(e.getPoint());
                if (row < 0) {
                    return;
                }
                table.setRowSelectionInterval(row, row);
                popupMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        });
    }

    private void removeSelectedRowFromPortfolio() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        Object itemCellObj = tableModel.getValueAt(modelRow, 0);
        if (!(itemCellObj instanceof ItemCell)) {
            return;
        }

        ItemCell itemCell = (ItemCell) itemCellObj;
        Integer accountId = copilotLoginRS.get().getAccountId(osrsLoginRS.get().displayName);
        if (accountId == null || accountId == -1) {
            return;
        }

        clientThread.invokeLater(() -> {
            Map<Integer, Integer> runeliteInventory = itemController.getRunliteInventory();
            int bagQuantity = runeliteInventory == null ? 0 : Math.max(0, runeliteInventory.getOrDefault(itemCell.itemId, 0));
            int bankQuantity = bankStateRS.get().isLoaded()
                    ? Math.max(0, bankStateRS.get().getItems().getOrDefault(itemCell.itemId, 0))
                    : -1;

            ToggleItemPortfolioRequest request = new ToggleItemPortfolioRequest(accountId, itemCell.itemId, -1, bagQuantity, bankQuantity);
            apiRequestHandler.toggleItemPortfolioAsync(
                    request,
                    (userId, result) -> {
                        Suggestion suggestion = suggestionManager.getSuggestion();
                        portfolioStateRS.updatePortfolioState(
                                suggestion == null ? null : suggestion.getBankItems(),
                                result == null ? null : result.getPortfolioItems()
                        );
                        suggestionManager.setSuggestionNeeded(true);
                    },
                    error -> {
                    }
            );
            return true;
        });
    }

    private JPanel buildLoginPromptPanel() {
        JPanel loginPromptPanel = new JPanel(new GridBagLayout());
        loginPromptPanel.setOpaque(false);
        JLabel messageLabel = new JLabel("Log into the game to see account portfolio");
        messageLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        messageLabel.setFont(messageLabel.getFont().deriveFont(18f));
        loginPromptPanel.add(messageLabel);
        return loginPromptPanel;
    }

    private String formatGp(long amount, boolean signed) {
        String prefix = signed && amount > 0 ? "+" : "";
        return prefix + GP_FORMAT.format(amount) + " gp";
    }

    private Double calculateUnrealizedRoi(PortfolioItemCardData item) {
        if (item.getUnrealizedUnitProfit() == null) {
            return null;
        }
        long unitBuyPrice = item.getPostTaxSellUnitPrice() - item.getUnrealizedUnitProfit();
        if (unitBuyPrice <= 0) {
            return null;
        }
        return (double) item.getUnrealizedUnitProfit() / (double) unitBuyPrice;
    }

    private static class ItemCell {
        private final int itemId;
        private final String name;

        private ItemCell(int itemId, String name) {
            this.itemId = itemId;
            this.name = name;
        }
    }

}
