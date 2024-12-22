package com.flippingcopilot.ui;

import com.flippingcopilot.model.SuggestionManager;
import com.flippingcopilot.model.SuggestionPreferencesManager;
import net.runelite.client.ui.ColorScheme;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;


@Singleton
public class ControlPanel extends JPanel {

    @Inject
    public ControlPanel(SuggestionPreferencesManager suggestionPreferencesManager,
                        SuggestionManager suggestionManager,
                        BlacklistDropdownPanel blocklistDropdownPanel) {
        SellOnlyModeToggleButton sellOnlyModeToggleButton = new SellOnlyModeToggleButton();
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(5, 2, 5, 2));

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BorderLayout());
        buttonPanel.setOpaque(false);
        add(buttonPanel);

        JLabel buttonText = new JLabel("Sell-only Mode");
        buttonPanel.add(buttonText, BorderLayout.LINE_START);
        buttonPanel.add(sellOnlyModeToggleButton, BorderLayout.LINE_END);
        add(Box.createRigidArea(new Dimension(0, 3)));

        add(blocklistDropdownPanel);
        sellOnlyModeToggleButton.addItemListener(i ->
        {
            suggestionPreferencesManager.setSellOnlyMode(sellOnlyModeToggleButton.isSelected());
            suggestionManager.setSuggestionNeeded(true);
        });
    }
}
