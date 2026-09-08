package copilot.ui;
import copilot.model.Preferences;
import static net.runelite.client.util.ImageUtil.*;

import net.runelite.client.util.*;
import static copilot.ui.UIUtilities.*;
import static javax.swing.BorderFactory.*;
import static net.runelite.client.ui.ColorScheme.*;
import copilot.config.*;
import copilot.controller.*;
import copilot.model.*;
import copilot.ui.flipsdialog.*;
import copilot.util.*;
import joptsimple.internal.*;
import lombok.extern.slf4j.*;
import net.runelite.api.*;
import net.runelite.client.callback.*;
import net.runelite.client.game.*;

import javax.inject.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.text.*;
import java.util.*;

import static copilot.util.Constants.MIN_GP_NEEDED_TO_FLIP;

@Singleton
@Slf4j
public class SuggestionPanel extends JPanel {
    private static final int DEFAULT_PANEL_HEIGHT = 150;

    // dependencies
    private final CopilotConfig config;
    private final Suggestions suggestions;
    private final Preferences preferences;
    private final AccountStatusManager accounts;
    public final PauseButton pauseButton;
    private final JButton blockButton = new JButton();
    private final PlayerLogin login;
    private final Client client;
    private final PausedManager pausedManager;
    private final Uncollected uncollectedManager;
    private final ClientThread clientThread;
    private final HighlightController highlights;
    private final ItemManager itemManager;
    private final GrandExchange grandExchange;
    private final PremiumInstanceController premiumInstanceController;
    private final FlipsDialogController dialogs;
    private final ProfitCalculator profitCalculator;

    private final JLabel suggestionText = new JLabel();
    private final JLabel suggestionIcon = new JLabel(new ImageIcon(loadImageResource(getClass(),"/small_open_arrow.png")));
    private final JPanel suggestionTextContainer = new JPanel();
    private final JLabel additionalInfoText = new JLabel();
    public final Spinner spinner = new Spinner();
    private JLabel skipButton;
    private final JPanel buttonContainer = new JPanel(), suggestedActionPanel;
    private final PreferencesPanel preferencesPanel;
    private final JLayeredPane layeredPane = new JLayeredPane();
    private boolean isPreferencesPanelVisible = false;
    private final JLabel gearButton;
    private String innerSuggestionMessage;
    private static final String HIGHLIGHTED_COLOR = "yellow";

    private String serverMessage = "";

    public void setServerMessage(String serverMessage) {
        this.serverMessage = serverMessage == null ? "" : serverMessage;
    }

