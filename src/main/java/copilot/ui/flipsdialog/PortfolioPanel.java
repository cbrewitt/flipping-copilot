package copilot.ui.flipsdialog;
import static java.util.Comparator.*;
import static javax.swing.JOptionPane.*;
import static javax.swing.SwingUtilities.*;
import static java.util.Map.entry;

import copilot.controller.*;
import static copilot.ui.UIUtilities.*;
import static net.runelite.client.ui.ColorScheme.*;
import copilot.config.*;
import copilot.model.*;
import copilot.rs.*;
import net.runelite.client.callback.*;
import net.runelite.client.ui.*;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.List;

public class PortfolioPanel extends JPanel {
    private static final NumberFormat GP_FORMAT = NumberFormat.getNumberInstance(Locale.US);
    private static final String CONTENT_CARD = "content", LOGIN_PROMPT_CARD = "login";
    private static final String[] COLUMN_NAMES = {
            "Item", "Market value", "Quantity", "Unrealized Profit", "Unrealized ROI", "Avg buy price", "Time held"
    };

    private static final Map<String, Comparator<PortfolioItem>> SORT_COMPARATORS = new HashMap<>(Map.ofEntries(
        entry("Item", comparing(PortfolioItem::getItemName, nullsLast(String.CASE_INSENSITIVE_ORDER))),
        // Numeric columns are pre-reversed so the default DESC direction shows largest-first
        entry("Market value", Comparator.<PortfolioItem>comparingLong(
                i -> i.postTaxSellUnitPrice * (long) i.portfolioQuantity).reversed()),
        entry("Quantity", comparingInt(PortfolioItem::getPortfolioQuantity).reversed()),
        entry("Avg buy price", comparingLong(PortfolioItem::getUnitBuyPrice).reversed()),
        entry("Time held", comparingInt(PortfolioItem::getHeldMinutes)),
        entry("Unrealized Profit", comparingLong(PortfolioItem::portfolioUnrealizedProfit).reversed()),
        entry("Unrealized ROI", comparing(
                PortfolioPanel::calculateUnrealizedRoi,
                nullsLast(Comparator.<Double>naturalOrder().reversed())))
    ));

    private final Items itemController;
    private final CopilotConfig config;
    private final ApiClient api;
    private final Suggestions suggestions;
    private final CopilotLogin copilotLogin;
    private final GameLogin gameLogin;
    private final PortfolioStateRS portfolioStateRS;
    private final BankStateRS bankStateRS;
    private final ClientThread clientThread;
    private final Consumer<Integer> openPriceGraph;
    private final CardLayout cardLayout;
    private final JPanel cardPanel, summaryTablePanel;
    private final PaginatedTablePanel<PortfolioItem> tablePanel;
    private final JLabel autoSyncInfoLabel;
    private final JButton clearPortfolioButton;
    private final Map<Integer, ImageIcon> itemIconCache = new ConcurrentHashMap<>();

    private List<PortfolioItem> currentItems = new ArrayList<>();
    private String sortColumn = "Market value";
    private SortDirection sortDirection = SortDirection.DESC;

