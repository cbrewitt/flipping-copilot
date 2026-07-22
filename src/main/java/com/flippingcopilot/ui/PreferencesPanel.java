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
import java.util.Objects;

import static com.flippingcopilot.ui.UIUtilities.*;

@Slf4j
@Singleton
public class PreferencesPanel extends JPanel {
    private static final Option[] MIN_PREDICTED_PROFIT_OPTIONS = new Option[]{
            new Option("Auto", null),
            new Option("20K", 20_000L),
            new Option("50K", 50_000L),
            new Option("100K", 100_000L),
            new Option("200K", 200_000L),
            new Option("500K", 500_000L),
            new Option("1M", 1_000_000L)
    };

    private static final Option[] RESERVED_SLOTS_OPTIONS = new Option[]{
            new Option("Auto", null),
            new Option("0", 0),
            new Option("1", 1),
            new Option("2", 2),
            new Option("3", 3),
            new Option("4", 4),
            new Option("5", 5),
            new Option("6", 6),
            new Option("7", 7),
            new Option("8", 8)
    };

    private static final Option[] DUMP_ALERT_MIN_PROFIT_OPTIONS = new Option[]{
            new Option("Off", null),
            new Option("100K+", 100_000L),
            new Option("200K+", 200_000L),
            new Option("500K+", 500_000L),
            new Option("1M+", 1_000_000L),
            new Option("2M+", 2_000_000L),
            new Option("5M+", 5_000_000L)
    };

    private final SuggestionPreferencesManager preferencesManager;
    private final OsrsLoginManager osrsLoginManager;
    private final JPanel sellOnlyButton;
    private final PreferencesToggleButton sellOnlyModeToggleButton;
    private final JPanel buyAndHoldButton;
    private final PreferencesToggleButton buyAndHoldToggleButton;
    private final JPanel f2pOnlyButton;
    private final PreferencesToggleButton f2pOnlyModeToggleButton;
    private final ItemSearchMultiSelect blocklistDropdownPanel;
    private final JComboBox<String> profileSelector;
    private final JButton addProfileButton;
    private final JButton deleteProfileButton;
    private final JComboBox<Option> reservedSlotsDropdown;
    private final JComboBox<Option> dumpAlertsDropdown;
    private final JPanel preferencesContent;
    private final JPanel loginPromptPanel;
    private final JComboBox<Option> minPredictedProfitDropdown;
    private boolean suppressMinProfitEvents;
    private boolean suppressReservedSlotsEvents;
    private boolean suppressDumpAlertsEvents;

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

        preferencesContent = verticalPanel(ColorScheme.DARKER_GRAY_COLOR);

        JLabel preferencesTitle = new JLabel("Suggestion Settings");
        preferencesTitle.setForeground(Color.WHITE);
        preferencesTitle.setFont(preferencesTitle.getFont().deriveFont(Font.BOLD));
        preferencesTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        setFixedSize(preferencesTitle, MainPanel.CONTENT_WIDTH - 30, preferencesTitle.getPreferredSize().height);
        preferencesTitle.setHorizontalAlignment(SwingConstants.CENTER);
        preferencesContent.add(preferencesTitle);
        addVerticalGap(preferencesContent, 8);

        loginPromptPanel = messagePanel(
                "<html><center>Log in to the game<br>to alter suggestion settings.</center></html>",
                ColorScheme.DARKER_GRAY_COLOR,
                ColorScheme.LIGHT_GRAY_COLOR);

        add(preferencesContent, "preferences");
        add(loginPromptPanel, "login");

