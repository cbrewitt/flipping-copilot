package com.flippingcopilot.ui;

import com.flippingcopilot.controller.FlippingCopilotConfig;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import java.awt.*;

public class FlipPanel extends JPanel {

    public FlipPanel(String itemName, long profit, FlippingCopilotConfig config) {
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        JLabel itemNameLabel = new JLabel(UIUtilities.truncateString(itemName, 22));
        JLabel profitLabel = new JLabel(UIUtilities.formatProfit(profit));
        profitLabel.setForeground(UIUtilities.getProfitColor(profit, config));

        add(itemNameLabel, BorderLayout.LINE_START);
        add(profitLabel, BorderLayout.LINE_END);
    }
}