    @Inject
    public SuggestionPanel(CopilotConfig config,
                           Suggestions suggestions,
                           Preferences preferences,
                           AccountStatusManager accounts,
                           PauseButton pauseButton,
                           PreferencesPanel preferencesPanel,
                           PlayerLogin login,
                           Client client, PausedManager pausedManager,
                           Uncollected uncollectedManager,
                           ClientThread clientThread,
                           HighlightController highlights,
                           ItemManager itemManager,
                           GrandExchange grandExchange,  PremiumInstanceController premiumInstanceController, FlipsDialogController dialogs, ProfitCalculator profitCalculator) {
        this.preferencesPanel = preferencesPanel; this.config = config; this.suggestions = suggestions;
        this.preferences = preferences; this.accounts = accounts; this.pauseButton = pauseButton; this.login = login;
        this.client = client; this.pausedManager = pausedManager; this.uncollectedManager = uncollectedManager;
        this.clientThread = clientThread; this.highlights = highlights; this.itemManager = itemManager;
        this.grandExchange = grandExchange; this.premiumInstanceController = premiumInstanceController;
        this.dialogs = dialogs; this.profitCalculator = profitCalculator;

        layeredPane.setLayout(null);
        setPreferredSize(new Dimension(MainPanel.CONTENT_WIDTH, DEFAULT_PANEL_HEIGHT));
        suggestedActionPanel = darkPanel(new BorderLayout(), DARKER_GRAY_COLOR);
        suggestedActionPanel.setBorder(createEmptyBorder(10, 5, 10, 5));
        suggestedActionPanel.setBounds(0, 0, MainPanel.CONTENT_WIDTH, DEFAULT_PANEL_HEIGHT);

        var suggestionContainer = darkPanel(new BorderLayout(), DARKER_GRAY_COLOR);
        suggestionContainer.setPreferredSize(new Dimension(MainPanel.CONTENT_WIDTH - 10, 85));
        suggestedActionPanel.add(suggestionContainer, BorderLayout.CENTER);

        // Center panel for main suggestion content (icon and text)
        var suggestionMainPanel = darkPanel(new CardLayout(), DARKER_GRAY_COLOR);
        suggestionContainer.add(suggestionMainPanel, BorderLayout.CENTER);

        suggestionTextContainer.setLayout(new BoxLayout(suggestionTextContainer, BoxLayout.X_AXIS));
        suggestionTextContainer.add(Box.createHorizontalGlue());
        suggestionTextContainer.add(suggestionIcon);
        suggestionTextContainer.add(suggestionText);
        suggestionTextContainer.add(Box.createHorizontalGlue());
        suggestionTextContainer.setOpaque(true); suggestionTextContainer.setBackground(DARKER_GRAY_COLOR);
        suggestionIcon.setVisible(false); suggestionIcon.setOpaque(true);
        suggestionIcon.setBackground(DARKER_GRAY_COLOR); suggestionIcon.setBorder(createEmptyBorder(0, 0, 0, 0));

        suggestionText.setHorizontalAlignment(SwingConstants.CENTER);
        suggestionText.setBorder(createEmptyBorder(0, 3, 0, 3));
        suggestionMainPanel.add(suggestionTextContainer);

        suggestionMainPanel.add(spinner);

        // Add expected profit text to SOUTH of suggestionContainer
        additionalInfoText.setHorizontalAlignment(SwingConstants.CENTER);
        additionalInfoText.setForeground(LIGHT_GRAY_COLOR); additionalInfoText.setText("");
        additionalInfoText.setBorder(createEmptyBorder(0, 6, 8, 6)); // top, left, bottom, right
        suggestionContainer.add(additionalInfoText, BorderLayout.SOUTH);

        setupButtonContainer();
        suggestedActionPanel.add(buttonContainer, BorderLayout.SOUTH);

        layeredPane.add(suggestedActionPanel, JLayeredPane.DEFAULT_LAYER);
        this.preferencesPanel.setVisible(false);

        layeredPane.add(this.preferencesPanel, JLayeredPane.PALETTE_LAYER);

        // Create and add the gear button
        gearButton = gearButton("Settings", this::handleGearClick);
        gearButton.setEnabled(true); gearButton.setFocusable(true); gearButton.setBackground(DARKER_GRAY_COLOR);
        gearButton.setOpaque(true); gearButton.setBounds(5, 5, 20, 20);

        layeredPane.add(gearButton, JLayeredPane.MODAL_LAYER);

        setLayout(new BorderLayout());
        setBackground(DARKER_GRAY_COLOR);
        setPanelHeight(DEFAULT_PANEL_HEIGHT);

        add(layeredPane);
    }

