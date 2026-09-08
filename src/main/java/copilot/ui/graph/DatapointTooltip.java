package copilot.ui.graph;

import copilot.ui.graph.model.*;

import java.awt.*;
import java.text.*;
import java.util.*;
import java.util.List;

public class DatapointTooltip {

    // Padding inside tooltip
    private static final int PADDING = 8;

    public static void draw(Graphics2D graphics, Config config, Rectangle pa, Bounds paBounds, Datapoint point) {
        // Prepare tooltip text
        NumberFormat format = new DecimalFormat("#,###", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        String priceStr = format.format(point.getPrice());

        final String typeText;
        String timeText = Constants.SECOND_DATE_FORMAT.format(new Date(point.getTime() * 1000L));
        switch (point.type) {
            case INSTA_SELL_BUY:
                typeText = point.isLow() ? "low (insta-sell)" : "high (insta-buy)";
                break;
            case FIVE_MIN_AVERAGE:
                typeText = point.isLow() ? "low 5m average" : "high 5min average";
                timeText = timeRange(point.time, Constants.FIVE_MIN_SECONDS, Constants.MINUTE_DATE_FORMAT);
                break;
            case HOUR_AVERAGE:
                typeText = point.isLow() ? "low 1h average" : "high 1h average";
                timeText = timeRange(point.time, Constants.HOUR_SECONDS, Constants.MINUTE_DATE_FORMAT);
                break;
            case PREDICTION:
                typeText = point.isLow() ? "low prediction" : "high prediction";
                break;
            case FLIP_TRANSACTION:
                String volumeStr = format.format(Math.abs(point.qty));
                typeText = point.isLow() ? "Buy " + volumeStr : "Sell "+ volumeStr;
                break;
            default:
                throw new IllegalArgumentException("invalid point type: "+ point.type);
        }

        // Calculate tooltip dimensions
        graphics.setFont(graphics.getFont().deriveFont(Config.FONT_SIZE));
        var fm = graphics.getFontMetrics();
        int typeTextWidth = fm.stringWidth(typeText), timeTextWidth = fm.stringWidth(timeText);
        int priceStrWidth = fm.stringWidth(priceStr);
        int textWidth = Math.max(Math.max(typeTextWidth, timeTextWidth), priceStrWidth);
        int textHeight = fm.getHeight() * 3; // Three lines of text

        int tooltipWidth = textWidth + PADDING * 2, tooltipHeight = textHeight + PADDING * 2;

        Point hoverPosition = point.getHoverPosition(pa, paBounds);

        // Position tooltip near point but ensure it stays within panel bounds
        int tooltipX = hoverPosition.x + 15, tooltipY = hoverPosition.y - tooltipHeight - 5;

        // Adjust if tooltip would go off screen
        if (tooltipX + tooltipWidth > pa.width) { tooltipX = hoverPosition.x - tooltipWidth - 5; }
        if (tooltipY < 0) { tooltipY = hoverPosition.y + 15; }

        drawBox(graphics, config, tooltipX, tooltipY, tooltipWidth, tooltipHeight,
                tooltipY + PADDING + fm.getAscent(), Arrays.asList(typeText, timeText, priceStr));

        // Highlight the hovered point
        if (point.type != Datapoint.Type.FLIP_TRANSACTION) {
            graphics.setColor(point.isLow() ? config.lowColor : config.highColor);

            // Draw larger point to highlight hover
            int highlightSize = 8;
            graphics.fillOval(hoverPosition.x - highlightSize / 2,
                    hoverPosition.y - highlightSize / 2,
                    highlightSize, highlightSize);

            graphics.setColor(Color.WHITE);
            graphics.drawOval(hoverPosition.x - highlightSize / 2 - 1,
                    hoverPosition.y - highlightSize / 2 - 1,
                    highlightSize + 2, highlightSize + 2);
        }
    }

    public static void drawVolume(Graphics2D graphics, Config config, Rectangle pa, Bounds bounds, Datapoint point) {
        NumberFormat format = new DecimalFormat("#,###", DecimalFormatSymbols.getInstance(Locale.ENGLISH));

        String headerLine = "1h volume";
        String timeLine = timeRange(point.time, Constants.HOUR_SECONDS, Constants.MINUTE_TIME_FORMAT);
        String lowLine = "low (insta-sell): "+format.format(point.getLowVolume());
        String highLine = "high (insta-buy): "+format.format(point.getHighVolume());

        List<String> lines = Arrays.asList(headerLine, timeLine, lowLine, highLine);

        graphics.setFont(graphics.getFont().deriveFont(Config.FONT_SIZE));
        var fm = graphics.getFontMetrics();
        int textWidth = lines.stream().mapToInt(fm::stringWidth).max().orElse(0);
        int textHeight = fm.getHeight() * 4;

        int y = bounds.toY2(pa, point.lowVolume + point.highVolume) - textHeight - 8 - 2*PADDING;
        
        int x = bounds.toX(pa, point.time + Constants.HOUR_SECONDS / 2) - textWidth / 2;
        
        if (x < pa.x) { x = pa.x + 2; } else if ( x + textWidth > pa.x + pa.width) {
            x -= (x + textWidth - pa.x - pa.width) -2;
        }

        drawBox(graphics, config, x, y, textWidth + 2 * PADDING, textHeight + 2 * PADDING,
                y + fm.getHeight() + PADDING / 2, lines);
    }

    private static String timeRange(int time, int duration, DateFormat endFormat) {
        return Constants.MINUTE_DATE_FORMAT.format(new Date(time * 1000L)) + " - "
                + endFormat.format(new Date((time + duration) * 1000L));
    }

    private static void drawBox(Graphics2D graphics, Config config, int x, int y, int width, int height,
                                int baseline, List<String> lines) {
        graphics.setColor(Config.TOOLTIP_BACKGROUND);
        graphics.fillRoundRect(x, y, width, height, 8, 8);
        graphics.setColor(Config.TOOLTIP_BORDER);
        graphics.drawRoundRect(x, y, width, height, 8, 8);
        graphics.setColor(config.textColor);
        for (String line : lines) {
            graphics.drawString(line, x + PADDING, baseline);
            baseline += graphics.getFontMetrics().getHeight();
        }
    }
}
