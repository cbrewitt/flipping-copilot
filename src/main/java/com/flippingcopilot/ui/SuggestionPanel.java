package com.flippingcopilot.ui;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.controller.*;
import com.flippingcopilot.model.*;
import com.flippingcopilot.ui.flipsdialog.FlipsDialogController;
import com.flippingcopilot.util.ProfitCalculator;
import joptsimple.internal.Strings;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import static com.flippingcopilot.ui.UIUtilities.*;
import static com.flippingcopilot.util.Constants.MIN_GP_NEEDED_TO_FLIP;


@Singleton
@Slf4j
public class SuggestionPanel extends JPanel {
    private static final int DEFAULT_PANEL_HEIGHT = 150;

    // dependencies
    private final FlippingCopilotConfig config;
    private final SuggestionManager suggestionManager;
    private final SuggestionPreferencesManager suggestionPreferencesManager;
    private final AccountStatusManager accountStatusManager;
    public final PauseButton pauseButton;
    private final JButton blockButton = new JButton();
    private final OsrsLoginManager osrsLoginManager;
    private final Client client;
    private final PausedManager pausedManager;
    private final GrandExchangeUncollectedManager uncollectedManager;
    private final ClientThread clientThread;
    private final HighlightController highlightController;
    private final ItemManager itemManager;
    private final GrandExchange grandExchange;
    private final PremiumInstanceController premiumInstanceController;
    private final FlipsDialogController flipsDialogController;
    private final ProfitCalculator profitCalculator;


    private final JLabel suggestionText = new JLabel();
    private final JLabel suggestionIcon = new JLabel(new ImageIcon(ImageUtil.loadImageResource(getClass(),"/small_open_arrow.png")));
    private final JPanel suggestionTextContainer = new JPanel();
    private final JLabel additionalInfoText = new JLabel();
    public final Spinner spinner = new Spinner();
    private JLabel skipButton;
    private final JPanel buttonContainer = new JPanel();
    private final JPanel suggestedActionPanel;
    private final PreferencesPanel preferencesPanel;
    private final JLayeredPane layeredPane = new JLayeredPane();
    private volatile boolean isPreferencesPanelVisible = false;
    private final JLabel gearButton;
    private volatile boolean collectItemsSuggested;
    private final AtomicLong refreshGeneration = new AtomicLong();
    private static final String HIGHLIGHTED_COLOR = "yellow";

    private String serverMessage = "";

    private enum DisplayMode {
        MESSAGE, LOADING, SUGGESTION, EMPTY
    }

    @Value
    @Builder
    private static class DisplaySnapshot {
        DisplayMode mode;
        String text;
        String serverMessage;
        String additionalInfo;
        String tooltip;
        AsyncBufferedImage image;
        boolean showButtons;
    }

    public void setServerMessage(String serverMessage) {
        this.serverMessage = serverMessage == null ? "" : serverMessage;
    }


