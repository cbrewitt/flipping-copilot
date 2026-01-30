package com.flippingcopilot.ui;

import com.flippingcopilot.controller.DumpsStreamController;
import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.controller.PremiumInstanceController;
import com.flippingcopilot.model.OsrsLoginManager;
import com.flippingcopilot.model.SuggestionPreferencesManager;
import com.flippingcopilot.model.SuggestionManager;
import com.flippingcopilot.ui.components.ItemSearchMultiSelect;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;

@Slf4j
@Singleton
public class PreferencesPanel extends JPanel {

    private final SuggestionPreferencesManager preferencesManager;
    private final OsrsLoginManager osrsLoginManager;
    private final JPanel sellOnlyButton;
    private final PreferencesToggleButton sellOnlyModeToggleButton;
    private final JPanel f2pOnlyButton;
    private final PreferencesToggleButton f2pOnlyModeToggleButton;
    private final ItemSearchMultiSelect blocklistDropdownPanel;
    private final JComboBox<String> profileSelector;
    private final JButton addProfileButton;
    private final JButton deleteProfileButton;
    private final JSpinner reservedSlotsSpinner;
    private final JPanel dumpSuggestionsPanel;
    private final PreferencesToggleButton dumpSuggestionsToggleButton;
    private final JPanel preferencesContent;
    private final JPanel loginPromptPanel;

