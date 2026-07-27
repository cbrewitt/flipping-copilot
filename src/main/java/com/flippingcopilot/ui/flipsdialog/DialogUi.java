package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.model.IntervalTimeUnit;
import com.flippingcopilot.ui.Spinner;
import com.flippingcopilot.ui.components.AccountDropdown;
import com.flippingcopilot.ui.components.IntervalDropdown;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.function.*;

final class DialogUi {
    private DialogUi() {
    }

    static JPanel centeredMessage(String message, Color background, boolean opaque, float fontSize) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(opaque);
        if (background != null) {
            panel.setBackground(background);
        }
        JLabel label = new JLabel(message);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setFont(label.getFont().deriveFont(fontSize));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setMinimumSize(label.getPreferredSize());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(label, gbc);
        return panel;
    }

    static JPanel loadingCard(String message, Color background) {
        JLabel loadingLabel = new JLabel(message);
        Spinner spinner = new Spinner();
        JPanel loadingPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        loadingLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        loadingLabel.setFont(loadingLabel.getFont().deriveFont(14f));
        loadingPanel.setBackground(background);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(10, 10, 10, 10);
        spinner.show();
        loadingPanel.add(spinner, gbc);
        gbc.gridy = 1;
        loadingPanel.add(loadingLabel, gbc);
        return loadingPanel;
    }

    static IntervalDropdown intervalDropdown(BiConsumer<IntervalTimeUnit, Integer> onIntervalChanged) {
        IntervalDropdown dropdown = new IntervalDropdown(onIntervalChanged, IntervalDropdown.ALL_TIME, false);
        dropdown.setPreferredSize(new Dimension(150, dropdown.getPreferredSize().height));
        dropdown.setToolTipText("Select time interval");
        return dropdown;
    }

    static AccountDropdown accountDropdown(Supplier<Map<String, Integer>> accountsGetter, Consumer<Integer> onAccountChanged) {
        AccountDropdown dropdown = new AccountDropdown(accountsGetter, onAccountChanged, AccountDropdown.ALL_ACCOUNTS_DROPDOWN_OPTION);
        dropdown.setPreferredSize(new Dimension(120, dropdown.getPreferredSize().height));
        dropdown.setToolTipText("Select account");
        return dropdown;
    }

    static JPanel errorCard(JLabel errorLabel, Runnable onRetry) {
        errorLabel.setForeground(Color.RED);
        errorLabel.setFont(errorLabel.getFont().deriveFont(14f));
        errorLabel.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel errorPanel = new JPanel(new GridBagLayout());
        errorPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(10, 10, 10, 10);
        errorPanel.add(errorLabel, gbc);
        gbc.gridy = 1;
        gbc.insets = new Insets(20, 10, 10, 10);
        JButton retryButton = new JButton("Retry");
        retryButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        retryButton.setFocusable(false);
        retryButton.addActionListener(e -> onRetry.run());
        errorPanel.add(retryButton, gbc);
        return errorPanel;
    }

    static JSplitPane splitGraphCard(Component graph, Component stats) {
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
        splitPane.setLeftComponent(graph);
        splitPane.setRightComponent(stats);
        splitPane.setResizeWeight(0.95);
        splitPane.setDividerLocation(0.95);
        splitPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        return splitPane;
    }
}