    @Inject
    public SuggestionPanel(FlippingCopilotConfig config,
                           SuggestionManager suggestionManager,
                           SuggestionPreferencesManager suggestionPreferencesManager,
                           AccountStatusManager accountStatusManager,
                           PauseButton pauseButton,
                           PreferencesPanel preferencesPanel,
                           OsrsLoginManager osrsLoginManager,
                           Client client, PausedManager pausedManager,
                           GrandExchangeUncollectedManager uncollectedManager,
                           ClientThread clientThread,
                           HighlightController highlightController,
                           ItemManager itemManager,
                           GrandExchange grandExchange,  PremiumInstanceController premiumInstanceController, FlipsDialogController flipsDialogController, ProfitCalculator profitCalculator) {
        this.preferencesPanel = preferencesPanel;
        this.config = config;
        this.suggestionManager = suggestionManager;
        this.suggestionPreferencesManager = suggestionPreferencesManager;
        this.accountStatusManager = accountStatusManager;
        this.pauseButton = pauseButton;
        this.osrsLoginManager = osrsLoginManager;
        this.client = client;
        this.pausedManager = pausedManager;
        this.uncollectedManager = uncollectedManager;
        this.clientThread = clientThread;
        this.highlightController = highlightController;
        this.itemManager = itemManager;
        this.grandExchange = grandExchange;
        this.premiumInstanceController = premiumInstanceController;
        this.flipsDialogController = flipsDialogController;
        this.profitCalculator = profitCalculator;

        layeredPane.setLayout(null);
        setPreferredSize(new Dimension(MainPanel.CONTENT_WIDTH, DEFAULT_PANEL_HEIGHT));
        suggestedActionPanel = darkPanel(new BorderLayout(), ColorScheme.DARKER_GRAY_COLOR);
        suggestedActionPanel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));
        suggestedActionPanel.setBounds(0, 0, MainPanel.CONTENT_WIDTH, DEFAULT_PANEL_HEIGHT);

        JPanel suggestionContainer = darkPanel(new BorderLayout(), ColorScheme.DARKER_GRAY_COLOR);
        suggestionContainer.setPreferredSize(new Dimension(MainPanel.CONTENT_WIDTH - 10, 85));
        suggestedActionPanel.add(suggestionContainer, BorderLayout.CENTER);

        // Center panel for main suggestion content (icon and text)
        JPanel suggestionMainPanel = darkPanel(new CardLayout(), ColorScheme.DARKER_GRAY_COLOR);
        suggestionContainer.add(suggestionMainPanel, BorderLayout.CENTER);

        suggestionTextContainer.setLayout(new BoxLayout(suggestionTextContainer, BoxLayout.X_AXIS));
        suggestionTextContainer.add(Box.createHorizontalGlue());
        suggestionTextContainer.add(suggestionIcon);
        suggestionTextContainer.add(suggestionText);
        suggestionTextContainer.add(Box.createHorizontalGlue());
        suggestionTextContainer.setOpaque(true);
        suggestionTextContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        suggestionIcon.setVisible(false);
        suggestionIcon.setOpaque(true);
        suggestionIcon.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        suggestionIcon.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        suggestionText.setHorizontalAlignment(SwingConstants.CENTER);
        suggestionText.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 3));
        suggestionMainPanel.add(suggestionTextContainer);

        suggestionMainPanel.add(spinner);

        // Add expected profit text to SOUTH of suggestionContainer
        additionalInfoText.setHorizontalAlignment(SwingConstants.CENTER);
        additionalInfoText.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        additionalInfoText.setText("");
        additionalInfoText.setBorder(BorderFactory.createEmptyBorder(0, 6, 8, 6)); // top, left, bottom, right
        suggestionContainer.add(additionalInfoText, BorderLayout.SOUTH);

        setupButtonContainer();
        suggestedActionPanel.add(buttonContainer, BorderLayout.SOUTH);

        layeredPane.add(suggestedActionPanel, JLayeredPane.DEFAULT_LAYER);
        this.preferencesPanel.setVisible(false);

        layeredPane.add(this.preferencesPanel, JLayeredPane.PALETTE_LAYER);

        // Create and add the gear button
        gearButton = UIUtilities.gearButton("Settings", this::handleGearClick);
        gearButton.setEnabled(true);
        gearButton.setFocusable(true);
        gearButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        gearButton.setOpaque(true);
        gearButton.setBounds(5, 5, 20, 20);

        layeredPane.add(gearButton, JLayeredPane.MODAL_LAYER);

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setPanelHeight(DEFAULT_PANEL_HEIGHT);

        add(layeredPane);
    }

    private void handleGearClick() {
        isPreferencesPanelVisible = !isPreferencesPanelVisible;
        if (isPreferencesPanelVisible) {
            setPanelHeight(280);
        } else {
            setPanelHeight(DEFAULT_PANEL_HEIGHT);
        }

        preferencesPanel.setVisible(isPreferencesPanelVisible);
        suggestedActionPanel.setVisible(!isPreferencesPanelVisible);

        refresh();
        layeredPane.revalidate();
        layeredPane.repaint();
    }

    private void setPanelHeight(int height) {
        layeredPane.setSize(MainPanel.CONTENT_WIDTH, height);
        layeredPane.setPreferredSize(new Dimension(MainPanel.CONTENT_WIDTH, height));
        setPreferredSize(new Dimension(MainPanel.CONTENT_WIDTH, height));
        preferencesPanel.setBounds(0, 0, MainPanel.CONTENT_WIDTH, height);
        suggestedActionPanel.setBounds(0, 0, MainPanel.CONTENT_WIDTH, height);
    }

    private void setupButtonContainer() {
        buttonContainer.setLayout(new BorderLayout());
        buttonContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JPanel centerPanel = darkPanel(new GridLayout(1, 5, 15, 0), ColorScheme.DARKER_GRAY_COLOR);

        BufferedImage graphIcon = ImageUtil.loadImageResource(getClass(), "/graph.png");
        centerPanel.add(buildButton(graphIcon, "Price graph", flipsDialogController::openSuggestionPriceGraph));

        BufferedImage portfolioIcon = ImageUtil.loadImageResource(getClass(), "/pie-chart.png");
        centerPanel.add(buildButton(portfolioIcon, "Open portfolio", flipsDialogController::showPortfolioTab));

        centerPanel.add(pauseButton);

        BufferedImage blockImg = ImageUtil.loadImageResource(getClass(), "/block.png");
        ImageIcon blockIcon = new ImageIcon(blockImg);
        ImageIcon blockIconHover = new ImageIcon(ImageUtil.luminanceScale(blockImg, BUTTON_HOVER_LUMINANCE));
        blockButton.setIcon(blockIcon);
        blockButton.setToolTipText("Block this item");
        blockButton.setFocusPainted(false);
        blockButton.setBorderPainted(false);
        blockButton.setContentAreaFilled(false);
        blockButton.addActionListener(e -> confirmAndBlock());
        addHoverIcons(blockButton, () -> blockIcon, () -> blockIconHover);
        centerPanel.add(blockButton);

        BufferedImage skipIcon = ImageUtil.loadImageResource(getClass(), "/skip.png");
        skipButton = buildButton(skipIcon, "Skip suggestion", () -> {
            if (accountStatusManager.skipCurrentSuggestion()) {
                refresh();
            }
        });
        centerPanel.add(skipButton);

        buttonContainer.add(centerPanel, BorderLayout.CENTER);
    }

    private void confirmAndBlock() {
        Suggestion s = suggestionManager.getSuggestion();
        if (s == null) {
            log.debug("No current suggestion to block.");
            return;
        }

        String itemName = s.getName() != null ? s.getName() : "this item";
        int choice = JOptionPane.showConfirmDialog(
                blockButton,
                "Do you want to block " + itemName + "?",
                "Confirm Block",
                JOptionPane.YES_NO_OPTION
        );

        if (choice == JOptionPane.YES_OPTION) {
            suggestionPreferencesManager.blockItem(s.getItemId());
            log.debug("Blocked item with ID {} ({})", s.getItemId(), itemName);
            suggestionManager.setSuggestionNeeded(true);
        } else {
            log.debug("User canceled blocking for {}", itemName);
        }
    }


    private void setItemIcon(AsyncBufferedImage image) {
        if (image != null) {
            image.addTo(suggestionIcon);
            suggestionIcon.setVisible(true);
        }
    }

    private void setAdditionalInfoText(String text, String tooltip) {
        additionalInfoText.setText("<html><center>" + text + "</center></html>");
        additionalInfoText.setToolTipText(tooltip);
        suggestionText.setToolTipText(tooltip);
    }

    private static String quantityNameAndPrice(Suggestion suggestion, NumberFormat formatter) {
        return " <FONT COLOR=" + HIGHLIGHTED_COLOR + ">" + formatter.format(suggestion.getQuantity()) + "</FONT><br>" +
                "<FONT COLOR=white>" + suggestion.getName() + "</FONT><br>" +
                "for <FONT COLOR=" + HIGHLIGHTED_COLOR + ">" + formatter.format(suggestion.getPrice()) + "</FONT> gp<br>";
    }

    private DisplaySnapshot buildSuggestionSnapshot(Suggestion suggestion, AccountStatus accountStatus) {
        NumberFormat formatter = NumberFormat.getNumberInstance();
        String suggestionString = "<html><center>";
        AsyncBufferedImage image = null;
        SuggestionType suggestionType = suggestion.getType();
        if (suggestionType == null) {
            suggestionString += "Error processing suggestion<br>";
        } else {
        switch (suggestionType) {
            case WAIT:
                suggestionString += "Wait <br>";
                break;
            case ABORT:
                suggestionString += "Abort offer for<br><FONT COLOR=white>" + suggestion.getName() + "<br></FONT>";
                image = itemManager.getImage(suggestion.getItemId());
                break;
            case BUY:
                suggestionString += (suggestion.isHold() ? "Buy and hold" : "Buy") + quantityNameAndPrice(suggestion, formatter);
                image = itemManager.getImage(suggestion.getItemId());
                break;
            case SELL:
            case MODIFY_BUY:
            case MODIFY_SELL:
                String action = suggestion.isBuySuggestion() ? "buy" : "sell";
                if (suggestion.isModifySuggestion()) {
                    suggestionString += "Modify " + action +
                            "<br>" +
                            "<FONT COLOR=white>" + suggestion.getName() + "</FONT><br>" +
                            "to <FONT COLOR=" + HIGHLIGHTED_COLOR + ">" + formatter.format(suggestion.getPrice()) + "</FONT> gp<br>";
                } else {
                    suggestionString += (accountStatus.shouldSellFromBank(suggestion) ? "Sell from bank" : suggestion.isSellSuggestion() ? "Sell" : "Buy") + quantityNameAndPrice(suggestion, formatter);
                }
                image = itemManager.getImage(suggestion.getItemId());
                break;
            default:
                suggestionString += "Error processing suggestion<br>";
        }
        }
        String additionalInfoMessage = Strings.isNullOrEmpty(suggestion.getMessage()) ? "" : "<br>" + suggestion.getMessage();

        suggestionString += "</center></html>";
        String additionalInfo = additionalInfoMessage;
        String tooltip = null;
        if (suggestion.isBuySuggestion()) {
            additionalInfo = formatExpectedProfitAndDuration(suggestion.getExpectedProfit(), suggestion.getExpectedDuration()) + additionalInfoMessage;
            tooltip = formatSuggestionTooltip(suggestion, suggestion.getExpectedProfit());
        } else if (suggestion.isSellSuggestion()) {
            String text = "";
            Long profit = profitCalculator.calculateSuggestionProfit(suggestion);
            if (profit != null) {
                text = formatSellProfitLossAndDuration((double) profit, suggestion.getExpectedDuration());
            }
            additionalInfo = text + additionalInfoMessage;
            tooltip = formatSuggestionTooltip(suggestion, profit == null ? null : (double) profit);
        }
        return DisplaySnapshot.builder()
                .mode(DisplayMode.SUGGESTION)
                .text(suggestionString)
                .serverMessage(suggestion.getMessage())
                .additionalInfo(additionalInfo)
                .tooltip(tooltip)
                .image(image)
                .showButtons(!suggestion.isWaitSuggestion())
                .build();
    }

    public void setMessage(String message) {
        additionalInfoText.setVisible(false);
        clearSuggestionTooltips();
        collectItemsSuggested = "Collect items".equals(message);
        setButtonsVisible(false);

        // Check if message contains "<manage>"
        String displayMessage = message;
        if (message != null && message.contains("<manage>")) {
            // Replace <manage> with a styled link
            displayMessage = message.replace("<manage>",
                    "<a href='#' style='text-decoration:underline'>manage</a>");

            // Add mouse listener if not already present
            boolean hasListener = false;
            for (MouseListener listener : suggestionText.getMouseListeners()) {
                if (listener instanceof ManageClickListener) {
                    hasListener = true;
                    break;
                }
            }

            if (!hasListener) {
                suggestionText.addMouseListener(new ManageClickListener());
                // Make the label show a hand cursor when hovering over it
                suggestionText.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }
        } else {
            suggestionText.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        }
        suggestionText.setText("<html><center>" + displayMessage + "<br>" + serverMessage + "</center></html>");
        suggestionText.setMaximumSize(new Dimension(suggestionText.getPreferredSize().width, Integer.MAX_VALUE));
        suggestionTextContainer.revalidate();
        suggestionTextContainer.repaint();
    }

    private class ManageClickListener extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            String text = suggestionText.getText();
            if (text.contains("manage")) {
                premiumInstanceController.loadAndOpenPremiumInstanceDialog();
            }
        }
    }

    public boolean isCollectItemsSuggested() {
        return collectItemsSuggested;
    }

    public void showLoading() {
        collectItemsSuggested = false;
        suggestionTextContainer.setVisible(false);
        setServerMessage("");
        spinner.show();
        setButtonsVisible(false);
        suggestionIcon.setVisible(false);
        additionalInfoText.setText("");
        clearSuggestionTooltips();
        additionalInfoText.setVisible(false);
        suggestionText.setText("");
    }

    public void hideLoading() {
        spinner.hide();
        suggestionTextContainer.setVisible(true);
        additionalInfoText.setVisible(true);
    }

    private void setButtonsVisible(boolean visible) {
        skipButton.setVisible(visible);
        blockButton.setVisible(visible);
        suggestionIcon.setVisible(visible);
    }

    private DisplaySnapshot messageSnapshot(String message, String serverMessage) {
        return DisplaySnapshot.builder()
                .mode(DisplayMode.MESSAGE)
                .text(message)
                .serverMessage(serverMessage)
                .build();
    }

    private DisplaySnapshot buildDisplaySnapshot() {
        if (isPreferencesPanelVisible) {
            return DisplaySnapshot.builder().mode(DisplayMode.EMPTY).build();
        }
        if (pausedManager.isPaused()) {
            return messageSnapshot("Suggestions are paused", "");
        }
        String errorMessage = osrsLoginManager.getInvalidStateDisplayMessage();
        if (errorMessage != null) {
            return messageSnapshot(errorMessage, "");
        }
        if (suggestionManager.isSuggestionRequestInProgress() || suggestionManager.isSuggestionRefreshPending()) {
            return DisplaySnapshot.builder().mode(DisplayMode.LOADING).build();
        }
        HttpResponseException suggestionError = suggestionManager.getSuggestionError();
        if (suggestionError != null) {
            highlightController.redraw();
            return messageSnapshot("Error: " + suggestionError.getMessage(), "");
        }
        Suggestion suggestion = suggestionManager.getSuggestion();
        if (suggestion == null) {
            return DisplaySnapshot.builder().mode(DisplayMode.EMPTY).build();
        }
        AccountStatus accountStatus = accountStatusManager.getAccountStatus();
        if(accountStatus == null) {
            return DisplaySnapshot.builder().mode(DisplayMode.EMPTY).build();
        }
        String message = suggestion.getMessage();
        boolean collectNeeded = accountStatus.isCollectNeeded(suggestion, grandExchange.isSetupOfferOpen());
        if(collectNeeded && !uncollectedManager.HasUncollected(osrsLoginManager.getAccountHash())) {
            log.warn("tick {} collect is suggested but there is nothing to collect! suggestion: {} {} {}", client.getTickCount(), suggestion.getType(), suggestion.getQuantity(), suggestion.getItemId());
        }
        DisplaySnapshot snapshot;
        if (collectNeeded) {
            snapshot = messageSnapshot("Collect items", message);
        } else if (suggestion.isWaitSuggestion() && !grandExchange.isOpen() && accountStatus.emptySlotExists()) {
            snapshot = messageSnapshot("Open the Grand Exchange<br>to get a flip suggestion", message);
        } else if (suggestion.isWaitSuggestion() && accountStatus.moreGpNeeded()) {
            snapshot = messageSnapshot("Add at least <FONT COLOR=" + HIGHLIGHTED_COLOR + ">"
                    + NumberFormat.getNumberInstance().format(MIN_GP_NEEDED_TO_FLIP)
                    + "</FONT> gp<br>to your inventory<br>to get a flip suggestion", message);
        } else if (suggestion.isWaitSuggestion()
                && grandExchange.isOpen()
                && accountStatus.emptySlotExists()
                && suggestionPreferencesManager.isReceiveDumpSuggestions()) {
            snapshot = messageSnapshot("Waiting for dumps...", message);
        }  else {
            snapshot = buildSuggestionSnapshot(suggestion, accountStatus);
        }
        highlightController.redraw();
        return snapshot;
    }

    public void refresh() {
        long generation = refreshGeneration.incrementAndGet();
        clientThread.invokeLater(() -> {
            if (generation != refreshGeneration.get()) {
                return;
            }
            log.debug("refreshing suggestion panel {}", client.getGameState());
            DisplaySnapshot snapshot = buildDisplaySnapshot();
            SwingUtilities.invokeLater(() -> {
                if (generation == refreshGeneration.get()) {
                    applyDisplaySnapshot(snapshot);
                }
            });
        });
    }

    private void applyDisplaySnapshot(DisplaySnapshot snapshot) {
        if (isPreferencesPanelVisible) {
            preferencesPanel.refresh();
            return;
        }
        if (snapshot.getMode() == DisplayMode.LOADING) {
            showLoading();
            return;
        }
        hideLoading();
        setServerMessage(snapshot.getServerMessage());
        if (snapshot.getMode() == DisplayMode.MESSAGE) {
            setMessage(snapshot.getText());
            return;
        }
        if (snapshot.getMode() == DisplayMode.EMPTY) {
            collectItemsSuggested = false;
            return;
        }
        collectItemsSuggested = false;
        suggestionTextContainer.setVisible(false);
        clearSuggestionTooltips();
        setButtonsVisible(snapshot.isShowButtons());
        suggestionIcon.setVisible(false);
        setItemIcon(snapshot.getImage());
        suggestionText.setText(snapshot.getText());
        suggestionText.setMaximumSize(new Dimension(suggestionText.getPreferredSize().width, Integer.MAX_VALUE));
        setAdditionalInfoText(snapshot.getAdditionalInfo(), snapshot.getTooltip());
        suggestionTextContainer.setVisible(true);
        suggestionTextContainer.revalidate();
        suggestionTextContainer.repaint();
    }

    private String formatSellProfitLossAndDuration(Double expectedProfit, Double expectedDuration) {
        String formattedProfit = formatProfit(expectedProfit);
        Color color = config.profitAmountColor();
        if(expectedProfit < 0) {
            color = config.lossAmountColor();
        }
        String text = boldColor(formattedProfit, color) + " profit";
        if (expectedDuration != null) {
            String formattedDuration = formatSuggestionDuration(expectedDuration);
            text += " in <b>" + formattedDuration + "</b>";
        }
        return text;
    }

    private String formatExpectedProfitAndDuration(Double expectedProfit, Double expectedDuration) {
        if (expectedProfit == null || expectedDuration == null) {
            return "";
        }
        String formattedProfit = formatProfit(expectedProfit);
        String formattedDuration = formatSuggestionDuration(expectedDuration);
        return boldColor(formattedProfit, config.profitAmountColor()) + " profit in <b>" + formattedDuration + "</b>";
    }

    private String formatSuggestionTooltip(Suggestion suggestion, Double suggestionProfit) {
        String roiLine = formatRoiTooltipLine(suggestion, suggestionProfit);
        String costLine = formatCostTooltipLine(suggestion);
        if (roiLine == null && costLine == null) {
            return null;
        }
        StringBuilder tooltip = new StringBuilder("<html>");
        appendTooltipLine(tooltip, roiLine);
        appendTooltipLine(tooltip, costLine);
        return tooltip.append("</html>").toString();
    }

    private void appendTooltipLine(StringBuilder tooltip, String line) {
        if (line == null) {
            return;
        }
        if (tooltip.length() > "<html>".length()) {
            tooltip.append("<br>");
        }
        tooltip.append(line);
    }

    private String formatCostTooltipLine(Suggestion suggestion) {
        Long cost = profitCalculator.calculateSuggestionCostBasis(suggestion);
        if (cost == null) {
            return null;
        }
        return "Cost: <font color='#FFFFFF'>" + UIUtilities.quantityToRSDecimalStack(cost, false) + " gp</font>";
    }

    private String formatRoiTooltipLine(Suggestion suggestion, Double suggestionProfit) {
        if (suggestionProfit == null) {
            return null;
        }
        Double roi = profitCalculator.calculateSuggestionRoi(suggestion, suggestionProfit);
        if (roi == null) {
            return null;
        }
        Color roiColor = UIUtilities.getProfitColor(roi, config);
        return "ROI: <font color='" + colorHex(roiColor) + "'>" + formatRoi(roi) + "</font>";
    }

    private void clearSuggestionTooltips() {
        additionalInfoText.setToolTipText(null);
        suggestionText.setToolTipText(null);
    }

    private String boldColor(String text, Color color) {
        return "<b><font color='" + colorHex(color) + "'>" + text + "</font></b>";
    }

    private String formatRoi(double roi) {
        return String.format(Locale.ENGLISH, "%.2f%%", roi * 100.0d);
    }

    private String formatProfit(double profit) {
        if (Math.abs(profit) >= 1_000_000) {
            return String.format("%.1fM", profit / 1_000_000).replace(".0", "");
        } else if (Math.abs(profit) >= 1_000) {
            return String.format("%.1fK", profit / 1_000).replace(".0", "");
        } else {
            return String.format("%.0f", profit);
        }
    }
}
