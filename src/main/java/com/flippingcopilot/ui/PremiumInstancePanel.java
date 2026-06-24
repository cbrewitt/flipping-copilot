package com.flippingcopilot.ui;

import com.flippingcopilot.controller.ApiRequestHandler;
import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.model.PremiumInstanceStatus;
import com.flippingcopilot.model.SuggestionManager;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.flippingcopilot.ui.UIUtilities.*;

@Slf4j
public class PremiumInstancePanel extends JPanel {

    private final CardLayout cardLayout;
    private final JPanel cardPanel;
    private final List<JComboBox<String>> instanceDropdowns;
    private final FlippingCopilotConfig config;
    private final ApiRequestHandler apiRequestHandler;
    private final SuggestionManager suggestionManager;

    public PremiumInstancePanel(FlippingCopilotConfig config, ApiRequestHandler apiRequestHandler, SuggestionManager suggestionManager) {
        this.config = config;
        this.apiRequestHandler = apiRequestHandler;
        this.suggestionManager = suggestionManager;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARKER_GRAY_COLOR);

        cardLayout = new CardLayout();
        cardPanel = darkPanel(cardLayout, ColorScheme.DARKER_GRAY_COLOR);

        cardPanel.add(createLoadingPanel(), "loading");
        cardPanel.add(darkPanel(new BorderLayout(), ColorScheme.DARKER_GRAY_COLOR), "error");
        cardPanel.add(darkPanel(new BorderLayout(), ColorScheme.DARKER_GRAY_COLOR), "management");

        add(cardPanel, BorderLayout.CENTER);

        instanceDropdowns = new ArrayList<>();
    }

    private JPanel createLoadingPanel() {
        JPanel panel = darkPanel(new GridBagLayout(), ColorScheme.DARKER_GRAY_COLOR);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 10, 0);

        Spinner spinner = new Spinner();
        spinner.show();
        panel.add(spinner, gbc);

        gbc.gridy = 1;
        JLabel loadingLabel = new JLabel("Loading premium account data");
        loadingLabel.setForeground(Color.WHITE);
        panel.add(loadingLabel, gbc);

        return panel;
    }

    public void showLoading() {
        cardLayout.show(cardPanel, "loading");
    }

    public void showError(String errorMessage) {
        JPanel errorPanel = (JPanel) cardPanel.getComponent(1); // error panel
        errorPanel.removeAll();
        errorPanel.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(10, 10, 10, 10);

        JLabel errorLabel = new JLabel("<html><center>" + errorMessage + "</center></html>");
        errorLabel.setForeground(Color.RED);
        errorLabel.setHorizontalAlignment(SwingConstants.CENTER);
        errorPanel.add(errorLabel, gbc);

        cardLayout.show(cardPanel, "error");
    }

    public void showManagementView(PremiumInstanceStatus status) {
        JPanel managementPanel = (JPanel) cardPanel.getComponent(2); // management panel
        managementPanel.removeAll();
        managementPanel.setLayout(new BorderLayout());
        managementPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        managementPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JPanel headerPanel = verticalPanel(ColorScheme.DARKER_GRAY_COLOR);
        JLabel countLabel = new JLabel("You have " + status.getPremiumInstancesCount() + " premium accounts");
        countLabel.setFont(countLabel.getFont().deriveFont(Font.BOLD));
        countLabel.setForeground(Color.WHITE);
        countLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerPanel.add(countLabel);
        addVerticalGap(headerPanel, 15);

        managementPanel.add(headerPanel, BorderLayout.NORTH);
        instanceDropdowns.clear();

        JPanel scrollContent = verticalPanel(ColorScheme.DARKER_GRAY_COLOR);

        for (int i = 0; i < status.getPremiumInstancesCount(); i++) {
            JPanel instancePanel = darkPanel(new FlowLayout(FlowLayout.LEFT), ColorScheme.DARKER_GRAY_COLOR);
            instancePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));

            JLabel instanceLabel = new JLabel("Premium account " + (i + 1) + ":");
            instanceLabel.setForeground(Color.WHITE);
            instanceLabel.setPreferredSize(new Dimension(130, 25));
            instancePanel.add(instanceLabel);

            JComboBox<String> dropdown = new JComboBox<>();
            dropdown.setPreferredSize(new Dimension(200, 25));

            String currentAssignment = null;
            if (i < status.getCurrentlyAssignedDisplayNames().size()) {
                dropdown.addItem("Unassigned");
                currentAssignment = status.getCurrentlyAssignedDisplayNames().get(i);
                dropdown.addItem(currentAssignment);
                dropdown.setSelectedIndex(1);
            } else {
                dropdown.addItem("Unassigned");
            }

            for (String availableName : status.getAvailableDisplayNames()) {
                if (!availableName.equals(currentAssignment)) {
                    dropdown.addItem(availableName);
                }
            }

            instancePanel.add(dropdown);
            instanceDropdowns.add(dropdown);

            scrollContent.add(instancePanel);
            addVerticalGap(scrollContent, 5);
        }

        scrollContent.add(Box.createVerticalGlue());

        JScrollPane scrollPane = new JScrollPane(scrollContent);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scrollPane.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);

        managementPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = darkPanel(new BorderLayout(), ColorScheme.DARKER_GRAY_COLOR);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JLabel changesLabel = new JLabel("Changes remaining (re-charges 1 per day): " + status.getChangesRemaining());
        changesLabel.setForeground(config.lossAmountColor());
        changesLabel.setToolTipText("Remaining updates. This limit recharges by 1 every day up to a max of 12.");
        bottomPanel.add(changesLabel, BorderLayout.WEST);

        JButton updateButton = new JButton("Update");
        updateButton.setEnabled(status.getChangesRemaining() > 0);
        if (status.getChangesRemaining() <= 0) {
            updateButton.setToolTipText("No changes remaining. Wait for daily recharge.");
        }

        updateButton.addActionListener(e -> {
            this.showLoading();
            Consumer<PremiumInstanceStatus> c = (s) -> {
                SwingUtilities.invokeLater(() -> {  // Make sure UI updates happen on EDT
                    if (s.getLoadingError() != null && !s.getLoadingError().isEmpty()) {
                        this.showError(s.getLoadingError());
                    } else {
                        this.showManagementView(s);
                        suggestionManager.setSuggestionNeeded(true);
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
            apiRequestHandler.asyncUpdatePremiumInstances(c, desiredAssignedDisplayNames);
        });
        bottomPanel.add(updateButton, BorderLayout.EAST);

        managementPanel.add(bottomPanel, BorderLayout.SOUTH);

        cardLayout.show(cardPanel, "management");
    }
}
