package com.flippingcopilot.ui;

import com.flippingcopilot.controller.FlippingCopilotPlugin;
import com.flippingcopilot.model.Suggestion;
import lombok.Setter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;

import static com.flippingcopilot.ui.UIUtilities.buildUriButton;
import static com.flippingcopilot.util.Constants.MIN_GP_NEEDED_TO_FLIP;

public class SuggestionPanel extends JPanel {
    private FlippingCopilotPlugin plugin;
    private final JLabel suggestionText = new JLabel();
    public final Spinner spinner = new Spinner();
    private final JLabel skipButton = new JLabel("skip");
    private final JPanel buttonContainer = new JPanel();

    @Setter
    private String serverMessage = "";

    public SuggestionPanel() {
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setPreferredSize(new Dimension(0, 135));

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
    }

    private void setupButtonContainer() {
        // this container appears at the bottom of the suggestion panel
        // It contains the graph button on the left and the skip button on the right
        buttonContainer.setLayout(new BorderLayout());
        buttonContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        buttonContainer.setPreferredSize(new Dimension(0, 20));
        add(buttonContainer, BorderLayout.SOUTH);

        // add the graph button to the left of the button container
        setupGraphButton();
        // add the skip button to the right of the button container
        setupSkipButton();
    }

    private void setupGraphButton() {
        BufferedImage graphIcon = ImageUtil.loadImageResource(getClass(), "/graph.png");
        JLabel graphButton = buildUriButton(graphIcon, "Price graph", "https://prices.runescape.wiki/osrs/item/562");
        buttonContainer.add(graphButton, BorderLayout.WEST);
    }


    private void setupSkipButton() {
        skipButton.setForeground(Color.GRAY);
        skipButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                skipButton.setForeground(Color.WHITE);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                skipButton.setForeground(Color.GRAY);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                showLoading();
                plugin.suggestionHandler.skipCurrentSuggestion();
            }
        });

        skipButton.setHorizontalAlignment(SwingConstants.RIGHT);
        buttonContainer.add(skipButton, BorderLayout.EAST);
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
            skipButton.setVisible(true);
        }
    }

    public void suggestCollect() {
        setMessage("Collect items");
        skipButton.setVisible(false);
    }

    public void suggestAddGp() {
        NumberFormat formatter = NumberFormat.getNumberInstance();
        setMessage("Add at least <FONT COLOR=yellow>" + formatter.format(MIN_GP_NEEDED_TO_FLIP)
                               + "</FONT> gp<br>to your inventory<br>"
                               + "to get a flip suggestion");
    }

    public void suggestLogin() {
        setMessage("Log in to the game<br>to get a flip suggestion");
        skipButton.setVisible(false);
    }

    public void showConnectionError() {
        setMessage("Failed to connect to server");
        skipButton.setVisible(false);
    }

    public void setMessage(String message) {
        suggestionText.setText("<html><center>" + message +  "<br>" + serverMessage + "</center><html>");
        skipButton.setVisible(false);
    }

    public void showLoading() {
        suggestionText.setVisible(false);
        setServerMessage("");
        spinner.show();
        skipButton.setVisible(false);
    }

    public void hideLoading() {
        spinner.hide();
        suggestionText.setVisible(true);
    }
}
