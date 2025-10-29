package com.flippingcopilot.ui.graph;

import com.flippingcopilot.ui.graph.model.Bounds;
import com.flippingcopilot.ui.graph.model.Config;
import com.flippingcopilot.ui.graph.model.Constants;
import com.flippingcopilot.ui.graph.model.Datapoint;
import lombok.Getter;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@Getter
public class DatapointTooltip {

    private int padding;

    public DatapointTooltip() {
        this.padding = 8; // Padding inside tooltip
    }

    public void draw(Graphics2D g2, Config config, Rectangle pa, Bounds paBounds, Datapoint point) {
        // Prepare tooltip text
        NumberFormat format = new DecimalFormat("#,###", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        String priceStr = format.format(point.getPrice());

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
            case FLIP_TRANSACTION:
                String volumeStr = format.format(Math.abs(point.qty));
                typeText = point.isLow() ? "Buy " + volumeStr : "Sell "+ volumeStr;
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

        Point hoverPosition = point.getHoverPosition(pa, paBounds);

        // Position tooltip near point but ensure it stays within panel bounds
        int tooltipX = hoverPosition.x + 15;
        int tooltipY = hoverPosition.y - tooltipHeight - 5;

        // Adjust if tooltip would go off screen
        if (tooltipX + tooltipWidth > pa.width) {
            tooltipX = hoverPosition.x - tooltipWidth - 5;
        }
        if (tooltipY < 0) {
            tooltipY = hoverPosition.y + 15;
        }

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
        if (point.type != Datapoint.Type.FLIP_TRANSACTION) {
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

    public void drawVolume(Graphics2D g2d, Config config, Rectangle pa, Bounds bounds, Datapoint point) {
        NumberFormat format = new DecimalFormat("#,###", DecimalFormatSymbols.getInstance(Locale.ENGLISH));

        String headerLine = "1h volume";
        String timeLine = Constants.MINUTE_DATE_FORMAT.format(new Date(point.getTime() * 1000L)) + " - " + Constants.MINUTE_TIME_FORMAT.format(new Date((point.getTime()+ Constants.HOUR_SECONDS) * 1000L));
        String lowLine = "low (insta-sell): "+format.format(point.getLowVolume());
        String highLine = "high (insta-buy): "+format.format(point.getHighVolume());


        List<String> lines = Arrays.asList(headerLine, timeLine, lowLine, highLine);

        g2d.setFont(g2d.getFont().deriveFont(Config.FONT_SIZE));
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = lines.stream().mapToInt(fm::stringWidth).max().orElse(0);
        int textHeight = fm.getHeight() * 4;

        int y = bounds.toY2(pa, point.lowVolume + point.highVolume) - textHeight - 8 - 2*padding;
        
        int x = bounds.toX(pa, point.time + Constants.HOUR_SECONDS / 2) - textWidth / 2;
        
        if (x < pa.x) {
            x = pa.x + 2;
        } else if ( x + textWidth > pa.x + pa.width) {
            x -= (x + textWidth - pa.x - pa.width) -2;
        }

        g2d.setColor(Config.TOOLTIP_BACKGROUND);
        g2d.fillRoundRect(x, y, textWidth + 2*padding, textHeight + 2* padding, 8, 8);

        g2d.setColor(Config.TOOLTIP_BORDER);
        g2d.drawRoundRect(x, y, textWidth + 2*padding, textHeight + 2*padding, 8, 8);


        int yPos = y + fm.getHeight()+padding /2;
        g2d.setColor(config.textColor);
        for(String line : lines) {
            g2d.drawString(line, x + padding, yPos);
            yPos += fm.getHeight();
        }
    }
}