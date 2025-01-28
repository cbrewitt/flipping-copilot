package com.flippingcopilot.ui;

import com.flippingcopilot.controller.FlippingCopilotConfig;
import com.flippingcopilot.controller.HighlightController;
import com.flippingcopilot.model.*;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
    private final OsrsLoginManager osrsLoginManager;
    private final Client client;
    private final PausedManager pausedManager;
    private final GrandExchangeUncollectedManager uncollectedManager;
    private final ClientThread clientThread;
    private final HighlightController highlightController;

    private final JLabel suggestionText = new JLabel();
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

    @Setter
    private String serverMessage = "";


    @Inject
    public SuggestionPanel(FlippingCopilotConfig config,
                           SuggestionManager suggestionManager,
                           AccountStatusManager accountStatusManager,
                           PauseButton pauseButton,
                           PreferencesPanel preferencesPanel,
                           OsrsLoginManager osrsLoginManager,
                           Client client, PausedManager pausedManager,
                           GrandExchangeUncollectedManager uncollectedManager,
                           ClientThread clientThread,
                           HighlightController highlightController) {
        this.preferencesPanel = preferencesPanel;
        this.config = config;
        this.suggestionManager = suggestionManager;
        this.accountStatusManager = accountStatusManager;
        this.pauseButton = pauseButton;
        this.osrsLoginManager = osrsLoginManager;
        this.client = client;
        this.pausedManager = pausedManager;
        this.uncollectedManager = uncollectedManager;
        this.clientThread = clientThread;
        this.highlightController = highlightController;


        // Create the layered pane first
        layeredPane.setLayout(null);  // LayeredPane needs null layout

        // Create a main panel that will hold all the regular components
        suggestedActionPanel = new JPanel(new BorderLayout());
        suggestedActionPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        suggestedActionPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        suggestedActionPanel.setBounds(0, 0, 300, 150);  // Set appropriate size
        JLabel title = new JLabel("<html><center> <FONT COLOR=white><b>Suggested Action:" +
                "</b></FONT></center></html>");
        title.setHorizontalAlignment(SwingConstants.CENTER);
        suggestedActionPanel.add(title, BorderLayout.NORTH);

        JPanel suggestionContainer = new JPanel();
        suggestionContainer.setLayout(new CardLayout());
        suggestionContainer.setOpaque(true);
        suggestionContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        suggestionContainer.setPreferredSize(new Dimension(0, 85));
        suggestedActionPanel.add(suggestionContainer, BorderLayout.CENTER);

        suggestionText.setHorizontalAlignment(SwingConstants.CENTER);
        suggestionText.setOpaque(true);
        suggestionText.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        suggestionContainer.add(suggestionText);
        suggestionContainer.add(spinner);
        setupButtonContainer();
        suggestedActionPanel.add(buttonContainer, BorderLayout.SOUTH);


        layeredPane.add(suggestedActionPanel, JLayeredPane.DEFAULT_LAYER);

        // Build the suggestion preferences panel:
        this.preferencesPanel.setVisible(false);
        layeredPane.add(this.preferencesPanel, JLayeredPane.DEFAULT_LAYER);

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
        // Replace the existing gear button MouseAdapter with this implementation
        gearButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isEventDispatchThread()) {
                    SwingUtilities.invokeLater(() -> handleGearClick());
                    return;
                }
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
        layeredPane.add(gearButton, JLayeredPane.PALETTE_LAYER);

        // Set up the main panel
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setPreferredSize(new Dimension(0, 150));

        add(layeredPane);

        setupPauseButton();

        // Add a component listener to handle resizing
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                preferencesPanel.setBounds(0, 0, getWidth(), getHeight());
                suggestedActionPanel.setBounds(0, 0, getWidth(), getHeight());
                layeredPane.setPreferredSize(new Dimension(getWidth(), getHeight()));
            }
        });
    }

    // Add this as a private method in the class
    private void handleGearClick() {
        log.debug("clicked suggestion preferences");
        isPreferencesPanelVisible = !isPreferencesPanelVisible;
        preferencesPanel.setVisible(isPreferencesPanelVisible);
        suggestedActionPanel.setVisible(!isPreferencesPanelVisible);
        refresh();
        layeredPane.revalidate();
        layeredPane.repaint();
    }

    private void setupButtonContainer() {
        buttonContainer.setLayout(new BorderLayout());
        buttonContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setupGraphButton();

        // skip button
        BufferedImage graphIcon = ImageUtil.loadImageResource(getClass(), "/skip.png");
        skipButton = buildButton(graphIcon, "Skip suggestion", () -> {
            showLoading();
            Suggestion s = suggestionManager.getSuggestion();
            accountStatusManager.setSkipSuggestion(s != null ? s.getId(): -1);
            suggestionManager.setSuggestionNeeded(true);
        });
        buttonContainer.add(skipButton, BorderLayout.EAST);
    }

    private void setupGraphButton() {
        BufferedImage graphIcon = ImageUtil.loadImageResource(getClass(), "/graph.png");
        graphButton = buildButton(graphIcon, "Price graph", () -> {
            Suggestion suggestion = suggestionManager.getSuggestion();
            String url = config.priceGraphWebsite().getUrl(suggestion.getName(), suggestion.getItemId());
            LinkBrowser.browse(url);
        });
        buttonContainer.add(graphButton, BorderLayout.WEST);
    }

    private void setupPauseButton() {
        Box box = Box.createHorizontalBox();
        box.add(Box.createHorizontalGlue());
        box.add(pauseButton);
        box.add(Box.createHorizontalGlue());
        buttonContainer.add(box, BorderLayout.CENTER);
    }


    public void updateSuggestion(Suggestion suggestion) {
        NumberFormat formatter = NumberFormat.getNumberInstance();
        String suggestionString = "<html><center>";

        switch (suggestion.getType()) {
            case "wait":
                suggestionString += "Wait <br>";
                break;
            case "abort":
                suggestionString += "Abort offer for<br><FONT COLOR=white>" + suggestion.getName() + "<br></FONT>";
                break;
            case "buy":
            case "sell":
                String capitalisedAction = suggestion.getType().equals("buy") ? "Buy" : "Sell";
                suggestionString += capitalisedAction +
                        " <FONT COLOR=yellow>" + formatter.format(suggestion.getQuantity()) + "</FONT><br>" +
                        "<FONT COLOR=white>" + suggestion.getName() + "</FONT><br>" +
                        "for <FONT COLOR=yellow>" + formatter.format(suggestion.getPrice()) + "</FONT> gp<br>";
                break;
            default:
                suggestionString += "Error processing suggestion<br>";
        }
        suggestionString += suggestion.getMessage();
        suggestionString += "</center><html>";
        innerSuggestionMessage = "";
        suggestionText.setText(suggestionString);
        suggestionText.setVisible(true);
        if(!suggestion.getType().equals("wait")) {
            setButtonsVisible(true);
        }
    }

    public void suggestCollect() {
        setMessage("Collect items");
        setButtonsVisible(false);
    }

    public void suggestAddGp() {
        NumberFormat formatter = NumberFormat.getNumberInstance();
        setMessage("Add at least <FONT COLOR=yellow>" + formatter.format(MIN_GP_NEEDED_TO_FLIP)
                               + "</FONT> gp<br>to your inventory<br>"
                               + "to get a flip suggestion");
    }

    public void setIsPausedMessage() {
        setMessage("Suggestions are paused");
        setButtonsVisible(false);
    }

    public void setMessage(String message) {
        innerSuggestionMessage = message;
        suggestionText.setText("<html><center>" + innerSuggestionMessage +  "<br>" + serverMessage + "</center><html>");
        setButtonsVisible(false);
    }

    public boolean isCollectItemsSuggested() {
        return suggestionText.isVisible() && "Collect items".equals(innerSuggestionMessage);
    }

    public void showLoading() {
        suggestionText.setVisible(false);
        setServerMessage("");
        spinner.show();
        setButtonsVisible(false);
    }

    public void hideLoading() {
        spinner.hide();
        suggestionText.setVisible(true);
    }

    private void setButtonsVisible(boolean visible) {
        skipButton.setVisible(visible);
        graphButton.setVisible(visible);
    }

    public void displaySuggestion() {
        Suggestion suggestion = suggestionManager.getSuggestion();
        if (suggestion == null) {
            return;
        }
        AccountStatus accountStatus = accountStatusManager.getAccountStatus();
        setServerMessage(suggestion.getMessage());
        boolean collectNeeded = accountStatus.isCollectNeeded(suggestion);
        if(collectNeeded && !uncollectedManager.HasUncollected(osrsLoginManager.getAccountHash())) {
            log.warn("tick {} collect is suggested but there is nothing to collect! suggestion: {} {} {}", client.getTickCount(), suggestion.getType(), suggestion.getQuantity(), suggestion.getItemId());
        }
        if (collectNeeded) {
            suggestCollect();
        } else if (suggestion.getType().equals("wait") && accountStatus.moreGpNeeded()) {
            suggestAddGp();
        } else {
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
        }
        if (pausedManager.isPaused()) {
            setIsPausedMessage();
            hideLoading();
            return;
        }

        String errorMessage = osrsLoginManager.getInvalidStateDisplayMessage();
        if (errorMessage != null) {
            setMessage(errorMessage);
            hideLoading();
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
}
