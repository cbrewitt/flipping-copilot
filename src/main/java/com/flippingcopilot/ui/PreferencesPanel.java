package com.flippingcopilot.ui;

import com.flippingcopilot.controller.PremiumInstanceController;
import com.flippingcopilot.model.SuggestionPreferencesManager;
import com.flippingcopilot.model.SuggestionManager;
import net.runelite.client.ui.ColorScheme;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;

@Singleton
public class PreferencesPanel extends JPanel {

    private final SuggestionPreferencesManager preferencesManager;
    private final JPanel sellOnlyButton;
    private final PreferencesToggleButton sellOnlyModeToggleButton;
    private final JPanel f2pOnlyButton;
    private final PreferencesToggleButton f2pOnlyModeToggleButton;
    private final BlacklistDropdownPanel blacklistDropdownPanel;

    @Inject
    public PreferencesPanel(
            SuggestionManager suggestionManager,
            SuggestionPreferencesManager preferencesManager,
            BlacklistDropdownPanel blocklistDropdownPanel,
            PremiumInstanceController premiumInstanceController) {
        super();
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
        add(Box.createRigidArea(new Dimension(0, 8)));
        add(this.blacklistDropdownPanel);

        sellOnlyModeToggleButton = new PreferencesToggleButton("Disable sell-only mode", "Enable sell-only mode");
        sellOnlyButton = new JPanel();
        sellOnlyButton.setLayout(new BorderLayout());
        sellOnlyButton.setOpaque(false);
        add(sellOnlyButton);
        JLabel buttonText = new JLabel("Sell-only mode");
        sellOnlyButton.add(buttonText, BorderLayout.LINE_START);
        sellOnlyButton.add(sellOnlyModeToggleButton, BorderLayout.LINE_END);
        sellOnlyModeToggleButton.addItemListener(i ->
        {
            preferencesManager.setSellOnlyMode(sellOnlyModeToggleButton.isSelected());
            suggestionManager.setSuggestionNeeded(true);
        });
        add(Box.createRigidArea(new Dimension(0, 3)));

        f2pOnlyModeToggleButton = new PreferencesToggleButton("Disable F2P-only mode",  "Enable F2P-only mode");
        f2pOnlyButton = new JPanel();
        f2pOnlyButton.setLayout(new BorderLayout());
        f2pOnlyButton.setOpaque(false);
        add(f2pOnlyButton);
        JLabel f2pOnlyButtonText = new JLabel("F2P-only mode");
        f2pOnlyButton.add(f2pOnlyButtonText, BorderLayout.LINE_START);
        f2pOnlyButton.add(f2pOnlyModeToggleButton, BorderLayout.LINE_END);
        f2pOnlyModeToggleButton.addItemListener(i ->
        {
            preferencesManager.setF2pOnlyMode(f2pOnlyModeToggleButton.isSelected());
            suggestionManager.setSuggestionNeeded(true);
        });

        // Premium instances panel - moved to the bottom
        add(Box.createRigidArea(new Dimension(0, 3)));
        JPanel premiumInstancesPanel = new JPanel();
        premiumInstancesPanel.setLayout(new BorderLayout());
        premiumInstancesPanel.setOpaque(false);
        JLabel premiumInstancesLabel = new JLabel("Premium accounts:");
        JButton manageButton = new JButton("manage");
        manageButton.addActionListener(e -> {
            premiumInstanceController.loadAndOpenPremiumInstanceDialog();
        });
        premiumInstancesPanel.add(premiumInstancesLabel, BorderLayout.LINE_START);
        premiumInstancesPanel.add(manageButton, BorderLayout.LINE_END);
        add(premiumInstancesPanel);
    }

    public void refresh() {
        if(!SwingUtilities.isEventDispatchThread()) {
            // we always execute this in the Swing EDT thread
            SwingUtilities.invokeLater(this::refresh);
            return;
        }

        sellOnlyModeToggleButton.setSelected(preferencesManager.getPreferences().isSellOnlyMode());
        sellOnlyButton.setVisible(true);
        f2pOnlyModeToggleButton.setSelected(preferencesManager.getPreferences().isF2pOnlyMode());
        f2pOnlyButton.setVisible(true);
        blacklistDropdownPanel.setVisible(true);
    }
}