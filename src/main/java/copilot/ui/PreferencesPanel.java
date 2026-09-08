package copilot.ui;
import static javax.swing.JOptionPane.*;
import static javax.swing.SwingUtilities.*;

import copilot.model.*;
import static javax.swing.BorderFactory.*;
import static net.runelite.client.ui.ColorScheme.*;
import copilot.controller.*;
import copilot.rs.*;
import copilot.ui.components.*;
import lombok.extern.slf4j.*;

import javax.inject.*;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.*;

import static copilot.ui.UIUtilities.*;
import java.util.function.*;

@Slf4j
@Singleton
public class PreferencesPanel extends JPanel {
    private static final Option[] MIN_PREDICTED_PROFIT_OPTIONS = options("Auto", "",
            20_000L, 50_000L, 100_000L, 200_000L, 500_000L, 1_000_000L);
    private static final Option[] RESERVED_SLOTS_OPTIONS = options("Auto", "", 0, 1, 2, 3, 4, 5, 6, 7, 8);
    private static final Option[] DUMP_ALERT_MIN_PROFIT_OPTIONS = options("Off", "+",
            100_000L, 200_000L, 500_000L, 1_000_000L, 2_000_000L, 5_000_000L);

    private static Option[] options(String disabledLabel, String suffix, Number... values) {
        var options = new Option[values.length + 1];
        options[0] = new Option(disabledLabel, null);
        for (int i = 0; i < values.length; i++) {
            Number value = values[i];
            String label = value.longValue() < 1000 ? value.toString() : quantityToRSDecimalStack(value.longValue(), false);
            options[i + 1] = new Option(label + suffix, value);
        }
        return options;
    }

    private final Preferences preferencesManager;
    private final AccountPreferencesRS accountPreferences;
    private final PreferencesToggleButton sellOnlyModeToggleButton, buyAndHoldToggleButton;
    private final PreferencesToggleButton f2pOnlyModeToggleButton;
    private final ItemSearchMultiSelect blocklistDropdownPanel;
    private final JComboBox<String> profileSelector;
    private final JButton addProfileButton, deleteProfileButton;
    private final OptionSelector reservedSlotsDropdown, dumpAlertsDropdown;
    private final JPanel preferencesContent, loginPromptPanel;
    private final OptionSelector minPredictedProfitDropdown;

