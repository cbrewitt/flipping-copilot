package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.controller.ApiRequestHandler;
import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.model.FlipManager;
import com.flippingcopilot.model.FlipStatus;
import com.flippingcopilot.model.FlipV2;
import com.flippingcopilot.model.HttpResponseException;
import com.flippingcopilot.model.PortfolioId;
import com.flippingcopilot.model.SortDirection;
import com.flippingcopilot.model.GeHistoryRow;
import com.flippingcopilot.model.GeHistoryState;
import com.flippingcopilot.rs.CopilotLoginRS;
import com.flippingcopilot.rs.GeHistoryStateRS;
import com.flippingcopilot.rs.OsrsLoginRS;
import com.flippingcopilot.ui.Spinner;
import com.flippingcopilot.ui.components.ItemSearchMultiSelect;
import com.flippingcopilot.util.ProfitCalculator;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    private static final String SECTIONS_CARD = "sections";
    private static final String LOGIN_PROMPT_CARD = "login";

    private static final long MAX_AGE_SECONDS = 30L * 24 * 60 * 60;

    private final FlipManager flipsManager;
    private final ItemController itemController;
    private final CopilotLoginRS copilotLoginRS;
    private final OsrsLoginRS osrsLoginRS;
    private final ApiRequestHandler apiRequestHandler;
    private final ExecutorService executorService;
    private final FlippingCopilotConfig config;
    private final GeHistoryStateRS geHistoryStateRS;

    private final Spinner spinner;
    private final JPanel spinnerOverlay;
    private final ItemSearchMultiSelect searchField;
    private final JLabel geHistoryStatusLabel;
    private final CardLayout cardLayout;
    private final JPanel cardPanel;

    private final Section disappearedSection;
    private final Section ghostSection;

    private Set<Integer> filteredItems = new HashSet<>();

    public MissedFlipsPanel(OsrsLoginRS osrsLoginRS,
                            FlipManager flipsManager,
                            ItemController itemController,
                            CopilotLoginRS copilotLoginRS,
                            ExecutorService executorService,
                            FlippingCopilotConfig config,
                            ApiRequestHandler apiRequestHandler,
                            GeHistoryStateRS geHistoryStateRS) {
        this.osrsLoginRS = osrsLoginRS;
        this.flipsManager = flipsManager;
        this.itemController = itemController;
        this.copilotLoginRS = copilotLoginRS;
        this.executorService = executorService;
        this.config = config;
        this.apiRequestHandler = apiRequestHandler;
        this.geHistoryStateRS = geHistoryStateRS;

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

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        geHistoryStatusLabel = new JLabel();
        geHistoryStatusLabel.setForeground(ColorScheme.BRAND_ORANGE);
        geHistoryStatusLabel.setVisible(false);
        rightPanel.add(geHistoryStatusLabel);
        topPanel.add(rightPanel, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

        disappearedSection = new Section("Flips with potential missed transactions", true);
        ghostSection = new Section("Full missed flips", false);

        JPanel sectionsPanel = new JPanel(new GridLayout(2, 1, 0, 10));
        sectionsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        sectionsPanel.add(disappearedSection.container);
        sectionsPanel.add(ghostSection.container);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        cardPanel.add(sectionsPanel, SECTIONS_CARD);
        cardPanel.add(buildLoginPromptPanel(), LOGIN_PROMPT_CARD);

        spinner = new Spinner();
        spinner.show();
        spinnerOverlay = new JPanel(new GridBagLayout());
        spinnerOverlay.setBackground(ColorScheme.DARK_GRAY_COLOR);
        spinnerOverlay.setOpaque(true);
        spinnerOverlay.add(spinner);
        spinnerOverlay.setVisible(false);

        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        layeredPane.setOpaque(true);
        layeredPane.setLayout(new OverlayLayout(layeredPane));
        layeredPane.add(spinnerOverlay, JLayeredPane.MODAL_LAYER);
        layeredPane.add(cardPanel, JLayeredPane.DEFAULT_LAYER);

        add(layeredPane, BorderLayout.CENTER);

        osrsLoginRS.registerListener(state -> SwingUtilities.invokeLater(this::refresh));
        geHistoryStateRS.registerListener(state -> SwingUtilities.invokeLater(this::updateGeHistoryStatusLabel));
        updateGeHistoryStatusLabel();
    }

    private void updateGeHistoryStatusLabel() {
        GeHistoryState state = geHistoryStateRS.get();
        if (state == null || !state.isLoaded() || state.getCapturedAt() <= 0) {
            geHistoryStatusLabel.setVisible(false);
            return;
        }
        geHistoryStatusLabel.setText("GE history known since " + formatEpoch(state.getCapturedAt()));
        geHistoryStatusLabel.setVisible(true);
    }

    private JPanel buildLoginPromptPanel() {
        return DialogUi.loginPrompt("Log into the game to view missed flips", ColorScheme.DARK_GRAY_COLOR, true);
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
            List<FlipV2> disappearedFlips;
            List<FlipV2> ghostFlips;
            if (accountId == null) {
                disappearedFlips = Collections.emptyList();
                ghostFlips = Collections.emptyList();
            } else {
                List<FlipV2> all = new ArrayList<>(flipsManager.getMissedFlipsForAccount(accountId));
                int cutoff = (int) (Instant.now().getEpochSecond() - MAX_AGE_SECONDS);
                all.removeIf(f -> f.getUpdatedTime() < cutoff);
                if (!filteredItems.isEmpty()) {
                    all.removeIf(f -> !filteredItems.contains(f.getItemId()));
                }
                disappearedFlips = new ArrayList<>();
                ghostFlips = new ArrayList<>();
                for (FlipV2 f : all) {
                    if (PortfolioId.isDisappeared(f.getPortfolioId())) {
                        disappearedFlips.add(f);
                    } else if (f.getPortfolioId() == PortfolioId.GHOST && f.getClosedQuantity() > 0) {
                        ghostFlips.add(f);
                    }
                }
            }
            boolean showLoginPrompt = accountId == null;
            SwingUtilities.invokeLater(() -> {
                cardLayout.show(cardPanel, showLoginPrompt ? LOGIN_PROMPT_CARD : SECTIONS_CARD);
                disappearedSection.update(disappearedFlips);
                ghostSection.update(ghostFlips);
            });
        });
    }

    private void setSpinnerVisible(boolean visible) {
        SwingUtilities.invokeLater(() -> {
            spinnerOverlay.setVisible(visible);
            disappearedSection.setTableEnabled(!visible);
            ghostSection.setTableEnabled(!visible);
        });
    }

    private String formatTimestamp(int epochSeconds) {
        if (epochSeconds == 0) {
            return "N/A";
        }
        return formatEpoch(epochSeconds);
    }

    private void showFlipMenu(MouseEvent e, FlipV2 flip, boolean isDisappearedSection) {
        JPopupMenu menu = new JPopupMenu();
        if (isDisappearedSection) {
            String flipOsrsDisplayName = copilotLoginRS.get().getDisplayName(flip.getAccountId());
            if (!canAddMissedSell(flipOsrsDisplayName, flip)) {
                return;
            }
            JMenuItem missedSellTransaction = new JMenuItem("Add missed sell transaction");
            missedSellTransaction.addActionListener(evt -> promptAndSubmitMissedSell(flip));
            menu.add(missedSellTransaction);
        } else {
            JMenuItem reviveFlip = new JMenuItem("Revive this flip");
            reviveFlip.addActionListener(evt -> promptAndSubmitReviveGhost(flip));
            menu.add(reviveFlip);
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

    private void promptAndSubmitMissedSell(FlipV2 flip) {
        int qty = flip.getOpenedQuantity() - flip.getClosedQuantity();
        long suggestedPrice = (long) (flip.getAvgBuyPrice() * 1.02);

        List<GeHistoryRow> sellMatches = findGeHistorySellMatches(flip);

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

        JTextField priceField = new JTextField(String.valueOf(suggestedPrice), 10);
        JComboBox<PriceOption> priceCombo = null;
        if (!sellMatches.isEmpty()) {
            priceCombo = new JComboBox<>();
            for (GeHistoryRow row : sellMatches) {
                priceCombo.addItem(new PriceOption(row.getPrice(), row.getQuantity(), false));
            }
            priceCombo.addItem(new PriceOption(0, 0, true));
            gbc.gridx = 1;
            dialogPanel.add(priceCombo, gbc);
            gbc.gridx = 1; gbc.gridy = 3;
            dialogPanel.add(priceField, gbc);

            JComboBox<PriceOption> comboRef = priceCombo;
            applyPriceOption((PriceOption) priceCombo.getSelectedItem(), priceField);
            priceCombo.addActionListener(e -> applyPriceOption((PriceOption) comboRef.getSelectedItem(), priceField));
        } else {
            gbc.gridx = 1;
            dialogPanel.add(priceField, gbc);
        }

        int result = JOptionPane.showConfirmDialog(this,
                dialogPanel,
                "Add Missed Sell Transaction",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        long price;
        PriceOption selected = priceCombo == null ? null : (PriceOption) priceCombo.getSelectedItem();
        if (selected != null && !selected.manual) {
            price = selected.price;
        } else {
            try {
                price = Long.parseLong(priceField.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this,
                        "Please enter a valid number for the price.",
                        "Invalid Price",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        if (price <= 0) {
            JOptionPane.showMessageDialog(this,
                    "Price must be a positive number.",
                    "Invalid Price",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        long avgBuy = flip.getAvgBuyPrice();
        long estimatedProfit = (long) qty * (ProfitCalculator.getPostTaxPrice(flip.getItemId(), price) - avgBuy);
        if (!validateProfit(estimatedProfit, flip, price)) {
            return;
        }

        setSpinnerVisible(true);
        log.info("Adding missed sale for flip {} qty={} price={}", flip.getId(), qty, price);

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
        apiRequestHandler.asyncAddMissedSale(flip.getId(), price, qty, onSuccess, onFailure);
    }

    private List<GeHistoryRow> findGeHistorySellMatches(FlipV2 flip) {
        GeHistoryState state = geHistoryStateRS.get();
        if (state == null || !state.isLoaded() || state.getCapturedAt() <= flip.getUpdatedTime()) {
            return Collections.emptyList();
        }
        List<GeHistoryRow> matches = new ArrayList<>();
        for (GeHistoryRow row : state.getRows()) {
            if (!row.isBuy() && row.getItemId() == flip.getItemId()) {
                matches.add(row);
            }
        }
        return matches;
    }

    private static void applyPriceOption(PriceOption option, JTextField priceField) {
        if (option == null) {
            return;
        }
        if (option.manual) {
            priceField.setEnabled(true);
        } else {
            priceField.setText(String.valueOf(option.price));
            priceField.setEnabled(false);
        }
    }

    private static class PriceOption {
        final long price;
        final int quantity;
        final boolean manual;

        PriceOption(long price, int quantity, boolean manual) {
            this.price = price;
            this.quantity = quantity;
            this.manual = manual;
        }

        @Override
        public String toString() {
            if (manual) {
                return "Manual...";
            }
            return String.format(Locale.US, "%,d gp (qty %d)", price, quantity);
        }
    }

    private void promptAndSubmitReviveGhost(FlipV2 flip) {
        int result = JOptionPane.showConfirmDialog(this,
                "Revive this ghost flip into your copilot portfolio?\n"
                        + "Item: " + flip.getCachedItemName(),
                "Confirm Revive",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (result != JOptionPane.YES_OPTION) {
            return;
        }
        setSpinnerVisible(true);
        log.info("reviving ghost flip {}", flip.getId());
        BiConsumer<Integer, List<FlipV2>> onSuccess = (userId, flips) -> {
            flipsManager.mergeFlips(flips, userId);
            setSpinnerVisible(false);
            refresh();
        };
        Consumer<HttpResponseException> onFailure = (r) -> {
            setSpinnerVisible(false);
            JOptionPane.showMessageDialog(this,
                    "Failed to revive flip. Please try again.",
                    "Revive Error",
                    JOptionPane.ERROR_MESSAGE);
        };
        apiRequestHandler.asyncReviveGhostFlip(flip.getId(), onSuccess, onFailure);
    }

    private boolean validateProfit(long profit, FlipV2 flip, long price) {
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

    private Object[] toRow(FlipV2 flip) {
        return new Object[]{
                formatTimestamp(flip.getOpenedTime()),
                formatTimestamp(flip.getClosedTime()),
                flip.getCachedItemName(),
                flip.getStatus().name(),
                flip.getOpenedQuantity(),
                flip.getClosedQuantity(),
                FlipTableUtil.averageBuy(flip),
                FlipTableUtil.averageSell(flip),
                flip.getTaxPaid(),
                flip.getProfit(),
                FlipTableUtil.profitEach(flip)
        };
    }

    private class Section {
        final JPanel container;
        final PaginatedTablePanel<FlipV2> tablePanel;
        final boolean isDisappearedSection;
        List<FlipV2> currentFlips = new ArrayList<>();
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

            JLabel titleLabel = new JLabel(title);
            titleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            titleLabel.setBorder(new EmptyBorder(10, 8, 10, 8));

            container = new JPanel(new BorderLayout());
            container.setBackground(ColorScheme.DARK_GRAY_COLOR);
            container.add(titleLabel, BorderLayout.NORTH);
            container.add(tablePanel, BorderLayout.CENTER);
        }

        void update(List<FlipV2> flips) {
            currentFlips = new ArrayList<>(flips);
            rerender();
        }

        void setTableEnabled(boolean enabled) {
            tablePanel.table().setEnabled(enabled);
        }

        private void rerender() {
            FilterSortUtil.sort(currentFlips, FlipTableUtil.COMPARATORS, sortColumn, sortDirection);
            tablePanel.setRows(new ArrayList<>(currentFlips));
        }
    }
}
