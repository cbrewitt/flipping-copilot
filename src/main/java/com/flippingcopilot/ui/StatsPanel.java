package com.flippingcopilot.ui;

import com.flippingcopilot.controller.FlipTracker;
import com.flippingcopilot.controller.FlippingCopilotConfig;
import com.flippingcopilot.model.Flip;
import net.runelite.api.Client;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

import static com.flippingcopilot.ui.UIUtilities.getProfitColor;


public class StatsPanel extends JPanel {
    private final JLabel profitText = new JLabel();
    private final JPanel flipLogPanel = new JPanel();
    private final FlippingCopilotConfig config;

    public StatsPanel(FlippingCopilotConfig config) {
        this.config = config;
        setLayout(new BorderLayout(3, 0));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setupTotalProfit();
        setupFlipLog();
    }

    void setupTotalProfit() {
        JLabel profitTitle = new JLabel("<html><b>Session Profit:<b></html>");
        profitTitle.setHorizontalAlignment(SwingConstants.CENTER);
        profitTitle.setForeground(Color.WHITE);

        profitText.setHorizontalAlignment(SwingConstants.CENTER);
        profitText.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        profitText.setFont(FontManager.getRunescapeBoldFont());

        add(profitTitle, BorderLayout.NORTH);
        add(profitText, BorderLayout.CENTER);
        updateProfitText(0);
    }

    public void updateFlips(FlipTracker flipTracker, Client client) {
        updateProfitText(flipTracker.getProfit());
        refreshFlipLog(flipTracker.getFlips(), client);
    }

    private void updateProfitText(long profit) {
        profitText.setText(((profit >= 0) ? "" : "-")
                + UIUtilities.quantityToRSDecimalStack(Math.abs(profit), true) + " gp");
        profitText.setToolTipText("Total Profit: " + QuantityFormatter.formatNumber(profit) + " gp");
        profitText.setForeground(getProfitColor(profit, config));
    }

    private void setupFlipLog() {
        flipLogPanel.setLayout(new BoxLayout(flipLogPanel, BoxLayout.Y_AXIS));
        flipLogPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        add(flipLogPanel, BorderLayout.SOUTH);

    }

    private void refreshFlipLog(Collection<Flip> flips, Client client) {
        flipLogPanel.removeAll();
        for (Flip flip : flips) {
            String itemName = client.getItemDefinition(flip.getItemId()).getName();
            FlipPanel flipPanel = new FlipPanel(itemName, flip.getProfit(), config);
            flipLogPanel.add(flipPanel);
        }
        flipLogPanel.revalidate();
        flipLogPanel.repaint();
    }


}
