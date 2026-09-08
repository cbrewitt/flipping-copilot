package copilot.ui.flipsdialog;
import static javax.swing.JOptionPane.*;
import static javax.swing.SwingUtilities.*;

import java.util.function.*;
import copilot.controller.*;
import static copilot.ui.UIUtilities.*;
import static net.runelite.client.ui.ColorScheme.*;
import copilot.config.*;
import copilot.model.*;
import copilot.rs.*;
import copilot.ui.*;
import copilot.ui.components.*;
import copilot.util.*;
import lombok.extern.slf4j.*;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.text.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static copilot.util.DateUtil.formatEpoch;
import static copilot.util.DateUtil.formatEpochOrNa;
import java.util.List;

@Slf4j
public class MissedFlipsPanel extends JPanel {

    private static final NumberFormat GP_FORMAT = NumberFormat.getNumberInstance(Locale.US);
    private static final String[] COLUMN_NAMES = {
            "First buy time", "Last sell time", "Item", "Status", "Bought", "Sold",
            "Avg. buy price", "Avg. sell price", "Tax", "Profit", "Profit ea."
    };

    private static final String SECTIONS_CARD = "sections", LOGIN_PROMPT_CARD = "login";

    private static final long MAX_AGE_SECONDS = 30L * 24 * 60 * 60;

    private final FlipManager flipsManager;
    private final Items itemController;
    private final CopilotLogin copilotLogin;
    private final GameLogin gameLogin;
    private final ApiClient api;
    private final ExecutorService executor;
    private final CopilotConfig config;
    private final GeHistoryStateRS geHistoryStateRS;

    private final DialogUi.BusyPane busyPane;
    private final ItemSearchMultiSelect searchField;
    private final JLabel geHistoryStatusLabel;
    private final CardLayout cardLayout;
    private final JPanel cardPanel;

    private final Section disappearedSection, ghostSection;

    private Set<Integer> filteredItems = new HashSet<>();

