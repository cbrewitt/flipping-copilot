package com.flippingcopilot.ui;

import com.flippingcopilot.model.OsrsLoginManager;
import com.flippingcopilot.model.SuggestionPreferencesManager;
import com.flippingcopilot.model.SuggestionManager;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.ColorScheme;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;

@Singleton
public class PreferencesPanel extends JPanel {

    public static String LOGIN_TO_MANAGE_SETTINGS = "Log in to manage settings";

    private final Client client;
    private final OsrsLoginManager osrsLoginManager;
    private final SuggestionPreferencesManager preferencesManager;
    private final JPanel sellOnlyButton;
    private final PreferencesToggleButton sellOnlyModeToggleButton;
    private final JPanel f2pOnlyButton;
    private final PreferencesToggleButton f2pOnlyModeToggleButton;
    private final BlacklistDropdownPanel blacklistDropdownPanel;
    private final JLabel messageText = new JLabel();

    @Inject
    public PreferencesPanel(
            OsrsLoginManager osrsLoginManager,
            SuggestionManager suggestionManager,
            SuggestionPreferencesManager suggestionPreferencesManager, Client client,
            SuggestionPreferencesManager preferencesManager,
            BlacklistDropdownPanel blocklistDropdownPanel) {
        super();
        this.osrsLoginManager = osrsLoginManager;
        this.client = client;
        this.preferencesManager = preferencesManager;
        this.blacklistDropdownPanel = blocklistDropdownPanel;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        setBounds(0, 0, 300, 150);

        JLabel preferencesTitle = new JLabel("Suggestion Settings");
        preferencesTitle.setForeground(Color.WHITE);
        preferencesTitle.setFont(preferencesTitle.getFont().deriveFont(Font.BOLD));
        preferencesTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        preferencesTitle.setMinimumSize(new Dimension(300, preferencesTitle.getPreferredSize().height));
        preferencesTitle.setMaximumSize(new Dimension(300, preferencesTitle.getPreferredSize().height));
        preferencesTitle.setHorizontalAlignment(SwingConstants.CENTER);
        add(preferencesTitle);
        add(Box.createRigidArea(new Dimension(0, 6)));
        sellOnlyModeToggleButton = new PreferencesToggleButton();
        sellOnlyButton = new JPanel();
        sellOnlyButton.setLayout(new BorderLayout());
        sellOnlyButton.setOpaque(false);
        add(sellOnlyButton);
        JLabel buttonText = new JLabel("Sell-only mode");
        sellOnlyButton.add(buttonText, BorderLayout.LINE_START);
        sellOnlyButton.add(sellOnlyModeToggleButton, BorderLayout.LINE_END);
        sellOnlyModeToggleButton.addItemListener(i ->
        {
            suggestionPreferencesManager.setSellOnlyMode(sellOnlyModeToggleButton.isSelected());
            suggestionManager.setSuggestionNeeded(true);
        });
        add(Box.createRigidArea(new Dimension(0, 3)));

        f2pOnlyModeToggleButton = new PreferencesToggleButton();
        f2pOnlyButton = new JPanel();
        f2pOnlyButton.setLayout(new BorderLayout());
        f2pOnlyButton.setOpaque(false);
        add(f2pOnlyButton);
        JLabel f2pOnlyButtonText = new JLabel("F2P-only mode");
        f2pOnlyButton.add(f2pOnlyButtonText, BorderLayout.LINE_START);
        f2pOnlyButton.add(f2pOnlyModeToggleButton, BorderLayout.LINE_END);
        f2pOnlyModeToggleButton.addItemListener(i ->
        {
            suggestionPreferencesManager.setF2pOnlyMode(f2pOnlyModeToggleButton.isSelected());
            suggestionManager.setSuggestionNeeded(true);
        });
        add(Box.createRigidArea(new Dimension(0, 3)));

        add(this.blacklistDropdownPanel);
        add(Box.createRigidArea(new Dimension(0, 30)));
        add(messageText);
        messageText.setText(LOGIN_TO_MANAGE_SETTINGS);
        messageText.setVisible(false);
        messageText.setAlignmentX(Component.CENTER_ALIGNMENT);
        messageText.setHorizontalAlignment(SwingConstants.CENTER);
    }

    public void refresh() {
        if(osrsLoginManager.getPlayerDisplayName() != null && client.getGameState() == GameState.LOGGED_IN) {
            sellOnlyModeToggleButton.setSelected(preferencesManager.getPreferences().isSellOnlyMode());
            sellOnlyButton.setVisible(true);
            f2pOnlyModeToggleButton.setSelected(preferencesManager.getPreferences().isF2pOnlyMode());
            f2pOnlyButton.setVisible(true);
            blacklistDropdownPanel.setVisible(true);
            messageText.setVisible(false);
        } else {
            sellOnlyButton.setVisible(false);
            f2pOnlyButton.setVisible(false);
            blacklistDropdownPanel.setVisible(false);
            messageText.setVisible(true);
        }
    }
}