    @Inject
    public PreferencesPanel(
            SuggestionManager suggestionManager,
            SuggestionPreferencesManager preferencesManager,
            PremiumInstanceController premiumInstanceController,
            ItemController itemController,
            OsrsLoginManager osrsLoginManager,
            DumpsStreamController dumpsStreamController) {
        super();
        this.preferencesManager = preferencesManager;
        this.osrsLoginManager = osrsLoginManager;

        blocklistDropdownPanel = new ItemSearchMultiSelect(
                () -> new HashSet<>(preferencesManager.blockedItems()),
                itemController::allItemIds,
                itemController::search,
                (bl) -> {
                    preferencesManager.setBlockedItems(bl);
                    suggestionManager.setSuggestionNeeded(true);
                },
                "Item blocklist...",
                SwingUtilities.getWindowAncestor(this));

        setLayout(new CardLayout());
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        preferencesContent = new JPanel();
        preferencesContent.setLayout(new BoxLayout(preferencesContent, BoxLayout.Y_AXIS));
        preferencesContent.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JLabel preferencesTitle = new JLabel("Suggestion Settings");
        preferencesTitle.setForeground(Color.WHITE);
        preferencesTitle.setFont(preferencesTitle.getFont().deriveFont(Font.BOLD));
        preferencesTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        preferencesTitle.setMinimumSize(new Dimension(MainPanel.CONTENT_WIDTH - 30, preferencesTitle.getPreferredSize().height));
        preferencesTitle.setMaximumSize(new Dimension(MainPanel.CONTENT_WIDTH - 30, preferencesTitle.getPreferredSize().height));
        preferencesTitle.setHorizontalAlignment(SwingConstants.CENTER);
        preferencesContent.add(preferencesTitle);
        preferencesContent.add(Box.createRigidArea(new Dimension(0, 8)));

        loginPromptPanel = new JPanel(new GridBagLayout());
        loginPromptPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        JLabel loginPrompt = new JLabel("<html><center>Log in to the game<br>to get a flip suggestion</center></html>");
        loginPrompt.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        loginPromptPanel.add(loginPrompt);

        add(preferencesContent, "preferences");
        add(loginPromptPanel, "login");

        // Profile selector panel
        JPanel profilePanel = new JPanel();
        profilePanel.setLayout(new BorderLayout());
        profilePanel.setOpaque(false);
        profilePanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));

        // Panel for dropdown and buttons
        JPanel profileControlPanel = new JPanel();
        profileControlPanel.setLayout(new BoxLayout(profileControlPanel, BoxLayout.X_AXIS));
        profileControlPanel.setOpaque(false);

        // Initialize profile model with default
        profileSelector = new JComboBox<>();
        profileSelector.setPreferredSize(new Dimension(160, 25));
        profileSelector.setMaximumSize(new Dimension(160, 25));
        profileSelector.addActionListener(e -> {
            String selectedProfile = (String) profileSelector.getSelectedItem();
            if (selectedProfile != null && !selectedProfile.equals(preferencesManager.getCurrentProfile())) {
                preferencesManager.setCurrentProfile(selectedProfile);
                refresh();
            }
        });

        // Add button for creating new profiles
        addProfileButton = new JButton("+");
        addProfileButton.setPreferredSize(new Dimension(15, 25));
        addProfileButton.setMaximumSize(new Dimension(15, 25));
        addProfileButton.setToolTipText("Add new profile");
        addProfileButton.addActionListener(e -> {
            String newProfileName = JOptionPane.showInputDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "Enter new profile name (must be valid file name):",
                    "New preferences profile",
                    JOptionPane.PLAIN_MESSAGE);
            if (newProfileName != null && !newProfileName.trim().isEmpty()) {
                newProfileName = newProfileName.trim();
                try {
                    preferencesManager.addProfile(newProfileName);
                    refresh();
                } catch (IOException ex) {
                    log.error("adding new profile: {}", newProfileName, ex);
                    JOptionPane.showMessageDialog(
                            SwingUtilities.getWindowAncestor(this),
                            "Error adding new profile: "+ ex.getMessage(),
                            "Add profile failed",
                            JOptionPane.WARNING_MESSAGE);
                }
            }
        });

        // Delete button for removing custom profiles
        deleteProfileButton = new JButton("-");
        deleteProfileButton.setPreferredSize(new Dimension(15, 25));
        deleteProfileButton.setMaximumSize(new Dimension(15, 25));
        deleteProfileButton.setToolTipText("Delete current profile");
        deleteProfileButton.addActionListener(e -> {
            String selectedProfile = (String) profileSelector.getSelectedItem();
            if (selectedProfile != null) {
                int result = JOptionPane.showConfirmDialog(
                        SwingUtilities.getWindowAncestor(this),
                        "Delete profile '" + selectedProfile + "'?",
                        "Delete Profile",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE);
                if (result == JOptionPane.YES_OPTION) {
                    ((DefaultComboBoxModel<String>) profileSelector.getModel()).removeElement(selectedProfile);
                    try {
                        preferencesManager.deleteSelectedProfile();
                        profileSelector.setSelectedItem(preferencesManager.getCurrentProfile());
                    } catch (IOException ex) {
                        log.error("removing profile: {}", selectedProfile, ex);
                        JOptionPane.showMessageDialog(
                                SwingUtilities.getWindowAncestor(this),
                                "Error deleting profile: "+ ex.getMessage(),
                                "Remove profile failed",
                                JOptionPane.WARNING_MESSAGE);
                    }
                    refresh();
                }
            }
        });

        profileControlPanel.add(profileSelector);
        profileControlPanel.add(Box.createRigidArea(new Dimension(5, 0)));
        profileControlPanel.add(addProfileButton);
        profileControlPanel.add(Box.createRigidArea(new Dimension(2, 0)));
        profileControlPanel.add(deleteProfileButton);

        profilePanel.add(profileControlPanel, BorderLayout.LINE_START);
        preferencesContent.add(profilePanel);

        // Blocklist dropdown panel
        blocklistDropdownPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(5, 0, 5, 0),
                blocklistDropdownPanel.getBorder()));
        preferencesContent.add(blocklistDropdownPanel);

        // Sell-only mode toggle
        sellOnlyModeToggleButton = new PreferencesToggleButton("Disable sell-only mode", "Enable sell-only mode");
        sellOnlyButton = new JPanel();
        sellOnlyButton.setLayout(new BorderLayout());
        sellOnlyButton.setOpaque(false);
        preferencesContent.add(sellOnlyButton);
        JLabel buttonText = new JLabel("Sell-only mode");
        sellOnlyButton.add(buttonText, BorderLayout.LINE_START);
        sellOnlyButton.add(sellOnlyModeToggleButton, BorderLayout.LINE_END);
        sellOnlyModeToggleButton.addItemListener(i -> {
            preferencesManager.setSellOnlyMode(sellOnlyModeToggleButton.isSelected());
            suggestionManager.setSuggestionNeeded(true);
        });
        preferencesContent.add(Box.createRigidArea(new Dimension(0, 3)));

        // F2P-only mode toggle
        f2pOnlyModeToggleButton = new PreferencesToggleButton("Disable F2P-only mode",  "Enable F2P-only mode");
        f2pOnlyButton = new JPanel();
        f2pOnlyButton.setLayout(new BorderLayout());
        f2pOnlyButton.setOpaque(false);
        preferencesContent.add(f2pOnlyButton);
        JLabel f2pOnlyButtonText = new JLabel("F2P-only mode");
        f2pOnlyButton.add(f2pOnlyButtonText, BorderLayout.LINE_START);
        f2pOnlyButton.add(f2pOnlyModeToggleButton, BorderLayout.LINE_END);
        f2pOnlyModeToggleButton.addItemListener(i -> {
            preferencesManager.setF2pOnlyMode(f2pOnlyModeToggleButton.isSelected());
            suggestionManager.setSuggestionNeeded(true);
        });

        preferencesContent.add(Box.createRigidArea(new Dimension(0, 3)));
        // Dump suggestions toggle
        dumpSuggestionsToggleButton = new PreferencesToggleButton("Disable dump suggestions", "Receive dump suggestions");
        dumpSuggestionsPanel = new JPanel(new BorderLayout());
        dumpSuggestionsPanel.setOpaque(false);
        JLabel dumpSuggestionsLabel = new JLabel("Dump alerts");
        dumpSuggestionsPanel.add(dumpSuggestionsLabel, BorderLayout.LINE_START);
        dumpSuggestionsPanel.add(dumpSuggestionsToggleButton, BorderLayout.LINE_END);
        dumpSuggestionsToggleButton.addItemListener(e -> {
            boolean enabled = dumpSuggestionsToggleButton.isSelected();
            preferencesManager.setReceiveDumpSuggestions(enabled);
        });
        preferencesContent.add(dumpSuggestionsPanel);
        preferencesContent.add(Box.createRigidArea(new Dimension(0, 3)));

        // Reserved slots
        JPanel reservedSlotsPanel = new JPanel(new BorderLayout());
        reservedSlotsPanel.setOpaque(false);
        JLabel reservedSlotsLabel = new JLabel("Reserved slots");
        reservedSlotsSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 8, 1));
        reservedSlotsSpinner.setPreferredSize(new Dimension(60, 25));
        reservedSlotsSpinner.setMaximumSize(new Dimension(60, 25));
        reservedSlotsSpinner.addChangeListener(e -> {
            int value = (int) reservedSlotsSpinner.getValue();
            preferencesManager.setReservedSlots(value);
            suggestionManager.setSuggestionNeeded(true);
        });
        reservedSlotsPanel.add(reservedSlotsLabel, BorderLayout.LINE_START);
        reservedSlotsPanel.add(reservedSlotsSpinner, BorderLayout.LINE_END);
        preferencesContent.add(reservedSlotsPanel);
        preferencesContent.add(Box.createRigidArea(new Dimension(0, 6)));

        // Premium instances panel - moved to the bottom
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
        preferencesContent.add(premiumInstancesPanel);
        preferencesContent.add(Box.createRigidArea(new Dimension(0, 3)));
    }


    public void refresh() {
        if (!SwingUtilities.isEventDispatchThread()) {
            // we always execute this in the Swing EDT thread
            SwingUtilities.invokeLater(this::refresh);
            return;
        }
        CardLayout layout = (CardLayout) getLayout();
        if (osrsLoginManager.getPlayerDisplayName() == null) {
            layout.show(this, "login");
            return;
        }
        layout.show(this, "preferences");
        sellOnlyModeToggleButton.setSelected(preferencesManager.isSellOnlyMode());
        f2pOnlyModeToggleButton.setSelected(preferencesManager.isF2pOnlyMode());
        int reservedSlots = preferencesManager.getReservedSlots();
        reservedSlotsSpinner.setValue(reservedSlots);
        dumpSuggestionsToggleButton.setSelected(preferencesManager.isReceiveDumpSuggestions());
        deleteProfileButton.setVisible(!preferencesManager.isDefaultProfileSelected());
        List<String> correctOptions = preferencesManager.getAvailableProfiles();
        DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) profileSelector.getModel();
        model.removeAllElements();
        model.addAll(correctOptions);
        model.setSelectedItem(preferencesManager.getCurrentProfile());
    }
}