    @Inject
    public PreferencesPanel(
            Suggestions suggestionManager,
            Preferences preferencesManager,
            PremiumInstanceController premiumInstanceController,
            Items itemController,
            AccountPreferencesRS accountPreferences,
            DumpStream dumpsStreamController) {
        super();
        this.preferencesManager = preferencesManager; this.accountPreferences = accountPreferences;

        blocklistDropdownPanel = new ItemSearchMultiSelect(
                () -> new HashSet<>(preferencesManager.blockedItems()),
                itemController::allItemIds,
                itemController::search,
                (bl) -> {
                    preferencesManager.setBlockedItems(bl);
                    suggestionManager.setSuggestionNeeded(true);
                },
                "Item blocklist...",
                getWindowAncestor(this));

        setLayout(new CardLayout());
        setBackground(DARKER_GRAY_COLOR);
        setBorder(createEmptyBorder(10, 15, 10, 15));

        preferencesContent = verticalPanel(DARKER_GRAY_COLOR);

        var preferencesTitle = coloredLabel("Suggestion Settings", Color.WHITE);
        preferencesTitle.setFont(preferencesTitle.getFont().deriveFont(Font.BOLD));
        preferencesTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        setFixedSize(preferencesTitle, MainPanel.CONTENT_WIDTH - 30, preferencesTitle.getPreferredSize().height);
        preferencesTitle.setHorizontalAlignment(SwingConstants.CENTER);
        preferencesContent.add(preferencesTitle);
        addVerticalGap(preferencesContent, 8);

        loginPromptPanel = darkPanel(new GridBagLayout(), DARKER_GRAY_COLOR);
        var loginPromptLabel = coloredLabel("<html><center>Log in to the game<br>to alter suggestion settings.</center></html>", LIGHT_GRAY_COLOR);
        loginPromptPanel.add(loginPromptLabel);

        add(preferencesContent, "preferences");
        add(loginPromptPanel, "login");

        // Profile selector panel
        var profilePanel = transparentPanel(new BorderLayout());
        profilePanel.setBorder(createEmptyBorder(0, 0, 5, 0));

        // Panel for dropdown and buttons
        var profileControlPanel = new JPanel();
        profileControlPanel.setLayout(new BoxLayout(profileControlPanel, BoxLayout.X_AXIS));
        profileControlPanel.setOpaque(false);

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
            String newProfileName = showInputDialog(
                    getWindowAncestor(this),
                    "Enter new profile name (must be valid file name):",
                    "New preferences profile",
                    PLAIN_MESSAGE);
            if (newProfileName != null && !newProfileName.trim().isEmpty()) {
                newProfileName = newProfileName.trim();
                try {
                    preferencesManager.addProfile(newProfileName);
                    refresh();
                } catch (IOException ex) {
                    log.error("adding new profile: {}", newProfileName, ex);
                    showMessageDialog(
                            getWindowAncestor(this),
                            "Error adding new profile: "+ ex.getMessage(),
                            "Add profile failed",
                            WARNING_MESSAGE);
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
                int result = showConfirmDialog(
                        getWindowAncestor(this),
                        "Delete profile '" + selectedProfile + "'?",
                        "Delete Profile",
                        YES_NO_OPTION,
                        QUESTION_MESSAGE);
                if (result == YES_OPTION) {
                    ((DefaultComboBoxModel<String>) profileSelector.getModel()).removeElement(selectedProfile);
                    try {
                        preferencesManager.deleteSelectedProfile();
                        profileSelector.setSelectedItem(preferencesManager.getCurrentProfile());
                    } catch (IOException ex) {
                        log.error("removing profile: {}", selectedProfile, ex);
                        showMessageDialog(
                                getWindowAncestor(this),
                                "Error deleting profile: "+ ex.getMessage(),
                                "Remove profile failed",
                                WARNING_MESSAGE);
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
        blocklistDropdownPanel.setBorder(createCompoundBorder(
                createEmptyBorder(5, 0, 5, 0),
                blocklistDropdownPanel.getBorder()));
        preferencesContent.add(blocklistDropdownPanel);

        buyAndHoldToggleButton = addToggle("Enable holds", "Disable holds", "Enable holds",
                preferencesManager::setBuyAndHold, suggestionManager, 3);
        sellOnlyModeToggleButton = addToggle("Sell-only mode", "Disable sell-only mode", "Enable sell-only mode",
                preferencesManager::setSellOnlyMode, suggestionManager, 3);
        f2pOnlyModeToggleButton = addToggle("F2P-only mode", "Disable F2P-only mode", "Enable F2P-only mode",
                preferencesManager::setF2pOnlyMode, suggestionManager, 0);

        minPredictedProfitDropdown = addSelector("Min. predicted profit", MIN_PREDICTED_PROFIT_OPTIONS, value -> {
            preferencesManager.setMinPredictedProfit(value == null ? null : value.longValue());
            suggestionManager.setSuggestionNeeded(true);
        }, 3);
        dumpAlertsDropdown = addSelector("Dump alerts", DUMP_ALERT_MIN_PROFIT_OPTIONS, value -> {
            preferencesManager.setReceiveDumpSuggestions(value != null);
            preferencesManager.setDumpMinPredictedProfit(value == null ? null : value.longValue());
            suggestionManager.setSuggestionNeeded(true);
        }, 6);
        reservedSlotsDropdown = addSelector("Reserved slots", RESERVED_SLOTS_OPTIONS, value -> {
            preferencesManager.setReservedSlots(value == null ? null : value.intValue());
            suggestionManager.setSuggestionNeeded(true);
        }, 6);

        // Premium instances panel - moved to the bottom
        var manageButton = new JButton("manage");
        manageButton.addActionListener(e -> {
            premiumInstanceController.loadAndOpenPremiumInstanceDialog();
        });
        preferencesContent.add(formRow("Premium accounts:", manageButton));
        addVerticalGap(preferencesContent, 3);
    }

    public void refresh() {
        if (!ensureEdt(this::refresh)) return;
        CardLayout layout = (CardLayout) getLayout();
        if (!accountPreferences.hasAccount()) {
            layout.show(this, "login");
            return;
        }
        layout.show(this, "preferences");
        sellOnlyModeToggleButton.setSelected(preferencesManager.isSellOnlyMode());
        buyAndHoldToggleButton.setSelected(preferencesManager.isBuyAndHold());
        f2pOnlyModeToggleButton.setSelected(preferencesManager.isF2pOnlyMode());
        reservedSlotsDropdown.selectValue(preferencesManager.getReservedSlots(), 0);
        syncDumpAlerts(preferencesManager.isReceiveDumpSuggestions(), preferencesManager.getDumpMinPredictedProfit());
        minPredictedProfitDropdown.selectValue(preferencesManager.getMinPredictedProfit(), 0);
        deleteProfileButton.setVisible(!preferencesManager.isDefaultProfileSelected());
        var correctOptions = preferencesManager.getAvailableProfiles();
        DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) profileSelector.getModel();
        model.removeAllElements();
        model.addAll(correctOptions);
        model.setSelectedItem(preferencesManager.getCurrentProfile());
    }

    private PreferencesToggleButton addToggle(String label, String disableText, String enableText,
                                               Consumer<Boolean> setter, Suggestions suggestions, int gap) {
        var button = new PreferencesToggleButton(disableText, enableText);
        preferencesContent.add(formRow(label, button));
        button.addItemListener(event -> {
            setter.accept(button.isSelected());
            suggestions.setSuggestionNeeded(true);
        });
        if (gap > 0) { addVerticalGap(preferencesContent, gap); }
        return button;
    }

    private OptionSelector addSelector(String label, Option[] options, Consumer<Number> setter, int gap) {
        var selector = new OptionSelector(options, setter);
        preferencesContent.add(formRow(label, selector));
        addVerticalGap(preferencesContent, gap);
        return selector;
    }

    private void syncDumpAlerts(boolean enabled, Long minProfit) {
        dumpAlertsDropdown.selectValue(enabled ? (minProfit != null ? minProfit : 100_000L) : null, enabled ? 1 : 0);
    }

    // Programmatic refreshes must not write preferences or request new suggestions.
    static final class OptionSelector extends JComboBox<Option> {
        private boolean syncing;

        OptionSelector(Option[] options, Consumer<Number> setter) {
            super(new DefaultComboBoxModel<>(options));
            setFixedSize(this, 75, 25);
            addActionListener(event -> {
                if (syncing) { return; }
                Option selected = (Option) getSelectedItem();
                setter.accept(selected == null ? null : selected.value);
            });
        }

        void selectValue(Number value, int fallback) {
            Option selected = getItemAt(fallback);
            for (int i = 0; i < getItemCount(); i++) {
                if (Objects.equals(getItemAt(i).value, value)) {
                    selected = getItemAt(i);
                    break;
                }
            }
            try {
                syncing = true;
                setSelectedItem(selected);
            } finally {
                syncing = false;
            }
        }
    }

    @lombok.RequiredArgsConstructor(access = lombok.AccessLevel.PACKAGE)
    static final class Option {
        private final String label;
        private final Number value;


        @Override
        public String toString() { return label; }
    }
}