    private void handleGearClick() {
        isPreferencesPanelVisible = !isPreferencesPanelVisible;
        if (isPreferencesPanelVisible) { setPanelHeight(280); } else {
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
        buttonContainer.setLayout(new BorderLayout()); buttonContainer.setBackground(DARKER_GRAY_COLOR);

        var centerPanel = darkPanel(new GridLayout(1, 5, 15, 0), DARKER_GRAY_COLOR);

        var graphIcon = loadImageResource(getClass(), "/graph.png");
        centerPanel.add(buildButton(graphIcon, "Price graph", dialogs::openSuggestionPriceGraph));

        var portfolioIcon = loadImageResource(getClass(), "/pie-chart.png");
        centerPanel.add(buildButton(portfolioIcon, "Open portfolio", dialogs::showPortfolioTab));

        centerPanel.add(pauseButton);

        var blockImg = loadImageResource(getClass(), "/block.png");
        var blockIcon = new ImageIcon(blockImg);
        var blockIconHover = new ImageIcon(luminanceScale(blockImg, BUTTON_HOVER_LUMINANCE));
        blockButton.setIcon(blockIcon); blockButton.setToolTipText("Block this item");
        blockButton.setFocusPainted(false); blockButton.setBorderPainted(false);
        blockButton.setContentAreaFilled(false);
        blockButton.addActionListener(e -> confirmAndBlock());
        addHoverIcons(blockButton, () -> blockIcon, () -> blockIconHover);
        centerPanel.add(blockButton);

        var skipIcon = loadImageResource(getClass(), "/skip.png");
        skipButton = buildButton(skipIcon, "Skip suggestion", () -> {
            if (accounts.skipCurrentSuggestion()) { refresh(); }
        });
        centerPanel.add(skipButton);

        buttonContainer.add(centerPanel, BorderLayout.CENTER);
    }

    private void confirmAndBlock() {
        Suggestion s = suggestions.getSuggestion();
        if (s == null) {
            log.debug("No current suggestion to block.");
            return;
        }

        String itemName = s.name != null ? s.name : "this item";
        int choice = JOptionPane.showConfirmDialog(
                blockButton,
                "Do you want to block " + itemName + "?",
                "Confirm Block",
                JOptionPane.YES_NO_OPTION
        );

        if (choice == JOptionPane.YES_OPTION) {
            preferences.blockItem(s.itemId);
            log.debug("Blocked item with ID {} ({})", s.itemId, itemName);
            suggestions.setSuggestionNeeded(true);
        } else {
            log.debug("User canceled blocking for {}", itemName);
        }
    }

    private void setItemIcon(int itemId) {
        var image = itemManager.getImage(itemId);
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
        return " <FONT COLOR=" + HIGHLIGHTED_COLOR + ">" + formatter.format(suggestion.quantity) + "</FONT><br>" +
                "<FONT COLOR=white>" + suggestion.name + "</FONT><br>" +
                "for <FONT COLOR=" + HIGHLIGHTED_COLOR + ">" + formatter.format(suggestion.price) + "</FONT> gp<br>";
    }

    public void updateSuggestion(Suggestion suggestion) {
        var formatter = NumberFormat.getNumberInstance();
        String suggestionString = "<html><center>";
        suggestionTextContainer.setVisible(false);
        additionalInfoText.setText("");
        clearSuggestionTooltips();
        var suggestionType = suggestion.type;
        if (suggestionType == null) { suggestionString += "Error processing suggestion<br>"; } else {
        switch (suggestionType) {
            case WAIT:
                suggestionString += "Wait <br>";
                suggestionIcon.setVisible(false);
                break;
            case ABORT:
                suggestionString += "Abort offer for<br><FONT COLOR=white>" + suggestion.name + "<br></FONT>";
                setItemIcon(suggestion.itemId);
                break;
            case BUY:
                suggestionString += (suggestion.isHold() ? "Buy and hold" : "Buy") + quantityNameAndPrice(suggestion, formatter);
                setItemIcon(suggestion.itemId);
                break;
            case SELL:
            case MODIFY_BUY:
            case MODIFY_SELL:
                String action = suggestion.isBuySuggestion() ? "buy" : "sell";
                if (suggestion.isModifySuggestion()) {
                    suggestionString += "Modify " + action +
                            "<br>" +
                            "<FONT COLOR=white>" + suggestion.name + "</FONT><br>" +
                            "to <FONT COLOR=" + HIGHLIGHTED_COLOR + ">" + formatter.format(suggestion.price) + "</FONT> gp<br>";
                } else {
                    suggestionString += (shouldSellFromBank(suggestion) ? "Sell from bank" : suggestion.isSellSuggestion() ? "Sell" : "Buy") + quantityNameAndPrice(suggestion, formatter);
                }
                setItemIcon(suggestion.itemId);
                break;
            default:
                suggestionString += "Error processing suggestion<br>";
        }
        }
        String additionalInfoMessage = Strings.isNullOrEmpty(suggestion.message) ? "" : "<br>" + suggestion.message;

        suggestionString += "</center></html>";
        innerSuggestionMessage = "";
        if (!suggestion.isWaitSuggestion()) { setButtonsVisible(true); }
        suggestionText.setText(suggestionString);
        suggestionText.setMaximumSize(new Dimension(suggestionText.getPreferredSize().width, Integer.MAX_VALUE));
        if (suggestion.isBuySuggestion()) {
            setAdditionalInfoText(
                    formatExpectedProfitAndDuration(suggestion.expectedProfit, suggestion.expectedDuration) + additionalInfoMessage,
                    formatSuggestionTooltip(suggestion, suggestion.expectedProfit)
            );
        } else if (suggestion.isSellSuggestion()) {
            String text = "";
            Long profit = profitCalculator.calculateSuggestionProfit(suggestion);
            if (profit != null) {
                text = formatSellProfitLossAndDuration((double) profit, suggestion.expectedDuration);
            }
            setAdditionalInfoText(
                    text + additionalInfoMessage,
                    formatSuggestionTooltip(suggestion, profit == null ? null : (double) profit)
            );
        } else {
            setAdditionalInfoText(additionalInfoMessage, null);
        }

        suggestionTextContainer.setVisible(true);
        suggestionTextContainer.revalidate();
        suggestionTextContainer.repaint();
    }

    private boolean shouldSellFromBank(Suggestion suggestion) {
        var accountStatus = accounts.getAccountStatus();
        return accountStatus != null && accountStatus.shouldSellFromBank(suggestion);
    }

    public void suggestCollect() { setMessage("Collect items"); }

    public void suggestAddGp() {
        var formatter = NumberFormat.getNumberInstance();
        setMessage("Add " +
                "at least <FONT COLOR=" + HIGHLIGHTED_COLOR + ">" + formatter.format(MIN_GP_NEEDED_TO_FLIP)
                + "</FONT> gp<br>to your inventory<br>"
                + "to get a flip suggestion");
    }

    public void suggestScanningForDumps() { setMessage("Waiting for dumps..."); }

    public void suggestOpenGe() { setMessage("Open the Grand Exchange<br>" + "to get a flip suggestion"); }

    public void setIsPausedMessage() { setMessage("Suggestions are paused"); }

    public void setMessage(String message) {
        additionalInfoText.setVisible(false);
        clearSuggestionTooltips();
        innerSuggestionMessage = message;
        setButtonsVisible(false);

        // Check if message contains "<manage>"
        String displayMessage = message;
        if (message != null && message.contains("<manage>")) {
            // Replace <manage> with a styled link
            displayMessage = message.replace("<manage>", "<a href='#' style='text-decoration:underline'>manage</a>");

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
            if (text.contains("manage")) { premiumInstanceController.loadAndOpenPremiumInstanceDialog(); }
        }
    }

    public boolean isCollectItemsSuggested() {
        return suggestionText.isVisible() && "Collect items".equals(innerSuggestionMessage);
    }

    public void showLoading() {
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

    public void displaySuggestion() {
        Suggestion suggestion = suggestions.getSuggestion();
        setServerMessage("");
        if (suggestion == null) { return; }
        var accountStatus = accounts.getAccountStatus();
        if (accountStatus == null) { return; }
        setServerMessage(suggestion.message);
        boolean collectNeeded = accountStatus.isCollectNeeded(suggestion, grandExchange.isSetupOfferOpen());
        if(collectNeeded && !uncollectedManager.HasUncollected(login.getAccountHash())) {
            log.warn("tick {} collect is suggested but there is nothing to collect! suggestion: {} {} {}", client.getTickCount(), suggestion.type, suggestion.quantity, suggestion.itemId);
        }
        if (collectNeeded) { suggestCollect(); } else if (suggestion.isWaitSuggestion() && !grandExchange.isOpen() && accountStatus.emptySlotExists()) {
            suggestOpenGe();
        } else if (suggestion.isWaitSuggestion() && accountStatus.moreGpNeeded()) {
            suggestAddGp();
        } else if (suggestion.isWaitSuggestion()
                && grandExchange.isOpen()
                && accountStatus.emptySlotExists()
                && preferences.isReceiveDumpSuggestions()) { suggestScanningForDumps(); }  else {
            updateSuggestion(suggestion);
        }
        highlights.redraw();
    }

    public void refresh() {
        log.debug("refreshing suggestion panel {}", client.getGameState());
        if (!ensureEdt(this::refresh)) return;
        if(isPreferencesPanelVisible) {
            preferencesPanel.refresh();
            return;
        }
        if (pausedManager.isPaused()) {
            hideLoading();
            setIsPausedMessage();
            return;
        }

        String errorMessage = login.getInvalidStateDisplayMessage();
        if (errorMessage != null) {
            hideLoading();
            setServerMessage("");
            setMessage(errorMessage);
            return;
        }

        if(suggestions.isSuggestionRequestInProgress() || suggestions.isSuggestionRefreshPending()) {
            showLoading();
            return;
        }
        hideLoading();

        final var suggestionError = suggestions.getSuggestionError();
        if(suggestionError != null) {
            highlights.redraw();
            setMessage("Error: " + suggestionError.getMessage());
            return;
        }

        if(!client.isClientThread()) { clientThread.invoke(this::displaySuggestion); } else {
            displaySuggestion();
        }
    }

    private String formatSellProfitLossAndDuration(Double expectedProfit, Double expectedDuration) {
        String formattedProfit = formatProfit(expectedProfit);
        Color color = config.profitAmountColor();
        if(expectedProfit < 0) { color = config.lossAmountColor(); }
        String text = boldColor(formattedProfit, color) + " profit";
        if (expectedDuration != null) {
            String formattedDuration = formatSuggestionDuration(expectedDuration);
            text += " in <b>" + formattedDuration + "</b>";
        }
        return text;
    }

    private String formatExpectedProfitAndDuration(Double expectedProfit, Double expectedDuration) {
        if (expectedProfit == null || expectedDuration == null) { return ""; }
        String formattedProfit = formatProfit(expectedProfit);
        String formattedDuration = formatSuggestionDuration(expectedDuration);
        return boldColor(formattedProfit, config.profitAmountColor()) + " profit in <b>" + formattedDuration + "</b>";
    }

    private String formatSuggestionTooltip(Suggestion suggestion, Double suggestionProfit) {
        String roiLine = formatRoiTooltipLine(suggestion, suggestionProfit);
        String costLine = formatCostTooltipLine(suggestion);
        if (roiLine == null && costLine == null) { return null; }
        var tooltip = new StringBuilder("<html>");
        appendTooltipLine(tooltip, roiLine);
        appendTooltipLine(tooltip, costLine);
        return tooltip.append("</html>").toString();
    }

    private void appendTooltipLine(StringBuilder tooltip, String line) {
        if (line == null) { return; }
        if (tooltip.length() > "<html>".length()) { tooltip.append("<br>"); }
        tooltip.append(line);
    }

    private String formatCostTooltipLine(Suggestion suggestion) {
        Long cost = profitCalculator.calculateSuggestionCostBasis(suggestion);
        if (cost == null) { return null; }
        return "Cost: <font color='#FFFFFF'>" + quantityToRSDecimalStack(cost, false) + " gp</font>";
    }

    private String formatRoiTooltipLine(Suggestion suggestion, Double suggestionProfit) {
        if (suggestionProfit == null) { return null; }
        Double roi = profitCalculator.calculateSuggestionRoi(suggestion, suggestionProfit);
        if (roi == null) { return null; }
        Color roiColor = getProfitColor(roi, config);
        return "ROI: <font color='" + colorHex(roiColor) + "'>" + formatRoi(roi) + "</font>";
    }

    private void clearSuggestionTooltips() {
        additionalInfoText.setToolTipText(null);
        suggestionText.setToolTipText(null);
    }

    private String boldColor(String text, Color color) {
        return "<b><font color='" + colorHex(color) + "'>" + text + "</font></b>";
    }

    private String formatRoi(double roi) { return String.format(Locale.ENGLISH, "%.2f%%", roi * 100.0d); }

    private String formatProfit(double profit) {
        if (Math.abs(profit) >= 1_000_000) { return String.format("%.1fM", profit / 1_000_000).replace(".0", ""); } else if (Math.abs(profit) >= 1_000) {
            return String.format("%.1fK", profit / 1_000).replace(".0", "");
        } else {
            return String.format("%.0f", profit);
        }
    }
}
