package com.flippingcopilot.ui;

import com.flippingcopilot.model.OsrsLoginManager;
import com.flippingcopilot.model.SuggestionPreferencesManager;
import com.flippingcopilot.model.SuggestionManager;
import net.runelite.client.ui.ColorScheme;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;

@Singleton
public class PreferencesPanel extends JPanel {

    public static String LOGIN_TO_MANAGE_SETTINGS = "Log in to manage settings";

    private final OsrsLoginManager osrsLoginManager;
    private final SuggestionPreferencesManager preferencesManager;
    private final JPanel sellOnlyButton;
    private final SellOnlyModeToggleButton sellOnlyModeToggleButton;
    private final BlacklistDropdownPanel blacklistDropdownPanel;
    private final JLabel messageText = new JLabel();

    @Inject
    public PreferencesPanel(
            OsrsLoginManager osrsLoginManager,
            SuggestionManager suggestionManager,
            SuggestionPreferencesManager suggestionPreferencesManager,
            SuggestionPreferencesManager preferencesManager,
            BlacklistDropdownPanel blocklistDropdownPanel) {
        super();
        this.osrsLoginManager = osrsLoginManager;
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
        sellOnlyModeToggleButton = new SellOnlyModeToggleButton();
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        sellOnlyButton = new JPanel();
        sellOnlyButton.setLayout(new BorderLayout());
        sellOnlyButton.setOpaque(false);
        add(sellOnlyButton);
        JLabel buttonText = new JLabel("Sell-only Mode");
        sellOnlyButton.add(buttonText, BorderLayout.LINE_START);
        sellOnlyButton.add(sellOnlyModeToggleButton, BorderLayout.LINE_END);
        add(Box.createRigidArea(new Dimension(0, 3)));
        add(this.blacklistDropdownPanel);
        sellOnlyModeToggleButton.addItemListener(i ->
        {
            suggestionPreferencesManager.setSellOnlyMode(sellOnlyModeToggleButton.isSelected());
            suggestionManager.setSuggestionNeeded(true);
        });
        add(Box.createRigidArea(new Dimension(0, 40)));
        add(messageText);
        messageText.setText(LOGIN_TO_MANAGE_SETTINGS);
        messageText.setVisible(false);
        messageText.setAlignmentX(Component.CENTER_ALIGNMENT);
        messageText.setHorizontalAlignment(SwingConstants.CENTER);
    }

    public void refresh() {
        if(osrsLoginManager.getPlayerDisplayName() != null) {
            sellOnlyModeToggleButton.setSelected(preferencesManager.getPreferences().isSellOnlyMode());
            sellOnlyButton.setVisible(true);
            blacklistDropdownPanel.setVisible(true);
            messageText.setVisible(false);
        } else {
            sellOnlyButton.setVisible(false);
            blacklistDropdownPanel.setVisible(false);
            messageText.setVisible(true);
        }
    }
}
