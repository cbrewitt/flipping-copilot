package copilot.ui;

import copilot.model.*;
import static javax.swing.BorderFactory.*;
import static net.runelite.client.ui.ColorScheme.*;
import copilot.controller.*;
import copilot.config.*;
import lombok.extern.slf4j.*;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.*;

import static copilot.ui.UIUtilities.*;

@Slf4j
public class PremiumInstancePanel extends JPanel {

    private final CardLayout cardLayout;
    private final JPanel cardPanel;
    private final List<JComboBox<String>> instanceDropdowns;
    private final CopilotConfig config;
    private final ApiClient api;
    private final Suggestions suggestions;

    public PremiumInstancePanel(CopilotConfig config, ApiClient api, Suggestions suggestions) {
        this.config = config; this.api = api; this.suggestions = suggestions;

        setLayout(new BorderLayout());
        setBackground(DARKER_GRAY_COLOR);

        cardLayout = new CardLayout();
        cardPanel = darkPanel(cardLayout, DARKER_GRAY_COLOR);

        // Create loading panel
        cardPanel.add(createLoadingPanel(), "loading");

        // Create error panel container (will be populated when error occurs)
        cardPanel.add(darkPanel(new BorderLayout(), DARKER_GRAY_COLOR), "error");

        // Create management panel container (will be populated when data loads)
        cardPanel.add(darkPanel(new BorderLayout(), DARKER_GRAY_COLOR), "management");

        add(cardPanel, BorderLayout.CENTER);

        instanceDropdowns = new ArrayList<>();
    }

    private JPanel createLoadingPanel() {
        var panel = darkPanel(new GridBagLayout(), DARKER_GRAY_COLOR);

        var gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0; gbc.insets = new Insets(0, 0, 10, 0);

        var spinner = new Spinner();
        spinner.show();
        panel.add(spinner, gbc);

        gbc.gridy = 1;
        var loadingLabel = coloredLabel("Loading premium account data", Color.WHITE);
        panel.add(loadingLabel, gbc);

        return panel;
    }

    public void showLoading() { cardLayout.show(cardPanel, "loading"); }

    public void showError(String errorMessage) {
        JPanel errorPanel = (JPanel) cardPanel.getComponent(1); // error panel
        errorPanel.removeAll();
        errorPanel.setLayout(new GridBagLayout());

        var gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0; gbc.insets = new Insets(10, 10, 10, 10);

        var errorLabel = coloredLabel("<html><center>" + errorMessage + "</center></html>", Color.RED); errorLabel.setHorizontalAlignment(SwingConstants.CENTER);
        errorPanel.add(errorLabel, gbc);

        cardLayout.show(cardPanel, "error");
    }

    public void showManagementView(PremiumInstanceStatus status) {
        JPanel managementPanel = (JPanel) cardPanel.getComponent(2); // management panel
        managementPanel.removeAll();
        managementPanel.setLayout(new BorderLayout()); managementPanel.setBorder(createEmptyBorder(10, 10, 10, 10));
        managementPanel.setBackground(DARKER_GRAY_COLOR);

        // Create header panel for the count label
        var headerPanel = verticalPanel(DARKER_GRAY_COLOR);

        // Add premium instances count
        var countLabel = new JLabel("You have " + status.premiumInstancesCount + " premium accounts");
        countLabel.setFont(countLabel.getFont().deriveFont(Font.BOLD)); countLabel.setForeground(Color.WHITE);
        countLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerPanel.add(countLabel);
        addVerticalGap(headerPanel, 15);

        // Add header to the top of the management panel
        managementPanel.add(headerPanel, BorderLayout.NORTH);

        // Clear existing dropdowns
        instanceDropdowns.clear();

        // Create a panel for the scrollable content
        var scrollContent = verticalPanel(DARKER_GRAY_COLOR);

        // Add dropdowns for each instance
        for (int i = 0; i < status.premiumInstancesCount; i++) {
            var instancePanel = darkPanel(new FlowLayout(FlowLayout.LEFT), DARKER_GRAY_COLOR);
            instancePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));

            var instanceLabel = coloredLabel("Premium account " + (i + 1) + ":", Color.WHITE); instanceLabel.setPreferredSize(new Dimension(130, 25));
            instancePanel.add(instanceLabel);

            JComboBox<String> dropdown = new JComboBox<>();
            dropdown.setPreferredSize(new Dimension(200, 25));

            String currentAssignment = null;
            if (i < status.currentlyAssignedDisplayNames.size()) {
                // Add current assignment if exists
                dropdown.addItem("Unassigned");
                currentAssignment = status.currentlyAssignedDisplayNames.get(i);
                dropdown.addItem(currentAssignment);
                dropdown.setSelectedIndex(1);
            } else {
                dropdown.addItem("Unassigned");
            }

            // Add available names
            for (String availableName : status.availableDisplayNames) {
                if (!availableName.equals(currentAssignment)) { dropdown.addItem(availableName); }
            }

            instancePanel.add(dropdown);
            instanceDropdowns.add(dropdown);

            scrollContent.add(instancePanel);
            addVerticalGap(scrollContent, 5);
        }

        // Add vertical glue to push everything to the top
        scrollContent.add(Box.createVerticalGlue());

        // Create a scroll pane for the content
        var scrollPane = new JScrollPane(scrollContent);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBackground(DARKER_GRAY_COLOR);
        scrollPane.getViewport().setBackground(DARKER_GRAY_COLOR);

        // Add the scroll pane to the center of the management panel
        managementPanel.add(scrollPane, BorderLayout.CENTER);

        // Add bottom panel with changes remaining and update button
        var bottomPanel = darkPanel(new BorderLayout(), DARKER_GRAY_COLOR);
        bottomPanel.setBorder(createEmptyBorder(10, 0, 0, 0));

        var changesLabel = coloredLabel("Changes remaining (re-charges 1 per day): " + status.changesRemaining, config.lossAmountColor());
        changesLabel.setToolTipText("Remaining updates. This limit recharges by 1 every day up to a max of 12.");
        bottomPanel.add(changesLabel, BorderLayout.WEST);

        var updateButton = new JButton("Update");

        // Disable the update button if no changes remaining
        updateButton.setEnabled(status.changesRemaining > 0);
        if (status.changesRemaining <= 0) {
            // Add tooltip to explain why button is disabled when changes = 0
            updateButton.setToolTipText("No changes remaining. Wait for daily recharge.");
        }

        updateButton.addActionListener(e -> {
            this.showLoading();
            Consumer<PremiumInstanceStatus> c = (s) -> {
                SwingUtilities.invokeLater(() -> {  // Make sure UI updates happen on EDT
                    if (s.loadingError != null && !s.loadingError.isEmpty()) { this.showError(s.loadingError); } else {
                        this.showManagementView(s);
                        suggestions.setSuggestionNeeded(true);
                    }
                });
            };
            List<String> desiredAssignedDisplayNames = new ArrayList<>();
            for (JComboBox<String> dropdown : instanceDropdowns) {
                String selectedName = (String) dropdown.getSelectedItem();
                if (selectedName != null && !selectedName.equals("Unassigned") && !desiredAssignedDisplayNames.contains(selectedName)) {
                    desiredAssignedDisplayNames.add(selectedName);
                }
            }
            api.asyncUpdatePremiumInstances(c, desiredAssignedDisplayNames);
        });
        bottomPanel.add(updateButton, BorderLayout.EAST);

        // Add the bottom panel to the south of the management panel
        managementPanel.add(bottomPanel, BorderLayout.SOUTH);

        cardLayout.show(cardPanel, "management");
    }
}
