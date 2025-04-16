package com.flippingcopilot.ui.graph;

import com.flippingcopilot.ui.UIUtilities;
import com.flippingcopilot.ui.graph.model.Config;
import com.flippingcopilot.ui.graph.model.Constants;
import com.flippingcopilot.ui.graph.model.Datapoint;
import lombok.Getter;

import java.awt.*;
import java.util.Date;

@Getter
public class DatapointTooltip {

    private Rectangle bounds;
    private int padding;

    public DatapointTooltip() {
        this.bounds = new Rectangle();
        this.padding = 8; // Padding inside tooltip
    }

    public void draw(Graphics2D g2, Config config, PlotArea pa, Datapoint point) {
        // Prepare tooltip text
        String priceStr = UIUtilities.quantityToRSDecimalStack(point.getPrice(), true);

        final String typeText;
        final String timeText;
        switch (point.type) {
            case INSTA_SELL_BUY:
                typeText = point.isLow() ? "low (insta-sell)" : "high (insta-buy)";
                timeText = Constants.SECOND_DATE_FORMAT.format(new Date(point.getTime() * 1000L));
                break;
            case FIVE_MIN_AVERAGE:
                typeText = point.isLow() ? "low 5m average" : "high 5min average";
                timeText = Constants.MINUTE_DATE_FORMAT.format(new Date(point.getTime() * 1000L)) + " - " + Constants.MINUTE_DATE_FORMAT.format(new Date((point.getTime()+ Constants.FIVE_MIN_SECONDS) * 1000L));
                break;
            case HOUR_AVERAGE:
                typeText = point.isLow() ? "low 1h average" : "high 1h average";
                timeText = Constants.MINUTE_DATE_FORMAT.format(new Date(point.getTime() * 1000L)) + " - " + Constants.MINUTE_DATE_FORMAT.format(new Date((point.getTime()+ Constants.HOUR_SECONDS) * 1000L));
                break;
            case PREDICTION:
                typeText = point.isLow() ? "low prediction" : "high prediction";
                timeText = Constants.SECOND_DATE_FORMAT.format(new Date(point.getTime() * 1000L));
                break;
            default:
                throw new IllegalArgumentException("invalid point type: "+ point.type);
        }

        // Calculate tooltip dimensions
        g2.setFont(g2.getFont().deriveFont(Config.FONT_SIZE));
        FontMetrics fm = g2.getFontMetrics();
        int typeTextWidth = fm.stringWidth(typeText);
        int timeTextWidth = fm.stringWidth(timeText);
        int priceStrWidth = fm.stringWidth(priceStr);
        int textWidth = Math.max(Math.max(typeTextWidth, timeTextWidth), priceStrWidth);
        int textHeight = fm.getHeight() * 3; // Three lines of text

        int tooltipWidth = textWidth + padding * 2;
        int tooltipHeight = textHeight + padding * 2;

        Point hoverPosition = point.getHoverPosition(pa);

        // Position tooltip near point but ensure it stays within panel bounds
        int tooltipX = hoverPosition.x + 15;
        int tooltipY = hoverPosition.y - tooltipHeight - 5;

        // Adjust if tooltip would go off screen
        if (tooltipX + tooltipWidth > pa.w) {
            tooltipX = hoverPosition.x - tooltipWidth - 5;
        }
        if (tooltipY < 0) {
            tooltipY = hoverPosition.y + 15;
        }

        // Update bounds for later hit testing if needed
        bounds.setBounds(tooltipX, tooltipY, tooltipWidth, tooltipHeight);

        // Draw tooltip background
        g2.setColor(Config.TOOLTIP_BACKGROUND);
        g2.fillRoundRect(tooltipX, tooltipY, tooltipWidth, tooltipHeight, 8, 8);

        // Draw tooltip border
        g2.setColor(Config.TOOLTIP_BORDER);
        g2.drawRoundRect(tooltipX, tooltipY, tooltipWidth, tooltipHeight, 8, 8);

        // Draw tooltip text - first line (type text)
        g2.setColor(config.textColor);
        int yPos = tooltipY + padding + fm.getAscent();
        g2.drawString(typeText, tooltipX + padding, yPos);

        // Draw tooltip text - second line (time)
        yPos += fm.getHeight();
        g2.drawString(timeText, tooltipX + padding, yPos);

        // Draw tooltip text - third line (price)
        yPos += fm.getHeight();
        g2.drawString(priceStr, tooltipX + padding, yPos);

        // Highlight the hovered point
        if (point.isLow()) {
            g2.setColor(config.lowColor);
        } else {
            g2.setColor(config.highColor);
        }

        // Draw larger point to highlight hover

        int highlightSize = 8;
        g2.fillOval(hoverPosition.x - highlightSize / 2,
                hoverPosition.y - highlightSize / 2,
                highlightSize, highlightSize);

        g2.setColor(Color.WHITE);
        g2.drawOval(hoverPosition.x - highlightSize / 2 - 1,
                hoverPosition.y - highlightSize / 2 - 1,
                highlightSize + 2, highlightSize + 2);
    }
}