        // Profile selector panel
        JPanel profilePanel = transparentPanel(new BorderLayout());
        profilePanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));

        // Panel for dropdown and buttons
        JPanel profileControlPanel = transparentXAxisPanel();

        // Initialize profile model with default
        profileSelector = new JComboBox<>();
        setFixedSize(profileSelector, 160, 25);
        profileSelector.addActionListener(e -> {
            String selectedProfile = (String) profileSelector.getSelectedItem();
            if (selectedProfile != null && !selectedProfile.equals(preferencesManager.getCurrentProfile())) {
                preferencesManager.setCurrentProfile(selectedProfile);
                refresh();
            }
        });

        // Add button for creating new profiles
        addProfileButton = new JButton("+");
        setFixedSize(addProfileButton, 15, 25);
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
        setFixedSize(deleteProfileButton, 15, 25);
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
        addHorizontalGap(profileControlPanel, 5);
        profileControlPanel.add(addProfileButton);
        addHorizontalGap(profileControlPanel, 2);
        profileControlPanel.add(deleteProfileButton);

        profilePanel.add(profileControlPanel, BorderLayout.LINE_START);
        preferencesContent.add(profilePanel);

        // Blocklist dropdown panel
        blocklistDropdownPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(5, 0, 5, 0),
                blocklistDropdownPanel.getBorder()));
        preferencesContent.add(blocklistDropdownPanel);

        // Buy and hold toggle
        buyAndHoldToggleButton = new PreferencesToggleButton("Disable holds", "Enable holds");
        buyAndHoldButton = formRow("Enable holds", buyAndHoldToggleButton);
        preferencesContent.add(buyAndHoldButton);
        buyAndHoldToggleButton.addItemListener(i -> {
            preferencesManager.setBuyAndHold(buyAndHoldToggleButton.isSelected());
            suggestionManager.setSuggestionNeeded(true);
        });
        addVerticalGap(preferencesContent, 3);

        // Sell-only mode toggle
        sellOnlyModeToggleButton = new PreferencesToggleButton("Disable sell-only mode", "Enable sell-only mode");
        sellOnlyButton = formRow("Sell-only mode", sellOnlyModeToggleButton);
        preferencesContent.add(sellOnlyButton);
        sellOnlyModeToggleButton.addItemListener(i -> {
            preferencesManager.setSellOnlyMode(sellOnlyModeToggleButton.isSelected());
            suggestionManager.setSuggestionNeeded(true);
        });
        addVerticalGap(preferencesContent, 3);

        // F2P-only mode toggle
        f2pOnlyModeToggleButton = new PreferencesToggleButton("Disable F2P-only mode",  "Enable F2P-only mode");
        f2pOnlyButton = formRow("F2P-only mode", f2pOnlyModeToggleButton);
        preferencesContent.add(f2pOnlyButton);
        f2pOnlyModeToggleButton.addItemListener(i -> {
            preferencesManager.setF2pOnlyMode(f2pOnlyModeToggleButton.isSelected());
            suggestionManager.setSuggestionNeeded(true);
        });

        // Min predicted profit
        minPredictedProfitDropdown = new JComboBox<>(new DefaultComboBoxModel<>(MIN_PREDICTED_PROFIT_OPTIONS));
        setFixedSize(minPredictedProfitDropdown, 75, 25);
        minPredictedProfitDropdown.addActionListener(e -> {
            if (suppressMinProfitEvents) {
                return;
            }
            Option option = (Option) minPredictedProfitDropdown.getSelectedItem();
            preferencesManager.setMinPredictedProfit(option == null || option.value == null ? null : option.value.longValue());
            suggestionManager.setSuggestionNeeded(true);
        });
        preferencesContent.add(formRow("Min. predicted profit", minPredictedProfitDropdown));
        addVerticalGap(preferencesContent, 3);

        // Dump alerts dropdown
        dumpAlertsDropdown = new JComboBox<>(new DefaultComboBoxModel<>(DUMP_ALERT_MIN_PROFIT_OPTIONS));
        setFixedSize(dumpAlertsDropdown, 75, 25);
        dumpAlertsDropdown.addActionListener(e -> {
            if (suppressDumpAlertsEvents) {
                return;
            }
            Option option = (Option) dumpAlertsDropdown.getSelectedItem();
            if (option == null || option.value == null) {
                preferencesManager.setReceiveDumpSuggestions(false);
                preferencesManager.setDumpMinPredictedProfit(null);
            } else {
                preferencesManager.setReceiveDumpSuggestions(true);
                preferencesManager.setDumpMinPredictedProfit(option.value.longValue());
            }
            suggestionManager.setSuggestionNeeded(true);
        });
        preferencesContent.add(formRow("Dump alerts", dumpAlertsDropdown));
        addVerticalGap(preferencesContent, 6);

        // Reserved slots
        reservedSlotsDropdown = new JComboBox<>(new DefaultComboBoxModel<>(RESERVED_SLOTS_OPTIONS));
        setFixedSize(reservedSlotsDropdown, 75, 25);
        reservedSlotsDropdown.addActionListener(e -> {
            if (suppressReservedSlotsEvents) {
                return;
            }
            Option option = (Option) reservedSlotsDropdown.getSelectedItem();
            preferencesManager.setReservedSlots(option == null || option.value == null ? null : option.value.intValue());
            suggestionManager.setSuggestionNeeded(true);
        });
        preferencesContent.add(formRow("Reserved slots", reservedSlotsDropdown));
        addVerticalGap(preferencesContent, 6);

        // Premium instances panel - moved to the bottom
        JButton manageButton = new JButton("manage");
        manageButton.addActionListener(e -> {
            premiumInstanceController.loadAndOpenPremiumInstanceDialog();
        });
        preferencesContent.add(formRow("Premium accounts:", manageButton));
        addVerticalGap(preferencesContent, 3);
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
        buyAndHoldToggleButton.setSelected(preferencesManager.isBuyAndHold());
        f2pOnlyModeToggleButton.setSelected(preferencesManager.isF2pOnlyMode());
        syncReservedSlots(preferencesManager.getReservedSlots());
        syncDumpAlerts(preferencesManager.isReceiveDumpSuggestions(), preferencesManager.getDumpMinPredictedProfit());
        syncMinPredictedProfit(preferencesManager.getMinPredictedProfit());
        deleteProfileButton.setVisible(!preferencesManager.isDefaultProfileSelected());
        List<String> correctOptions = preferencesManager.getAvailableProfiles();
        DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) profileSelector.getModel();
        model.removeAllElements();
        model.addAll(correctOptions);
        model.setSelectedItem(preferencesManager.getCurrentProfile());
    }

    private void syncMinPredictedProfit(Long value) {
        try {
            suppressMinProfitEvents = true;
            minPredictedProfitDropdown.setSelectedItem(findMinProfitOption(value));
        } finally {
            suppressMinProfitEvents = false;
        }
    }

    private void syncDumpAlerts(boolean enabled, Long minProfit) {
        try {
            suppressDumpAlertsEvents = true;
            dumpAlertsDropdown.setSelectedItem(findDumpAlertOption(enabled, minProfit));
        } finally {
            suppressDumpAlertsEvents = false;
        }
    }

    private void syncReservedSlots(Integer value) {
        try {
            suppressReservedSlotsEvents = true;
            reservedSlotsDropdown.setSelectedItem(findReservedSlotsOption(value));
        } finally {
            suppressReservedSlotsEvents = false;
        }
    }

    private Option findMinProfitOption(Long value) {
        for (int i = 0; i < minPredictedProfitDropdown.getItemCount(); i++) {
            Option option = minPredictedProfitDropdown.getItemAt(i);
            if (Objects.equals(option.value, value)) {
                return option;
            }
        }
        return minPredictedProfitDropdown.getItemAt(0);
    }

    private Option findDumpAlertOption(boolean enabled, Long minProfit) {
        if (!enabled) {
            return dumpAlertsDropdown.getItemAt(0);
        }
        Long effective = minProfit != null ? minProfit : 100_000L;
        for (int i = 0; i < dumpAlertsDropdown.getItemCount(); i++) {
            Option option = dumpAlertsDropdown.getItemAt(i);
            if (Objects.equals(option.value, effective)) {
                return option;
            }
        }
        return dumpAlertsDropdown.getItemAt(1);
    }

    private Option findReservedSlotsOption(Integer value) {
        for (int i = 0; i < reservedSlotsDropdown.getItemCount(); i++) {
            Option option = reservedSlotsDropdown.getItemAt(i);
            if (Objects.equals(option.value, value)) {
                return option;
            }
        }
        return reservedSlotsDropdown.getItemAt(0);
    }

    private static final class Option {
        private final String label;
        private final Number value;

        private Option(String label, Number value) {
            this.label = label;
            this.value = value;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
