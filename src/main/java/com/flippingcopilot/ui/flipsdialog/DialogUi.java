package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.ui.Spinner;
import net.runelite.client.ui.ColorScheme;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

final class DialogUi {
    private DialogUi() {
    }

    static JPanel loginPrompt(String message, Color background, boolean opaque) {
        return centeredMessage(message, background, opaque, 18f);
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
}
