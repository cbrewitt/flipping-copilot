package copilot.ui;

import java.awt.event.*;
import javax.swing.*;
import static copilot.ui.UIUtilities.*;
import static net.runelite.client.ui.ColorScheme.*;
import copilot.config.*;
import copilot.model.*;

import java.awt.*;

import static copilot.util.DateUtil.formatEpoch;

public class FlipPanel extends JPanel {
    private static final Color HOVER_BACKGROUND = DARKER_GRAY_COLOR.brighter();

    public FlipPanel(Flip flip, CopilotConfig config, Runnable onClick) {
        setLayout(new BorderLayout());
        setBackground(DARKER_GRAY_COLOR);

        var itemQuantity = coloredLabel(String.format("%d x ", flip.closedQuantity), Color.WHITE);

        var itemNameLabel = new JLabel(truncateString(flip.cachedItemName, 19));

        // Create a sub-panel for the left side
        var leftPanel = new JPanel();
        leftPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0)); leftPanel.setBackground(DARKER_GRAY_COLOR);
        leftPanel.add(itemQuantity);
        leftPanel.add(itemNameLabel);

        var profitLabel = coloredLabel(formatProfitWithoutGp(flip.profit), getProfitColor(flip.profit, config));

        // Add the sub-panel to the LINE_START position
        add(leftPanel, BorderLayout.LINE_START);
        add(profitLabel, BorderLayout.LINE_END);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));

        String closeLabel = flip.closedQuantity == flip.openedQuantity ? "Close time" : "Partial close time";
        long closedCostBasis = flip.openedQuantity <= 0
                ? 0
                : (flip.spent * flip.closedQuantity) / flip.openedQuantity;
        String roiText = closedCostBasis > 0
                ? String.format("%.2f%%", ((double) flip.profit / (double) closedCostBasis) * 100.0d)
                : "Unknown";
        Color profitColor = getProfitColor(flip.profit, config);
        String profitColorHex = colorHex(profitColor);

        String tooltipText = String.format("<html>Profit: <font color='%s'>%s</font><br>ROI: <font color='%s'>%s</font><br>Avg buy price: <font color='#32A0FA'>%s</font><br>Avg sell price: <font color='#F0CF7B'>%s</font><br>Tax paid: <font color='#FFFFFF'>%s</font><br>Opened time: %s<br>%s: %s</html>",
                profitColorHex,
                formatProfit(flip.profit),
                profitColorHex,
                roiText,
                formatProfit(flip.getAvgBuyPrice()),
                formatProfit(flip.getAvgSellPrice()),
                formatProfit(flip.taxPaid),
                formatEpoch(flip.openedTime),
                closeLabel,
                formatEpoch(flip.closedTime));
        setToolTipText(tooltipText);
        leftPanel.setToolTipText(tooltipText);
        itemQuantity.setToolTipText(tooltipText);
        itemNameLabel.setToolTipText(tooltipText);
        profitLabel.setToolTipText(tooltipText);

        if (onClick != null) {
            Component[] clickableComponents = {this, leftPanel, itemQuantity, itemNameLabel, profitLabel};
            addMouseActions(onClick, hovered -> {
                setBackground(hovered ? HOVER_BACKGROUND : DARKER_GRAY_COLOR);
                leftPanel.setBackground(hovered ? HOVER_BACKGROUND : DARKER_GRAY_COLOR);
                for (Component component : clickableComponents) {
                    component.setCursor(hovered ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                }
            }, clickableComponents);
        }
    }
}