    public PortfolioPanel(Items itemController,
                          CopilotConfig config,
                          ApiClient api,
                          Suggestions suggestions,
                          CopilotLogin copilotLogin,
                          GameLogin osrsLoginRs,
                          PortfolioStateRS portfolioStateRS,
                          BankStateRS bankStateRS,
                          ClientThread clientThread,
                          Consumer<Integer> openPriceGraph) {
        this.itemController = itemController; this.config = config; this.api = api; this.suggestions = suggestions;
        this.copilotLogin = copilotLogin; gameLogin = osrsLoginRs; this.portfolioStateRS = portfolioStateRS;
        this.bankStateRS = bankStateRS; this.clientThread = clientThread; this.openPriceGraph = openPriceGraph;

        setLayout(new BorderLayout(0, 12));
        setBackground(DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(10, 10, 10, 10));

        cardLayout = new CardLayout();
        cardPanel = transparentPanel(cardLayout);

        var contentPanel = transparentPanel(new BorderLayout(0, 12));
        contentPanel.setBorder(new EmptyBorder(8, 0, 0, 0));

        var summarySection = transparentPanel(new BorderLayout(28, 0));
        summarySection.setBorder(new EmptyBorder(0, 0, 10, 0));

        summaryTablePanel = transparentPanel(new GridLayout(0, 2, 24, 10));
        summaryTablePanel.setBorder(new EmptyBorder(4, 0, 4, 0));
        summarySection.add(summaryTablePanel, BorderLayout.WEST);

        clearPortfolioButton = new JButton("Remove everything from portfolio");
        clearPortfolioButton.setFont(FontManager.getRunescapeFont()); clearPortfolioButton.setFocusable(false);
        clearPortfolioButton.addActionListener(e -> onClearPortfolioClicked());

        var rightControlsPanel = transparentPanel(new FlowLayout());
        rightControlsPanel.setLayout(new BorderLayout()); rightControlsPanel.setBorder(new EmptyBorder(0, 12, 0, 0));

        var bottomRightWrap = transparentPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        autoSyncInfoLabel = new JLabel();
        autoSyncInfoLabel.setForeground(BRAND_ORANGE); autoSyncInfoLabel.setFont(FontManager.getRunescapeFont());
        autoSyncInfoLabel.setHorizontalAlignment(SwingConstants.LEFT);
        bottomRightWrap.add(autoSyncInfoLabel);

        rightControlsPanel.add(bottomRightWrap, BorderLayout.SOUTH);
        summarySection.add(rightControlsPanel, BorderLayout.CENTER);

        contentPanel.add(summarySection, BorderLayout.NORTH);

        tablePanel = new PaginatedTablePanel<>(COLUMN_NAMES, this::toRow, 40);
        tablePanel.rightControls().add(clearPortfolioButton);
        tablePanel.installHeaderSort(() -> sortColumn, () -> sortDirection, (clickedColumn, newDirection) -> {
            sortColumn = clickedColumn;
            sortDirection = newDirection;
            renderTable();
        });
        tablePanel.installPopupHandler((e, row) -> showPortfolioMenu(e, tablePanel.row(row)));
        tablePanel.rightColumns(2, 6); // Quantity, Time held
        tablePanel.setRenderer(new StyledRenderer() {
            @Override
            protected void style(JTable table, Object value, boolean isSelected, int row) {
                if (value instanceof Long) { setText(formatGp((Long) value, false)); } else if (value == null) {
                    setText("Unknown");
                }
                setHorizontalAlignment(RIGHT);
            }
        }, 1, 5);
        tablePanel.setRenderer(new StyledRenderer() {
            @Override
            protected void style(JTable table, Object value, boolean isSelected, int row) {
                if (value instanceof ItemCell) {
                    ItemCell itemCell = (ItemCell) value;
                    setText(itemCell.name);
                    var cachedIcon = itemIconCache.get(itemCell.itemId);
                    if (cachedIcon != null) { setIcon(cachedIcon); } else {
                        setIcon(null);
                        itemController.loadImage(itemCell.itemId, image -> {
                            if (image != null) {
                                itemIconCache.put(itemCell.itemId, new ImageIcon(image));
                                invokeLater(table::repaint);
                            }
                        });
                    }
                } else {
                    setIcon(null);
                }
            }
        }, 0);

        var profitRenderer = new StyledRenderer() {
            @Override
            protected void style(JTable table, Object value, boolean isSelected, int row) {
                if (value instanceof Long) {
                    long amount = (Long) value;
                    setText(formatGp(amount, true));
                    setHorizontalAlignment(RIGHT);
                    if (!isSelected) { setForeground(getProfitColor(amount, config)); }
                }
            }
        };
        tablePanel.setRenderer(profitRenderer, 3);

        var roiRenderer = new StyledRenderer() {
            @Override
            protected void style(JTable table, Object value, boolean isSelected, int row) {
                setHorizontalAlignment(RIGHT);
                if (value instanceof Double) {
                    double roi = (Double) value;
                    setText(String.format("%.2f%%", roi * 100.0d));
                    if (!isSelected) { setForeground(getProfitColor(roi, config)); }
                } else {
                    setText(value == null ? "Unknown" : value.toString());
                }
            }
        };
        tablePanel.setRenderer(roiRenderer, 4);
        contentPanel.add(tablePanel, BorderLayout.CENTER);

        cardPanel.add(contentPanel, CONTENT_CARD);
        cardPanel.add(DialogUi.centeredMessage("Log into the game to see account portfolio", null, false, 18f), LOGIN_PROMPT_CARD);
        add(cardPanel, BorderLayout.CENTER);

        portfolioStateRS.registerListener(state -> invokeLater(() -> {
            if (gameLogin.get().loggedIn) { renderFromState(state); }
        }));
        gameLogin.registerListener(state -> invokeLater(this::refresh));
        bankStateRS.registerListener(state -> invokeLater(this::refreshAutoSyncLabel));

        refresh();
    }

