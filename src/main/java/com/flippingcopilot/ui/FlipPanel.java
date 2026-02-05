package com.flippingcopilot.ui;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.model.FlipV2;
import net.runelite.client.ui.ColorScheme;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;

import static com.flippingcopilot.util.DateUtil.formatEpoch;

public class FlipPanel extends JPanel {

    public FlipPanel(FlipV2 flip, FlippingCopilotConfig config) {
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JLabel itemQuantity = new JLabel(String.format("%d x ", flip.getClosedQuantity()));
        itemQuantity.setForeground(Color.WHITE);

        JLabel itemNameLabel = new JLabel(UIUtilities.truncateString(flip.getCachedItemName(), 19));

        // Create a sub-panel for the left side
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        leftPanel.add(itemQuantity);
        leftPanel.add(itemNameLabel);

        JLabel profitLabel = new JLabel(UIUtilities.formatProfitWithoutGp(flip.getProfit()));
        profitLabel.setForeground(UIUtilities.getProfitColor(flip.getProfit(), config));

        // Add the sub-panel to the LINE_START position
        add(leftPanel, BorderLayout.LINE_START);
        add(profitLabel, BorderLayout.LINE_END);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));

        String closeLabel = flip.getClosedQuantity() == flip.getOpenedQuantity() ? "Close time" : "Partial close time";

        String tooltipText = String.format("Opened time: %s\nAvg buy price: %s\n%s: %s\nAvg sell price: %s\nTax paid: %s\nProfit: %s",
                formatEpoch(flip.getOpenedTime()),
                UIUtilities.formatProfit(flip.getAvgBuyPrice()),
                closeLabel,
                formatEpoch(flip.getClosedTime()),
                UIUtilities.formatProfit(flip.getAvgSellPrice()),
                UIUtilities.formatProfit(flip.getTaxPaid()),
                UIUtilities.formatProfit(flip.getProfit()));
        setToolTipText(tooltipText);
    }
}