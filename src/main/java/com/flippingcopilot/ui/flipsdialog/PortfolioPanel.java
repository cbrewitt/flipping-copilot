package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.controller.ApiRequestHandler;
import com.flippingcopilot.controller.PortfolioController;
import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.model.PortfolioItemCardData;
import com.flippingcopilot.model.PortfolioSummaryData;
import com.flippingcopilot.model.SyncPortfolioRequest;
import com.flippingcopilot.model.FlipManager;
import com.flippingcopilot.rs.OsrsLoginRS;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public class PortfolioPanel extends JPanel {
    private static final NumberFormat GP_FORMAT = NumberFormat.getNumberInstance(Locale.US);
    private static final String CONTENT_CARD = "content";
    private static final String LOGIN_PROMPT_CARD = "login";
    private static final String[] COLUMN_NAMES = {
            "Item", "Portfolio Quantity", "Held Quantity", "Open flips", "Time held", "Unrealized Profit", "Unrealized RIO"
    };

    private final PortfolioController portfolioController;
    private final ItemController itemController;
    private final FlippingCopilotConfig config;
    private final OsrsLoginRS osrsLoginRS;
    private final ApiRequestHandler apiRequestHandler;
    private final FlipManager flipManager;
    private final CardLayout cardLayout;
    private final JPanel cardPanel;
    private final JPanel summaryTablePanel;
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final CardLayout syncButtonCardLayout;
    private final JPanel syncButtonCardPanel;
    private final JButton syncButton;
    private final JLabel syncDisabledMessageLabel;
    private final ClientThread clientThread;
    private final Map<Integer, ImageIcon> itemIconCache = new ConcurrentHashMap<>();

    public PortfolioPanel(PortfolioController portfolioController,
                          ItemController itemController,
                          FlippingCopilotConfig config,
                          OsrsLoginRS osrsLoginRs,
                          ApiRequestHandler apiRequestHandler,
                          FlipManager flipManager,
                          ClientThread clientThread) {
        this.portfolioController = portfolioController;
        this.itemController = itemController;
        this.config = config;
        this.osrsLoginRS = osrsLoginRs;
        this.apiRequestHandler = apiRequestHandler;
        this.flipManager = flipManager;
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

        JPanel syncControlsPanel = new JPanel();
        syncControlsPanel.setOpaque(false);
        syncControlsPanel.setLayout(new BoxLayout(syncControlsPanel, BoxLayout.Y_AXIS));

        syncButtonCardLayout = new CardLayout();
        syncButtonCardPanel = new JPanel(syncButtonCardLayout);
        syncButtonCardPanel.setOpaque(false);
        syncButtonCardPanel.setLayout(syncButtonCardLayout);

        syncButton = new JButton("Sync item quantities");
        syncButton.addActionListener(e -> onSyncClicked());

        JPanel syncingPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        syncingPanel.setOpaque(false);
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(90, 16));
        JLabel syncingLabel = new JLabel(" syncing item quantities");
        syncingLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        syncingPanel.add(progressBar);
        syncingPanel.add(syncingLabel);

        syncButtonCardPanel.add(syncButton, "button");
        syncButtonCardPanel.add(syncingPanel, "syncing");

        syncDisabledMessageLabel = new JLabel("Open bank once to enable sync");
        syncDisabledMessageLabel.setForeground(config.lossAmountColor());
        syncDisabledMessageLabel.setBorder(new EmptyBorder(6, 0, 0, 0));
        syncDisabledMessageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        syncDisabledMessageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel topRightButtonWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        topRightButtonWrap.setOpaque(false);
        topRightButtonWrap.setAlignmentX(Component.CENTER_ALIGNMENT);
        topRightButtonWrap.add(syncButtonCardPanel);

        syncControlsPanel.add(topRightButtonWrap);
        syncControlsPanel.add(syncDisabledMessageLabel);

        JPanel bottomRightWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bottomRightWrap.setOpaque(false);
        bottomRightWrap.add(syncControlsPanel);

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
        table.getColumnModel().getColumn(1).setCellRenderer(rightRenderer);
        table.getColumnModel().getColumn(3).setCellRenderer(rightRenderer);
        table.getColumnModel().getColumn(4).setCellRenderer(rightRenderer);
        table.getColumnModel().getColumn(2).setCellRenderer(new HeldQuantityCellRenderer());
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
                        setForeground(getProfitColor(amount));
                    }
                }
                return c;
            }
        };
        table.getColumnModel().getColumn(5).setCellRenderer(profitRenderer);

        DefaultTableCellRenderer roiRenderer = new DefaultTableCellRenderer();
        roiRenderer.setHorizontalAlignment(JLabel.RIGHT);
        table.getColumnModel().getColumn(6).setCellRenderer(roiRenderer);

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
        syncButtonCardLayout.show(syncButtonCardPanel, "button");
        updateSyncEnabledState();
        clientThread.invokeLater(() -> {
            List<PortfolioItemCardData> items = portfolioController.getPortfolioItems();
            List<PortfolioItemCardData> inPortfolioItems = filterInPortfolioItems(items);
            PortfolioSummaryData sm = portfolioController.buildPortfolioSummaryData(inPortfolioItems);
            SwingUtilities.invokeLater(() -> {
                renderSummary(sm, inPortfolioItems.size());
                renderTable(inPortfolioItems);
                revalidate();
                repaint();
            });
        });
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

    private void onSyncClicked() {
        syncButtonCardLayout.show(syncButtonCardPanel, "syncing");
        syncButton.setEnabled(false);

        clientThread.invokeLater(() -> {
            SyncPortfolioRequest request = portfolioController.buildSyncPortfolioRequest();
            if (request == null) {
                SwingUtilities.invokeLater(this::refresh);
                return;
            }
            apiRequestHandler.syncPortfolioAsync(
                    request,
                    (userId, flips) -> {
                        flipManager.mergeFlips(flips, userId);
                        SwingUtilities.invokeLater(this::refresh);
                    },
                    error -> SwingUtilities.invokeLater(this::refresh)
            );
        });
    }

    private void updateSyncEnabledState() {
        boolean enabled = portfolioController.isSyncEnabled();
        syncButton.setEnabled(enabled);
        syncDisabledMessageLabel.setVisible(!enabled);
    }

    private void renderSummary(PortfolioSummaryData data, int totalItemsInPortfolio) {
        summaryTablePanel.removeAll();
        if (data == null) {
            return;
        }
        addSummaryRow("Portfolio Market Value", formatGp(data.getPortfolioMarketValue(), false), Color.WHITE);
        addSummaryRow("Unrealised Profit", formatGp(data.getUnrealizedProfit(), true), getProfitColor(data.getUnrealizedProfit()));
        addSummaryRow("Cash Value", formatGp(data.getCashValue(), false), Color.WHITE);
        addSummaryRow("Assets Value", formatGp(data.getAssetsValue(), false), Color.WHITE);
        addSummaryRow("Total items in portfolio", NumberFormat.getIntegerInstance(Locale.US).format(totalItemsInPortfolio), Color.WHITE);
    }

    private void addSummaryRow(String label, String value, Color valueColor) {
        JLabel keyLabel = new JLabel(label);
        keyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        keyLabel.setFont(keyLabel.getFont().deriveFont(19f));

        JLabel valueLabel = new JLabel(value, SwingConstants.RIGHT);
        valueLabel.setForeground(valueColor);
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 19f));

        summaryTablePanel.add(keyLabel);
        summaryTablePanel.add(valueLabel);
    }

    private void renderTable(List<PortfolioItemCardData> items) {
        tableModel.setRowCount(0);
        for (PortfolioItemCardData item : items) {
            long heldQuantity = (long) item.getRuneliteInventoryQuantity() + item.getSuggestionBankQuantity() + item.getRuneliteGeQuantity();
            tableModel.addRow(new Object[]{
                    new ItemCell(item.getItemId(), item.getItemName()),
                    item.getOpenFlipsQuantity(),
                    new HeldQuantityCell(item.getRuneliteInventoryQuantity(), item.getSuggestionBankQuantity(), item.getRuneliteGeQuantity(), heldQuantity),
                    item.getOpenFlipsCount(),
                    formatDuration(item.getHeldMinutes()),
                    item.flipsUnrealizedProfit(),
                    formatUnrealizedRio(item)
            });
        }
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

    private Color getProfitColor(long profit) {
        if (profit > 0) {
            return config.profitAmountColor();
        }
        if (profit < 0) {
            return config.lossAmountColor();
        }
        return Color.WHITE;
    }

    private String formatGp(long amount, boolean signed) {
        String prefix = signed && amount > 0 ? "+" : "";
        return prefix + GP_FORMAT.format(amount) + " gp";
    }

    private String formatDuration(int heldMinutes) {
        Duration duration = Duration.ofMinutes(Math.max(0, heldMinutes));
        long days = duration.toDays();
        long hours = duration.minusDays(days).toHours();
        long minutes = duration.minusDays(days).minusHours(hours).toMinutes();
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return Math.max(1, minutes) + "m";
    }

    private String formatUnrealizedRio(PortfolioItemCardData item) {
        if (item.getUnrealizedUnitProfit() == null) {
            return "Unknown";
        }
        long unitBuyPrice = item.getPostTaxSellUnitPrice() - item.getUnrealizedUnitProfit();
        if (unitBuyPrice <= 0) {
            return "Unknown";
        }
        double rio = (double) item.getUnrealizedUnitProfit() / (double) unitBuyPrice;
        return String.format("%.2f%%", rio * 100.0d);
    }

    private static class ItemCell {
        private final int itemId;
        private final String name;

        private ItemCell(int itemId, String name) {
            this.itemId = itemId;
            this.name = name;
        }
    }

    private static class HeldQuantityCell {
        private final long inventory;
        private final long bank;
        private final long ge;
        private final long total;

        private HeldQuantityCell(long inventory, long bank, long ge, long total) {
            this.inventory = inventory;
            this.bank = bank;
            this.ge = ge;
            this.total = total;
        }
    }

    private static class HeldQuantityCellRenderer extends JPanel implements TableCellRenderer {
        private final JLabel inventoryKeyLabel = new JLabel("Inventory:");
        private final JLabel bankKeyLabel = new JLabel("Bank:");
        private final JLabel geKeyLabel = new JLabel("GE:");
        private final JLabel totalKeyLabel = new JLabel("");
        private final JLabel inventoryValueLabel = new JLabel();
        private final JLabel bankValueLabel = new JLabel();
        private final JLabel geValueLabel = new JLabel();
        private final JLabel totalValueLabel = new JLabel();
        private final JPanel miniTablePanel = new JPanel(new GridLayout(4, 2, 8, 3));

        private HeldQuantityCellRenderer() {
            setLayout(new BorderLayout());
            setBorder(new EmptyBorder(8, 6, 8, 6));

            inventoryKeyLabel.setHorizontalAlignment(SwingConstants.LEFT);
            bankKeyLabel.setHorizontalAlignment(SwingConstants.LEFT);
            geKeyLabel.setHorizontalAlignment(SwingConstants.LEFT);
            totalKeyLabel.setHorizontalAlignment(SwingConstants.LEFT);

            inventoryValueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            bankValueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            geValueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            totalValueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            totalValueLabel.setFont(totalValueLabel.getFont().deriveFont(Font.BOLD));
            totalValueLabel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, ColorScheme.MEDIUM_GRAY_COLOR));

            miniTablePanel.setOpaque(false);
            miniTablePanel.add(inventoryKeyLabel);
            miniTablePanel.add(inventoryValueLabel);
            miniTablePanel.add(bankKeyLabel);
            miniTablePanel.add(bankValueLabel);
            miniTablePanel.add(geKeyLabel);
            miniTablePanel.add(geValueLabel);
            miniTablePanel.add(totalKeyLabel);
            miniTablePanel.add(totalValueLabel);

            JPanel rightAlignWrap = new JPanel(new BorderLayout());
            rightAlignWrap.setOpaque(false);
            rightAlignWrap.add(miniTablePanel, BorderLayout.EAST);
            add(rightAlignWrap, BorderLayout.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Color bg = isSelected ? table.getSelectionBackground() : table.getBackground();
            Color fg = isSelected ? table.getSelectionForeground() : table.getForeground();
            setBackground(bg);
            setOpaque(true);
            miniTablePanel.setBackground(bg);
            inventoryKeyLabel.setForeground(fg);
            bankKeyLabel.setForeground(fg);
            geKeyLabel.setForeground(fg);
            totalKeyLabel.setForeground(fg);
            inventoryValueLabel.setForeground(fg);
            bankValueLabel.setForeground(fg);
            geValueLabel.setForeground(fg);
            totalValueLabel.setForeground(fg);

            if (value instanceof HeldQuantityCell) {
                HeldQuantityCell cell = (HeldQuantityCell) value;
                inventoryValueLabel.setText(NumberFormat.getIntegerInstance(Locale.US).format(cell.inventory));
                bankValueLabel.setText(NumberFormat.getIntegerInstance(Locale.US).format(cell.bank));
                geValueLabel.setText(NumberFormat.getIntegerInstance(Locale.US).format(cell.ge));
                totalValueLabel.setText(NumberFormat.getIntegerInstance(Locale.US).format(cell.total));
            } else {
                inventoryValueLabel.setText("");
                bankValueLabel.setText("");
                geValueLabel.setText("");
                totalValueLabel.setText("");
            }
            return this;
        }
    }
}