    public void onTabShown() { refresh(); }

    private void refresh() {
        if (!gameLogin.get().loggedIn) {
            cardLayout.show(cardPanel, LOGIN_PROMPT_CARD);
            clearPortfolioButton.setEnabled(false);
            revalidate();
            repaint();
            return;
        }
        cardLayout.show(cardPanel, CONTENT_CARD);
        Integer accountId = copilotLogin.get().getAccountId(gameLogin.get().displayName);
        clearPortfolioButton.setEnabled(accountId != null && accountId != -1);
        refreshAutoSyncLabel();
        renderFromState(portfolioStateRS.get());
    }

    private void onClearPortfolioClicked() {
        Integer accountId = copilotLogin.get().getAccountId(gameLogin.get().displayName);
        if (accountId == null || accountId == -1) { return; }
        int choice = showConfirmDialog(
                this,
                "Are you sure you want to remove all items from your portfolio?",
                "Confirm",
                YES_NO_OPTION);
        if (choice != YES_OPTION) { return; }
        clearPortfolioButton.setEnabled(false);
        api.asyncClearAccountPortfolio(
                accountId,
                (userId, result) -> invokeLater(() -> {
                    portfolioStateRS.updatePortfolioState(suggestions.getSuggestion(), result);
                    suggestions.setSuggestionNeeded(true);
                    clearPortfolioButton.setEnabled(true);
                }),
                error -> invokeLater(() -> {
                    clearPortfolioButton.setEnabled(true);
                    showMessageDialog(this, "Failed to clear portfolio. Please try again.", "Error", ERROR_MESSAGE);
                })
        );
    }

    private void refreshAutoSyncLabel() {
        String labelText = bankStateRS.get().loaded
                ? "Bank loaded. Full quantity syncing enabled. Note: items are excluded from syncing whilst active in one of your Grand Exchange slots."
                : "Please open your bank once to enable more accurate quantity syncing.";
        autoSyncInfoLabel.setText(String.format("<html><div style='width: 560px; text-align: left;'>%s</div></html>", labelText));
    }

    private List<PortfolioItem> filterInPortfolioItems(List<PortfolioItem> items) {
        List<PortfolioItem> filteredItems = new ArrayList<>();
        for (PortfolioItem item : items) {
            if (item.isInPortfolio()) { filteredItems.add(item); }
        }
        return filteredItems;
    }

    private void renderFromState(PortfolioState state) {
        List<PortfolioItem> items = new ArrayList<>(state.itemCardDataByItemId.values());
        currentItems = filterInPortfolioItems(items);
        var sm = state.summaryData;
        renderSummary(sm, currentItems.size());
        renderTable();
        revalidate();
        repaint();
    }

    private void renderSummary(PortfolioSummary data, int totalItemsInPortfolio) {
        summaryTablePanel.removeAll();
        if (data == null) { return; }
        addSummaryRow("Portfolio Market Value", formatGp(data.portfolioMarketValue, false), config.profitAmountColor());
        addSummaryRow("Unrealized Profit", formatGp(data.unrealizedProfit, true), getProfitColor(data.unrealizedProfit, config));
        addSummaryRow("Cash Value", formatGp(data.cashValue, false));
        addSummaryRow("Cash in Buy Offers", formatGp(data.lockedBuyCash, false));
        addSummaryRow("Assets Value", formatGp(data.assetsValue, false));
        addSummaryRow("Unique Items in Portfolio", NumberFormat.getIntegerInstance(Locale.US).format(totalItemsInPortfolio));
    }

    private void addSummaryRow(String label, String value) { addSummaryRow(label, value, LIGHT_GRAY_COLOR); }

