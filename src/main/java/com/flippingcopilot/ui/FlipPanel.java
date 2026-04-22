package com.flippingcopilot.ui;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.model.FlipV2;
import net.runelite.client.ui.ColorScheme;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static com.flippingcopilot.util.DateUtil.formatEpoch;

public class FlipPanel extends JPanel {
    private static final Color HOVER_BACKGROUND = ColorScheme.DARKER_GRAY_COLOR.brighter();

    public FlipPanel(FlipV2 flip, FlippingCopilotConfig config, Runnable onClick) {
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
        long closedCostBasis = flip.getOpenedQuantity() <= 0
                ? 0
                : (flip.getSpent() * flip.getClosedQuantity()) / flip.getOpenedQuantity();
        String roiText = closedCostBasis > 0
                ? String.format("%.2f%%", ((double) flip.getProfit() / (double) closedCostBasis) * 100.0d)
                : "Unknown";
        Color profitColor = UIUtilities.getProfitColor(flip.getProfit(), config);
        String profitColorHex = String.format("#%06X", (0xFFFFFF & profitColor.getRGB()));

        String tooltipText = String.format("<html>Profit: <font color='%s'>%s</font><br>ROI: <font color='%s'>%s</font><br>Avg buy price: %s<br>Avg sell price: %s<br>Tax paid: %s<br>Opened time: %s<br>%s: %s</html>",
                profitColorHex,
                UIUtilities.formatProfit(flip.getProfit()),
                profitColorHex,
                roiText,
                UIUtilities.formatProfit(flip.getAvgBuyPrice()),
                UIUtilities.formatProfit(flip.getAvgSellPrice()),
                UIUtilities.formatProfit(flip.getTaxPaid()),
                formatEpoch(flip.getOpenedTime()),
                closeLabel,
                formatEpoch(flip.getClosedTime()));
        setToolTipText(tooltipText);
        leftPanel.setToolTipText(tooltipText);
        itemQuantity.setToolTipText(tooltipText);
        itemNameLabel.setToolTipText(tooltipText);
        profitLabel.setToolTipText(tooltipText);

        if (onClick != null) {
            Component[] clickableComponents = {this, leftPanel, itemQuantity, itemNameLabel, profitLabel};
            MouseAdapter clickListener = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    onClick.run();
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    setBackground(HOVER_BACKGROUND);
                    leftPanel.setBackground(HOVER_BACKGROUND);
                    for (Component component : clickableComponents) {
                        component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    setBackground(ColorScheme.DARKER_GRAY_COLOR);
                    leftPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                    for (Component component : clickableComponents) {
                        component.setCursor(Cursor.getDefaultCursor());
                    }
                }
            };

            for (Component component : clickableComponents) {
                component.addMouseListener(clickListener);
            }
        }
    }
}
