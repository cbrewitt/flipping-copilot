package com.flippingcopilot.ui;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.controller.GrandExchange;
import com.flippingcopilot.controller.HighlightController;
import com.flippingcopilot.controller.PremiumInstanceController;
import com.flippingcopilot.manager.CopilotLoginManager;
import com.flippingcopilot.model.*;
import com.flippingcopilot.ui.flipsdialog.FlipsDialogController;
import joptsimple.internal.Strings;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;

import static com.flippingcopilot.ui.UIUtilities.*;
import static com.flippingcopilot.util.Constants.MIN_GP_NEEDED_TO_FLIP;


@Singleton
@Slf4j
public class SuggestionPanel extends JPanel {

    // dependencies
    private final FlippingCopilotConfig config;
    private final SuggestionManager suggestionManager;
    private final AccountStatusManager accountStatusManager;
    public final PauseButton pauseButton;
    private final BlockButton blockButton;
    private final OsrsLoginManager osrsLoginManager;
    private final Client client;
    private final PausedManager pausedManager;
    private final GrandExchangeUncollectedManager uncollectedManager;
    private final ClientThread clientThread;
    private final HighlightController highlightController;
    private final ItemManager itemManager;
    private final GrandExchange grandExchange;
    private final PremiumInstanceController premiumInstanceController;
    private final FlipManager flipManager;
    private final CopilotLoginManager copilotLoginManager;
    private final FlipsDialogController flipsDialogController;


    private final JLabel suggestionText = new JLabel();
    private final JLabel suggestionIcon = new JLabel(new ImageIcon(ImageUtil.loadImageResource(getClass(),"/small_open_arrow.png")));
    private final JPanel suggestionTextContainer = new JPanel();
    private final JLabel additionalInfoText = new JLabel();
    public final Spinner spinner = new Spinner();
    private JLabel skipButton;
    private final JPanel buttonContainer = new JPanel();
    private JLabel graphButton;
    private final JPanel suggestedActionPanel;
    private final PreferencesPanel preferencesPanel;
    private final JLayeredPane layeredPane = new JLayeredPane();
    private boolean isPreferencesPanelVisible = false;
    private final JLabel gearButton;
    private String innerSuggestionMessage;
    private String highlightedColor = "yellow";

    @Setter
    private String serverMessage = "";


