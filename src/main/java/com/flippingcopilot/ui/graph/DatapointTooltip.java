package com.flippingcopilot.ui.graph;

import com.flippingcopilot.ui.UIUtilities;
import com.flippingcopilot.ui.graph.model.Datapoint;
import lombok.Getter;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.text.SimpleDateFormat;
import java.util.Date;

@Getter
public class DatapointTooltip {

    private SimpleDateFormat dateFormat;
    private Rectangle bounds;
    private int padding;

    public DatapointTooltip() {
        this.dateFormat = new SimpleDateFormat("d MMM HH:mm");
        this.bounds = new Rectangle();
        this.padding = 8; // Padding inside tooltip
    }

    /**
     * Draws a tooltip for the given data point.
     *
     * @param g2 The graphics context
     * @param point The data point to show tooltip for
     * @param containerWidth The width of the container
     * @param containerHeight The height of the container
     */
    public void draw(Graphics2D g2, Datapoint point, int containerWidth, int containerHeight) {
        // Prepare tooltip text
        String timeStr = dateFormat.format(new Date(point.getTime() * 1000L));
        String priceStr = UIUtilities.quantityToRSDecimalStack(point.getPrice(), true);
        String typeStr = point.isLow() ? "insta sell" : "insta buy";
        if (point.isPrediction()) {
            typeStr += " Prediction";
        }

        String mainText = priceStr + " (" + typeStr + ")";
        String timeText = timeStr;

        // Calculate tooltip dimensions
        g2.setFont(g2.getFont().deriveFont(Config.FONT_SIZE));
        FontMetrics fm = g2.getFontMetrics();
        int mainTextWidth = fm.stringWidth(mainText);
        int timeTextWidth = fm.stringWidth(timeText);
        int textWidth = Math.max(mainTextWidth, timeTextWidth);
        int textHeight = fm.getHeight() * 2; // Two lines of text

        int tooltipWidth = textWidth + padding * 2;
        int tooltipHeight = textHeight + padding * 2;

        // Position tooltip near point but ensure it stays within panel bounds
        int tooltipX = point.getScreenPosition().x + 15;
        int tooltipY = point.getScreenPosition().y - tooltipHeight - 5;

        // Adjust if tooltip would go off screen
        if (tooltipX + tooltipWidth > containerWidth) {
            tooltipX = point.getScreenPosition().x - tooltipWidth - 5;
        }
        if (tooltipY < 0) {
            tooltipY = point.getScreenPosition().y + 15;
        }

        // Update bounds for later hit testing if needed
        bounds.setBounds(tooltipX, tooltipY, tooltipWidth, tooltipHeight);

        // Draw tooltip background
        g2.setColor(Config.TOOLTIP_BACKGROUND);
        g2.fillRoundRect(tooltipX, tooltipY, tooltipWidth, tooltipHeight, 8, 8);

        // Draw tooltip border
        g2.setColor(Config.TOOLTIP_BORDER);
        g2.drawRoundRect(tooltipX, tooltipY, tooltipWidth, tooltipHeight, 8, 8);

        // Draw tooltip text - first line (main text)
        g2.setColor(Config.TEXT_COLOR);
        int yPos = tooltipY + padding + fm.getAscent();
        g2.drawString(mainText, tooltipX + padding, yPos);

        // Draw tooltip text - second line (time)
        yPos += fm.getHeight();
        g2.drawString(timeText, tooltipX + padding, yPos);

        // Highlight the hovered point
        if (point.isLow()) {
            g2.setColor(Config.LOW_COLOR);
        } else {
            g2.setColor(Config.HIGH_COLOR);
        }

        // Draw larger point to highlight hover
        int highlightSize = 8;
        g2.fillOval(point.getScreenPosition().x - highlightSize / 2,
                point.getScreenPosition().y - highlightSize / 2,
                highlightSize, highlightSize);

        g2.setColor(Color.WHITE);
        g2.drawOval(point.getScreenPosition().x - highlightSize / 2 - 1,
                point.getScreenPosition().y - highlightSize / 2 - 1,
                highlightSize + 2, highlightSize + 2);
    }
}