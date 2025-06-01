package com.flippingcopilot.ui.flipsdialog;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import java.awt.*;

@Slf4j
public class ProfitPanel extends JPanel {

    public ProfitPanel() {
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // Placeholder panel for now - will be replaced with graph implementation
        JPanel placeholderPanel = new JPanel(new GridBagLayout());
        placeholderPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JLabel placeholderLabel = new JLabel("Profit graph coming soon...");
        placeholderLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        placeholderLabel.setFont(placeholderLabel.getFont().deriveFont(Font.BOLD, 16f));

        placeholderPanel.add(placeholderLabel);

        add(placeholderPanel, BorderLayout.CENTER);

        // TODO: Implement profit graph
        // This will likely use a similar approach to the GraphPanel in PriceGraphController
        // showing profit over time with various time range options
    }
}