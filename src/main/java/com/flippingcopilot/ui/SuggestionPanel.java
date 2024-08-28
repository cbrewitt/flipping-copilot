package com.flippingcopilot.ui;

import com.flippingcopilot.controller.FlippingCopilotConfig;
import com.flippingcopilot.controller.FlippingCopilotPlugin;
import com.flippingcopilot.model.Suggestion;
import lombok.Setter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;

import static com.flippingcopilot.ui.UIUtilities.*;
import static com.flippingcopilot.util.Constants.MIN_GP_NEEDED_TO_FLIP;

public class SuggestionPanel extends JPanel {
    private FlippingCopilotPlugin plugin;
    private final JLabel suggestionText = new JLabel();
    public final Spinner spinner = new Spinner();
    private JLabel skipButton;
    private final JPanel buttonContainer = new JPanel();
    private JLabel graphButton;
    private FlippingCopilotConfig config;

    @Setter
    private String serverMessage = "";

    public SuggestionPanel(FlippingCopilotConfig config) {
        this.config = config;
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        setPreferredSize(new Dimension(0, 150));

        JLabel title = new JLabel("<html><center> <FONT COLOR=white><b>Suggested Action:" +
                "</b></FONT></center></html>");
        title.setHorizontalAlignment(SwingConstants.CENTER);
        add(title, BorderLayout.NORTH);

        JPanel suggestionContainer = new JPanel();
        suggestionContainer.setLayout(new CardLayout());
        suggestionContainer.setOpaque(true);
        suggestionContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        suggestionContainer.setPreferredSize(new Dimension(0, 85));
        add(suggestionContainer, BorderLayout.CENTER);

        suggestionText.setHorizontalAlignment(SwingConstants.CENTER);
        suggestionText.setOpaque(true);
        suggestionText.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        suggestionContainer.add(suggestionText);
        suggestionContainer.add(spinner);
        setupButtonContainer();
        suggestLogin();
    }

    public void init(FlippingCopilotPlugin plugin) {
        this.plugin = plugin;
        setupPauseButton();
    }

    private void setupButtonContainer() {
        buttonContainer.setLayout(new BorderLayout());
        buttonContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        //buttonContainer.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
        add(buttonContainer, BorderLayout.SOUTH);
        setupGraphButton();
        setupSkipButton();
    }

    private void setupGraphButton() {
        BufferedImage graphIcon = ImageUtil.loadImageResource(getClass(), "/graph.png");
        graphButton = buildButton(graphIcon, "Price graph", () -> {
            Suggestion suggestion = plugin.suggestionHandler.getCurrentSuggestion();
            String url = config.priceGraphWebsite().getUrl(suggestion.getName(), suggestion.getItemId());
            LinkBrowser.browse(url);
        });
        buttonContainer.add(graphButton, BorderLayout.WEST);
    }


    private void setupSkipButton() {
        BufferedImage graphIcon = ImageUtil.loadImageResource(getClass(), "/skip.png");
        skipButton = buildButton(graphIcon, "Skip suggestion", () -> {
            showLoading();
            plugin.suggestionHandler.skipCurrentSuggestion();
        });
        buttonContainer.add(skipButton, BorderLayout.EAST);
    }

    private void setupPauseButton() {
        PauseButton pauseButton = new PauseButton(plugin);
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

    public void suggestLogin() {
        setMessage("Log in to the game<br>to get a flip suggestion");
        setButtonsVisible(false);
    }

    public void setIsPausedMessage() {
        setMessage("Suggestions are paused");
        setButtonsVisible(false);
    }

    public void showConnectionError() {
        setMessage("Failed to connect to server");
        setButtonsVisible(false);
    }

    public void setMessage(String message) {
        suggestionText.setText("<html><center>" + message +  "<br>" + serverMessage + "</center><html>");
        setButtonsVisible(false);
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
}