    public MissedFlipsPanel(GameLogin gameLogin,
                            FlipManager flipsManager,
                            Items itemController,
                            CopilotLogin copilotLogin,
                            ExecutorService executor,
                            CopilotConfig config,
                            ApiClient api,
                            GeHistoryStateRS geHistoryStateRS) {
        this.gameLogin = gameLogin; this.flipsManager = flipsManager; this.itemController = itemController;
        this.copilotLogin = copilotLogin; this.executor = executor; this.config = config; this.api = api;
        this.geHistoryStateRS = geHistoryStateRS;

        setLayout(new BorderLayout());
        setBackground(DARK_GRAY_COLOR);

        var topPanel = darkPanel(new BorderLayout(), DARK_GRAY_COLOR);
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        var leftPanel = darkPanel(new FlowLayout(FlowLayout.LEFT, 0, 0), DARK_GRAY_COLOR);

        searchField = ItemSearchMultiSelect.itemsFilter(this, itemController,
                () -> new HashSet<>(filteredItems), this::setFilteredItems);

        leftPanel.add(searchField);
        topPanel.add(leftPanel, BorderLayout.WEST);

        var rightPanel = darkPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0), DARK_GRAY_COLOR);
        geHistoryStatusLabel = new JLabel();
        geHistoryStatusLabel.setForeground(BRAND_ORANGE); geHistoryStatusLabel.setVisible(false);
        rightPanel.add(geHistoryStatusLabel);
        topPanel.add(rightPanel, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

        disappearedSection = new Section("Flips with potential missed transactions", true);
        ghostSection = new Section("Full missed flips", false);

        var sectionsPanel = darkPanel(new GridLayout(2, 1, 0, 10), DARK_GRAY_COLOR);
        sectionsPanel.add(disappearedSection.container);
        sectionsPanel.add(ghostSection.container);

        cardLayout = new CardLayout();
        cardPanel = darkPanel(cardLayout, DARK_GRAY_COLOR);
        cardPanel.add(sectionsPanel, SECTIONS_CARD);
        cardPanel.add(DialogUi.centeredMessage("Log into the game to view missed flips", DARK_GRAY_COLOR, true, 18f), LOGIN_PROMPT_CARD);

        busyPane = new DialogUi.BusyPane(cardPanel);
        add(busyPane, BorderLayout.CENTER);

        gameLogin.registerListener(state -> invokeLater(this::refresh));
        geHistoryStateRS.registerListener(state -> invokeLater(this::updateGeHistoryStatusLabel));
        updateGeHistoryStatusLabel();
    }

    private void updateGeHistoryStatusLabel() {
        var state = geHistoryStateRS.get();
        if (state == null || !state.loaded || state.capturedAt <= 0) {
            geHistoryStatusLabel.setVisible(false);
            return;
        }
        geHistoryStatusLabel.setText("GE history known since " + formatEpoch(state.capturedAt));
        geHistoryStatusLabel.setVisible(true);
    }

    private void setFilteredItems(Set<Integer> items) {
        filteredItems = items == null ? new HashSet<>() : new HashSet<>(items);
        refresh();
    }

    public void onTabShown() { refresh(); }

    private Integer resolveAccountId() {
        if (gameLogin == null || gameLogin.get() == null || !gameLogin.get().loggedIn) { return null; }
        String displayName = gameLogin.get().displayName;
        if (displayName == null) { return null; }
        var map = copilotLogin.get().displayNameToAccountId;
        if (map == null) { return null; }
        return map.get(displayName);
    }

    private void refresh() {
        executor.submit(() -> {
            Integer accountId = resolveAccountId();
            List<Flip> disappearedFlips, ghostFlips;
            if (accountId == null) {
                disappearedFlips = Collections.emptyList();
                ghostFlips = Collections.emptyList();
            } else {
                List<Flip> all = new ArrayList<>(flipsManager.getMissedFlipsForAccount(accountId));
                int cutoff = (int) (Instant.now().getEpochSecond() - MAX_AGE_SECONDS);
                all.removeIf(f -> f.updatedTime < cutoff);
                if (!filteredItems.isEmpty()) { all.removeIf(f -> !filteredItems.contains(f.itemId)); }
                disappearedFlips = new ArrayList<>();
                ghostFlips = new ArrayList<>();
                for (Flip f : all) {
                    if (PortfolioId.isDisappeared(f.portfolioId)) { disappearedFlips.add(f); } else if (f.portfolioId == PortfolioId.GHOST && f.closedQuantity > 0) {
                        ghostFlips.add(f);
                    }
                }
            }
            boolean showLoginPrompt = accountId == null;
            invokeLater(() -> {
                cardLayout.show(cardPanel, showLoginPrompt ? LOGIN_PROMPT_CARD : SECTIONS_CARD);
                disappearedSection.update(disappearedFlips);
                ghostSection.update(ghostFlips);
            });
        });
    }

    private void setSpinnerVisible(boolean visible) {
        invokeLater(() -> {
            busyPane.overlay.setVisible(visible);
            disappearedSection.setTableEnabled(!visible);
            ghostSection.setTableEnabled(!visible);
        });
    }

    private void showFlipMenu(MouseEvent e, Flip flip, boolean isDisappearedSection) {
        var menu = new JPopupMenu();
        if (isDisappearedSection) {
            String flipOsrsDisplayName = copilotLogin.get().getDisplayName(flip.accountId);
            if (!canAddMissedSell(flipOsrsDisplayName, flip)) { return; }
            DialogUi.addMenuItem(menu, "Add missed sell transaction", () -> promptAndSubmitMissedSell(flip));
        } else {
            DialogUi.addMenuItem(menu, "Revive this flip", () -> promptAndSubmitReviveGhost(flip));
        }
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private boolean canAddMissedSell(String flipOsrsDisplayName, Flip flip) {
        if (flipOsrsDisplayName == null) { return false; }
        if (FlipStatus.FINISHED.equals(flip.status)) { return false; }
        if (flip.openedQuantity - flip.closedQuantity <= 0) { return false; }
        return gameLogin.get().loggedIn && Objects.equals(flipOsrsDisplayName, gameLogin.get().displayName);
    }

    private void promptAndSubmitMissedSell(Flip flip) {
        int qty = flip.openedQuantity - flip.closedQuantity;
        long suggestedPrice = (long) (flip.getAvgBuyPrice() * 1.02);

        var sellMatches = findGeHistorySellMatches(flip);

        var dialogPanel = new JPanel(new GridBagLayout());
        var gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5); gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0; gbc.gridy = 0;
        dialogPanel.add(new JLabel("Item:"), gbc);
        gbc.gridx = 1;
        dialogPanel.add(new JLabel(flip.cachedItemName), gbc);
        gbc.gridx = 0; gbc.gridy = 1;
        dialogPanel.add(new JLabel("Quantity:"), gbc);
        gbc.gridx = 1;
        dialogPanel.add(new JLabel(String.valueOf(qty)), gbc);
        gbc.gridx = 0; gbc.gridy = 2;
        dialogPanel.add(new JLabel("Sell Price:"), gbc);

        var priceField = new JTextField(String.valueOf(suggestedPrice), 10);
        JComboBox<PriceOption> priceCombo = null;
        if (!sellMatches.isEmpty()) {
            priceCombo = new JComboBox<>();
            for (GeHistoryRow row : sellMatches) {
                priceCombo.addItem(new PriceOption(row.price, row.quantity, false));
            }
            priceCombo.addItem(new PriceOption(0, 0, true));
            gbc.gridx = 1;
            dialogPanel.add(priceCombo, gbc);
            gbc.gridx = 1; gbc.gridy = 3;
            dialogPanel.add(priceField, gbc);

            var comboRef = priceCombo;
            applyPriceOption((PriceOption) priceCombo.getSelectedItem(), priceField);
            priceCombo.addActionListener(e -> applyPriceOption((PriceOption) comboRef.getSelectedItem(), priceField));
        } else {
            gbc.gridx = 1;
            dialogPanel.add(priceField, gbc);
        }

        int result = showConfirmDialog(this, dialogPanel, "Add Missed Sell Transaction", YES_NO_OPTION, PLAIN_MESSAGE);

        if (result != YES_OPTION) { return; }

        long price;
        PriceOption selected = priceCombo == null ? null : (PriceOption) priceCombo.getSelectedItem();
        if (selected != null && !selected.manual) { price = selected.price; } else {
            try {
                price = Long.parseLong(priceField.getText().trim());
            } catch (NumberFormatException ex) {
                showMessageDialog(this, "Please enter a valid number for the price.", "Invalid Price", ERROR_MESSAGE);
                return;
            }
        }
        if (price <= 0) {
            showMessageDialog(this, "Price must be a positive number.", "Invalid Price", ERROR_MESSAGE);
            return;
        }

        long avgBuy = flip.getAvgBuyPrice();
        long estimatedProfit = (long) qty * (ProfitCalculator.getPostTaxPrice(flip.itemId, price) - avgBuy);
        if (!validateProfit(estimatedProfit, flip, price)) { return; }

        setSpinnerVisible(true);
        log.info("Adding missed sale for flip {} qty={} price={}", flip.id, qty, price);

        api.asyncAddMissedSale(flip.id, price, qty, this::mergeMissedFlips,
                failure("Failed to add sell transaction. Please try again.", "Transaction Error"));
    }

    private List<GeHistoryRow> findGeHistorySellMatches(Flip flip) {
        var state = geHistoryStateRS.get();
        if (state == null || !state.loaded || state.capturedAt <= flip.updatedTime) { return Collections.emptyList(); }
        List<GeHistoryRow> matches = new ArrayList<>();
        for (GeHistoryRow row : state.rows) {
            if (!row.buy && row.itemId == flip.itemId) { matches.add(row); }
        }
        return matches;
    }

    private static void applyPriceOption(PriceOption option, JTextField priceField) {
        if (option == null) { return; }
        if (option.manual) { priceField.setEnabled(true); } else {
            priceField.setText(String.valueOf(option.price)); priceField.setEnabled(false);
        }
    }

    @lombok.RequiredArgsConstructor(access = lombok.AccessLevel.PACKAGE)
    private static class PriceOption {
        final long price;
        final int quantity;
        final boolean manual;

        @Override
        public String toString() {
            if (manual) { return "Manual..."; }
            return String.format(Locale.US, "%,d gp (qty %d)", price, quantity);
        }
    }

    private void promptAndSubmitReviveGhost(Flip flip) {
        int result = showConfirmDialog(this,
                "Revive this ghost flip into your copilot portfolio?\n"
                        + "Item: " + flip.cachedItemName,
                "Confirm Revive",
                YES_NO_OPTION,
                QUESTION_MESSAGE);
        if (result != YES_OPTION) { return; }
        setSpinnerVisible(true);
        log.info("reviving ghost flip {}", flip.id);
        api.asyncReviveGhostFlip(flip.id, this::mergeMissedFlips,
                failure("Failed to revive flip. Please try again.", "Revive Error"));
    }

    private void mergeMissedFlips(int userId, List<Flip> flips) {
        flipsManager.mergeFlips(flips, userId);
        setSpinnerVisible(false);
        refresh();
    }

    private Consumer<HttpResponseException> failure(String message, String title) {
        return error -> {
            setSpinnerVisible(false);
            showMessageDialog(this, message, title, ERROR_MESSAGE);
        };
    }

    private boolean validateProfit(long profit, Flip flip, long price) {
        long absProfit = Math.abs(profit), avgBuyPrice = flip.getAvgBuyPrice();
        if (absProfit > 10_000_000L || (avgBuyPrice > 0 && price > avgBuyPrice * 5L)) {
            showMessageDialog(this,
                    "The estimated profit/loss (" + GP_FORMAT.format(absProfit) + " gp) is too large. " +
                            "Please double-check the sell price.",
                    "Profit Too Large",
                    ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    private Object[] toRow(Flip flip) {
        return new Object[]{
                formatEpochOrNa(flip.openedTime),
                formatEpochOrNa(flip.closedTime),
                flip.cachedItemName,
                flip.status.name(),
                flip.openedQuantity,
                flip.closedQuantity,
                FlipTableUtil.averageBuy(flip),
                FlipTableUtil.averageSell(flip),
                flip.taxPaid,
                flip.profit,
                FlipTableUtil.profitEach(flip)
        };
    }

    private class Section {
        final JPanel container;
        final PaginatedTablePanel<Flip> tablePanel;
        final boolean isDisappearedSection;
        List<Flip> currentFlips = new ArrayList<>();
        String sortColumn = "Last sell time";
        SortDirection sortDirection = SortDirection.DESC;

        Section(String title, boolean isDisappearedSection) {
            this.isDisappearedSection = isDisappearedSection;

            tablePanel = new PaginatedTablePanel<>(COLUMN_NAMES, MissedFlipsPanel.this::toRow);
            tablePanel.setTopControlsVisible(false);
            tablePanel.installHeaderSort(
                    () -> sortColumn,
                    () -> sortDirection,
                    (clickedColumn, newDirection) -> {
                        sortColumn = clickedColumn;
                        sortDirection = newDirection;
                        rerender();
                    });
            tablePanel.installPopupHandler((e, row) ->
                    showFlipMenu(e, tablePanel.row(row), Section.this.isDisappearedSection));

            tablePanel.moneyColumns(GP_FORMAT, true, 6, 7, 8, 10);
            tablePanel.profitColumns(GP_FORMAT, config, 9);
            tablePanel.centerColumns(3, 4, 5);

            var titleLabel = coloredLabel(title, LIGHT_GRAY_COLOR); titleLabel.setBorder(new EmptyBorder(10, 8, 10, 8));

            container = darkPanel(new BorderLayout(), DARK_GRAY_COLOR);
            container.add(titleLabel, BorderLayout.NORTH);
            container.add(tablePanel, BorderLayout.CENTER);
        }

        void update(List<Flip> flips) {
            currentFlips = new ArrayList<>(flips);
            rerender();
        }

        void setTableEnabled(boolean enabled) { tablePanel.table().setEnabled(enabled); }

        private void rerender() {
            FilterSortUtil.sort(currentFlips, FlipTableUtil.COMPARATORS, sortColumn, sortDirection);
            tablePanel.setRows(new ArrayList<>(currentFlips));
        }
    }
}