    private void addSummaryRow(String label, String value, Color valueColor) {
        var keyLabel = coloredLabel(label, LIGHT_GRAY_COLOR); keyLabel.setFont(FontManager.getRunescapeFont());

        var valueLabel = new JLabel(value, SwingConstants.RIGHT);
        valueLabel.setForeground(valueColor); valueLabel.setFont(FontManager.getRunescapeBoldFont());

        summaryTablePanel.add(keyLabel);
        summaryTablePanel.add(valueLabel);
    }

    private void renderTable() {
        List<PortfolioItem> sortedItems = new ArrayList<>(currentItems);
        FilterSortUtil.sort(sortedItems, SORT_COMPARATORS, sortColumn, sortDirection);
        tablePanel.setRows(sortedItems);
    }

    private Object[] toRow(PortfolioItem item) {
        var nf = NumberFormat.getIntegerInstance(Locale.US);
        long avgBuyPrice = item.unitBuyPrice;
        return new Object[]{
                new ItemCell(item.itemId, item.itemName),
                item.postTaxSellUnitPrice * item.portfolioQuantity,
                nf.format(item.portfolioQuantity),
                item.portfolioUnrealizedProfit(),
                calculateUnrealizedRoi(item),
                avgBuyPrice > 0 ? avgBuyPrice : null,
                formatDurationMinutes(item.heldMinutes)
        };
    }

    private void showPortfolioMenu(MouseEvent e, PortfolioItem item) {
        var popupMenu = new JPopupMenu();
        buildContextMenu(popupMenu, item);
        popupMenu.show(e.getComponent(), e.getX(), e.getY());
    }

    private void buildContextMenu(JPopupMenu menu, PortfolioItem item) {
        int portfolioQty = item.portfolioQuantity;

        DialogUi.addMenuItem(menu, "Show price graph", () -> openPriceGraph.accept(item.itemId));

        if (portfolioQty > 0) {
            DialogUi.addMenuItem(menu, "Remove from portfolio", () -> togglePortfolio(item.itemId, PortfolioRequest.REMOVE, 0));
        }

        if (portfolioQty > 1) {
            DialogUi.addMenuItem(menu, "Remove X from portfolio", () -> {
                String input = showInputDialog(this, "Quantity to remove:", "Remove from portfolio", PLAIN_MESSAGE);
                if (input != null) {
                    try {
                        int qty = Integer.parseInt(input.trim());
                        if (qty > 0) { togglePortfolio(item.itemId, PortfolioRequest.REMOVE, qty); }
                    } catch (NumberFormatException ignored) {
                    }
                }
            });
        }
    }

    private void togglePortfolio(int itemId, int portfolioId, int quantity) {
        Integer accountId = copilotLogin.get().getAccountId(gameLogin.get().displayName);
        if (accountId == null || accountId == -1) { return; }

        clientThread.invokeLater(() -> {
            var runeliteInventory = itemController.getRunliteInventory();
            int bagQuantity = runeliteInventory == null ? 0 : Math.max(0, runeliteInventory.getOrDefault(itemId, 0));
            int bankQuantity = bankStateRS.get().loaded
                    ? Math.max(0, bankStateRS.get().items.getOrDefault(itemId, 0))
                    : -1;

            var request = new PortfolioRequest(accountId, itemId, portfolioId, bagQuantity, bankQuantity, quantity);
            api.toggleItemPortfolioAsync(
                    request,
                    (userId, result) -> {
                        portfolioStateRS.updatePortfolioState(suggestions.getSuggestion(), result);
                        suggestions.setSuggestionNeeded(true);
                    },
                    error -> {
                    }
            );
            return true;
        });
    }

    private String formatGp(long amount, boolean signed) {
        String prefix = signed && amount > 0 ? "+" : "";
        return prefix + GP_FORMAT.format(amount) + " gp";
    }

    private static Double calculateUnrealizedRoi(PortfolioItem item) {
        if (item.unrealizedUnitProfit == null) { return null; }
        long unitBuyPrice = item.postTaxSellUnitPrice - item.unrealizedUnitProfit;
        if (unitBuyPrice <= 0) { return null; }
        return (double) item.unrealizedUnitProfit / (double) unitBuyPrice;
    }

    @lombok.RequiredArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    private static class ItemCell {
        private final int itemId;
        private final String name;

    }

}