    @Inject
    public SuggestionPanel(FlippingCopilotConfig config,
                           SuggestionManager suggestionManager,
                           AccountStatusManager accountStatusManager,
                           PauseButton pauseButton,
                           BlockButton blockButton,
                           PreferencesPanel preferencesPanel,
                           OsrsLoginManager osrsLoginManager,
                           Client client, PausedManager pausedManager,
                           GrandExchangeUncollectedManager uncollectedManager,
                           ClientThread clientThread,
                           HighlightController highlightController,
                           ItemManager itemManager,
                           GrandExchange grandExchange,  PremiumInstanceController premiumInstanceController, FlipManager flipManager, CopilotLoginManager copilotLoginManager, FlipsDialogController flipsDialogController) {
        this.preferencesPanel = preferencesPanel;
        this.config = config;
        this.suggestionManager = suggestionManager;
        this.accountStatusManager = accountStatusManager;
        this.pauseButton = pauseButton;
        this.blockButton = blockButton;
        this.osrsLoginManager = osrsLoginManager;
        this.client = client;
        this.pausedManager = pausedManager;
        this.uncollectedManager = uncollectedManager;
        this.clientThread = clientThread;
        this.highlightController = highlightController;
        this.itemManager = itemManager;
        this.grandExchange = grandExchange;
        this.premiumInstanceController = premiumInstanceController;
        this.flipManager = flipManager;
        this.copilotLoginManager = copilotLoginManager;
        this.flipsDialogController = flipsDialogController;

        layeredPane.setLayout(null);
        setPreferredSize(new Dimension(MainPanel.CONTENT_WIDTH, 150));
        suggestedActionPanel = new JPanel(new BorderLayout());
        suggestedActionPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        suggestedActionPanel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));
        suggestedActionPanel.setBounds(0, 0, MainPanel.CONTENT_WIDTH, 150);

        JPanel suggestionContainer = new JPanel(new BorderLayout());
        suggestionContainer.setOpaque(true);
        suggestionContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        suggestionContainer.setPreferredSize(new Dimension(MainPanel.CONTENT_WIDTH - 10, 85));
        suggestedActionPanel.add(suggestionContainer, BorderLayout.CENTER);

        // Center panel for main suggestion content (icon and text)
        JPanel suggestionMainPanel = new JPanel();
        suggestionMainPanel.setLayout(new CardLayout());
        suggestionMainPanel.setOpaque(true);
        suggestionMainPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
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
        BufferedImage gearIcon = ImageUtil.loadImageResource(getClass(), "/preferences-icon.png");
        gearIcon = ImageUtil.resizeImage(gearIcon, 20, 20);
        BufferedImage recoloredIcon = ImageUtil.recolorImage(gearIcon, ColorScheme.LIGHT_GRAY_COLOR);
        gearButton = buildButton(recoloredIcon, "Settings", () -> {});
        gearButton.setEnabled(true);
        gearButton.setFocusable(true);
        gearButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        gearButton.setOpaque(true);
        ImageIcon iconOff = new ImageIcon(recoloredIcon);
        ImageIcon iconOn = new ImageIcon(ImageUtil.luminanceScale(recoloredIcon, BUTTON_HOVER_LUMINANCE));
        gearButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleGearClick();
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                gearButton.setIcon(iconOn);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                gearButton.setIcon(iconOff);
            }
        });
        gearButton.setOpaque(true);
        gearButton.setBounds(5, 5, 20, 20);

        layeredPane.add(gearButton, JLayeredPane.MODAL_LAYER);

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setPanelHeight(150);

        add(layeredPane);
    }

    private void handleGearClick() {
        isPreferencesPanelVisible = !isPreferencesPanelVisible;
        if (isPreferencesPanelVisible) {
            setPanelHeight(245);
        } else {
            setPanelHeight(150);
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

        JPanel centerPanel = new JPanel(new GridLayout(1, 5, 15, 0));
        centerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        BufferedImage graphIcon = ImageUtil.loadImageResource(getClass(), "/graph.png");
        graphButton = buildButton(graphIcon, "Price graph", () -> {
            if(config.priceGraphWebsite().equals(FlippingCopilotConfig.PriceGraphWebsite.FLIPPING_COPILOT)) {
                Suggestion suggestion = suggestionManager.getSuggestion();
                if(suggestion != null && !suggestion.getType().equals("wait")) {
                    flipsDialogController.showPriceGraphTab(null, true, null);
                } else {
                    flipsDialogController.showPriceGraphTab(null, false, null);
                }
            } else {
                Suggestion suggestion = suggestionManager.getSuggestion();
                String url = config.priceGraphWebsite().getUrl(suggestion.getName(), suggestion.getItemId());
                LinkBrowser.browse(url);
            }
        });
        centerPanel.add(graphButton);

        JPanel emptyPanel = new JPanel();
        emptyPanel.setOpaque(false);
        centerPanel.add(emptyPanel);
        centerPanel.add(pauseButton);
        centerPanel.add(blockButton);

        BufferedImage skipIcon = ImageUtil.loadImageResource(getClass(), "/skip.png");
        skipButton = buildButton(skipIcon, "Skip suggestion", () -> {
            showLoading();
            Suggestion s = suggestionManager.getSuggestion();
            if(s != null) {
                s.actioned = true;
            }
            accountStatusManager.setSkipSuggestion(s != null ? s.getId() : -1);
            suggestionManager.setSuggestionNeeded(true);
        });
        centerPanel.add(skipButton);

        buttonContainer.add(centerPanel, BorderLayout.CENTER);
    }


    private void setItemIcon(int itemId) {
        AsyncBufferedImage image = itemManager.getImage(itemId);
        if (image != null) {
            image.addTo(suggestionIcon);
            suggestionIcon.setVisible(true);
        }
    }

    public void setAdditionalInfoText(String text) {
        additionalInfoText.setText("<html><center>" + text + "</center></html>");
    }

    public void updateSuggestion(Suggestion suggestion) {
        NumberFormat formatter = NumberFormat.getNumberInstance();
        String suggestionString = "<html><center>";
        suggestionTextContainer.setVisible(false);
        additionalInfoText.setText("");
        switch (suggestion.getType()) {
            case "wait":
                suggestionString += "Wait <br>";
                break;
            case "abort":
                suggestionString += "Abort offer for<br><FONT COLOR=white>" + suggestion.getName() + "<br></FONT>";
                setItemIcon(suggestion.getItemId());
                break;
            case "buy":
            case "sell":
                String capitalisedAction = suggestion.getType().equals("buy") ? "Buy" : "Sell";
                suggestionString += capitalisedAction +
                        " <FONT COLOR=" + highlightedColor + ">" + formatter.format(suggestion.getQuantity()) + "</FONT><br>" +
                        "<FONT COLOR=white>" + suggestion.getName() + "</FONT><br>" +
                        "for <FONT COLOR=" + highlightedColor + ">" + formatter.format(suggestion.getPrice()) + "</FONT> gp<br>";
                setItemIcon(suggestion.getItemId());
                break;
            default:
                suggestionString += "Error processing suggestion<br>";
        }
        String additionalInfoMessage = Strings.isNullOrEmpty(suggestion.getMessage()) ? "" : "<br>" + suggestion.getMessage();

        suggestionString += "</center><html>";
        innerSuggestionMessage = "";
        if(!suggestion.getType().equals("wait")) {
            setButtonsVisible(true);
        }
        suggestionText.setText(suggestionString);
        suggestionText.setMaximumSize(new Dimension(suggestionText.getPreferredSize().width, Integer.MAX_VALUE));
        if (suggestion.getType().equals("buy")) {
            setAdditionalInfoText(formatExpectedProfitAndDuration(suggestion.getExpectedProfit(), suggestion.getExpectedDuration()) + additionalInfoMessage);
        } else if (suggestion.getType().equals("sell")) {
            Transaction t = new Transaction();
            t.setItemId(suggestion.getItemId());
            t.setPrice(suggestion.getPrice());
            t.setQuantity(suggestion.getQuantity());
            t.setAmountSpent(suggestion.getPrice() * suggestion.getQuantity());
            t.setType(OfferStatus.SELL);
            Integer accountId = copilotLoginManager.getAccountId(osrsLoginManager.getLastDisplayName());
            String text = "";
            if (accountId != null) {
                Long profit = flipManager.estimateTransactionProfit(accountId, t);
                if (profit != null) {
                    text = formatSellProfitLossAndDuration((double) profit, suggestion.getExpectedDuration());
                }
            }
            setAdditionalInfoText(text + additionalInfoMessage);
        } else {
            setAdditionalInfoText(additionalInfoMessage);
        }

        suggestionTextContainer.setVisible(true);
        suggestionTextContainer.revalidate();
        suggestionTextContainer.repaint();
    }

    public void suggestCollect() {
        setMessage("Collect items");
        setButtonsVisible(false);
    }

    public void suggestAddGp() {
        NumberFormat formatter = NumberFormat.getNumberInstance();
        setMessage("Add " +
                "at least <FONT COLOR=" + highlightedColor + ">" + formatter.format(MIN_GP_NEEDED_TO_FLIP)
                + "</FONT> gp<br>to your inventory<br>"
                + "to get a flip suggestion");
        setButtonsVisible(false);
    }

    public void suggestOpenGe() {
        setMessage("Open the Grand Exchange<br>"
                + "to get a flip suggestion");
        setButtonsVisible(false);
    }

    public void setIsPausedMessage() {
        setMessage("Suggestions are paused");
        setButtonsVisible(false);
    }

    public void setMessage(String message) {
        additionalInfoText.setVisible(false);
        innerSuggestionMessage = message;
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
        return suggestionText.isVisible() && "Collect items".equals(innerSuggestionMessage);
    }

    public void showLoading() {
        suggestionTextContainer.setVisible(false);
        setServerMessage("");
        spinner.show();
        setButtonsVisible(false);
        suggestionIcon.setVisible(false);
        additionalInfoText.setText("");
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
        Suggestion suggestion = suggestionManager.getSuggestion();
        setServerMessage("");
        if (suggestion == null) {
            return;
        }
        AccountStatus accountStatus = accountStatusManager.getAccountStatus();
        if(accountStatus == null) {
            return;
        }
        setServerMessage(suggestion.getMessage());
        boolean collectNeeded = accountStatus.isCollectNeeded(suggestion);
        if(collectNeeded && !uncollectedManager.HasUncollected(osrsLoginManager.getAccountHash())) {
            log.warn("tick {} collect is suggested but there is nothing to collect! suggestion: {} {} {}", client.getTickCount(), suggestion.getType(), suggestion.getQuantity(), suggestion.getItemId());
        }
        if (collectNeeded) {
            suggestCollect();
        } else if(suggestion.getType().equals("wait") && !grandExchange.isOpen() && accountStatus.emptySlotExists()) {
            suggestOpenGe();
        } else if (suggestion.getType().equals("wait") && accountStatus.moreGpNeeded()) {
            suggestAddGp();
        }  else {
            updateSuggestion(suggestion);
        }
        highlightController.redraw();
    }

    public void refresh() {
        log.debug("refreshing suggestion panel {}", client.getGameState());
        if(!SwingUtilities.isEventDispatchThread()) {
            // we always execute this in the Swing EDT thread
            SwingUtilities.invokeLater(this::refresh);
            return;
        }
        if(isPreferencesPanelVisible) {
            preferencesPanel.refresh();
            return;
        }
        if (pausedManager.isPaused()) {
            hideLoading();
            setIsPausedMessage();
            return;
        }

        String errorMessage = osrsLoginManager.getInvalidStateDisplayMessage();
        if (errorMessage != null) {
            hideLoading();
            setServerMessage("");
            setMessage(errorMessage);
            return;
        }

        if(suggestionManager.isSuggestionRequestInProgress()) {
            showLoading();
            return;
        }
        hideLoading();

        final HttpResponseException suggestionError = suggestionManager.getSuggestionError();
        if(suggestionError != null) {
            highlightController.redraw();
            setMessage("Error: " + suggestionError.getMessage());
            return;
        }

        if(!client.isClientThread()) {
            clientThread.invoke(this::displaySuggestion);
        } else {
            displaySuggestion();
        }
    }

    private String formatSellProfitLossAndDuration(Double expectedProfit, Double expectedDuration) {
        String formattedProfit = formatProfit(expectedProfit);
        Color color = config.profitAmountColor();
        if(expectedProfit < 0) {
            color = config.lossAmountColor();
        }
        String colorHex = String.format("#%06X", (0xFFFFFF & color.getRGB()));
        String text = "<b><font color='" + colorHex + "'>" + formattedProfit + "</font></b> profit";
        if (expectedDuration != null) {
            String formattedDuration = formatDuration(expectedDuration);
            text += " in <b>" + formattedDuration + "</b>";
        }
        return text;
    }

    private String formatExpectedProfitAndDuration(Double expectedProfit, Double expectedDuration) {
        if (expectedProfit == null || expectedDuration == null) {
            return "";
        }
        String formattedProfit = formatProfit(expectedProfit);
        String formattedDuration = formatDuration(expectedDuration);
        Color profitColor = config.profitAmountColor();

        String colorHex = String.format("#%06X", (0xFFFFFF & profitColor.getRGB()));
        return "<b><font color='" + colorHex + "'>" + formattedProfit + "</font></b> profit in <b>" + formattedDuration + "</b>";
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

    private String formatDuration(double durationSeconds) {
        int totalMinutes = (int) Math.round(durationSeconds / 60.0);
        // Round to nearest 5 minutes
        totalMinutes = Math.round(totalMinutes / 5.0f) * 5;
        totalMinutes = Math.max(totalMinutes, 5);
        if (totalMinutes < 60) {
            return totalMinutes + "min";
        } else {
            int hours = totalMinutes / 60;
            int minutes = totalMinutes % 60;
            if (minutes == 0) {
                return hours + "h";
            } else {
                return hours + "h " + minutes + "m";
            }
        }
    }
}