package com.flippingcopilot.ui.graph;

import com.flippingcopilot.ui.UIUtilities;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
/**
 * Handles rendering of the price graph elements.
 * Separates rendering logic from component logic.
 */
public class Renderer {
    private SimpleDateFormat dateTimeFormat;
    private SimpleDateFormat timeOnlyFormat;
    private SimpleDateFormat dayFormat;

    /**
     * Creates a new graph renderer.
     */
    public Renderer() {
        this.dateTimeFormat = new SimpleDateFormat("d MMM HH:mm");
        this.timeOnlyFormat = new SimpleDateFormat("HH:mm");
        this.dayFormat = new SimpleDateFormat("d MMM");
    }

    /**
     * Convert a time value to X coordinate.
     *
     * @param time    The time value
     * @param minTime Minimum time value in the view
     * @param maxTime Maximum time value in the view
     * @param width   Component width
     * @return The corresponding X coordinate
     */
    public int timeToX(int time, int minTime, int maxTime, int width) {
        // Constrain the time value to the min/max range
        time = Math.max(minTime, Math.min(maxTime, time));
        double ratio = (double) (time - minTime) / (maxTime - minTime);
        return Config.PADDING + (int) (ratio * (width - 2 * Config.PADDING));
    }

    /**
     * Convert a price value to Y coordinate.
     *
     * @param price    The price value
     * @param minPrice Minimum price value in the view
     * @param maxPrice Maximum price value in the view
     * @param height   Component height
     * @return The corresponding Y coordinate
     */
    public int priceToY(int price, int minPrice, int maxPrice, int height) {
        // Constrain the price value to the min/max range
        price = Math.max(minPrice, Math.min(maxPrice, price));
        double ratio = (double) (price - minPrice) / (maxPrice - minPrice);
        return height - Config.PADDING - (int) (ratio * (height - 2 * Config.PADDING));
    }


    /**
     * Get a list of timestamps at fixed intervals (00:00, 03:00, 06:00, etc.)
     * within the visible time range.
     */
    private List<Long> getFixedIntervalTimes(int minTime, int maxTime) {
        List<Long> times = new ArrayList<>();
        int hourInterval = 6;

        Calendar calendar = Calendar.getInstance();

        // Start with the first midnight before or at minTime
        calendar.setTimeInMillis(minTime * 1000L);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        // If minTime is past midnight, move to the previous day
        if (calendar.getTimeInMillis()/1000L > minTime) {
            calendar.add(Calendar.DAY_OF_MONTH, -1);
        }

        // Add all times in 3-hour intervals until we pass maxTime
        while (calendar.getTimeInMillis()/1000L <= maxTime) {
            // Add this time if it's within or at the range boundary
            if (calendar.getTimeInMillis()/1000L >= minTime) {
                times.add(calendar.getTimeInMillis()/1000L);
            }

            // Add 3 hours
            calendar.add(Calendar.HOUR_OF_DAY, hourInterval);
        }

        return times;
    }


    /**
     * Draw labels for the X-axis (time) with improved positioning for midnight labels.
     */
    private void drawXAxisLabels(Graphics2D g2, int minTime, int maxTime, int width, int height) {
        g2.setFont(g2.getFont().deriveFont(Config.FONT_SIZE));
        g2.setColor(Config.TEXT_COLOR);

        // Get all the fixed interval times
        List<Long> gridTimes = getFixedIntervalTimes(minTime, maxTime);
        FontMetrics metrics = g2.getFontMetrics();
        int lastRegularLabelEnd = -1000; // Track the end of the last regular label
        int lastMidnightLabelEnd = -1000; // Track the end of the last midnight label

        // Draw labels for all grid times
        for (long time : gridTimes) {
            int x = timeToX((int)time, minTime, maxTime, width);

            // Determine if this is a midnight time (00:00)
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(time * 1000L);
            boolean isMidnight = cal.get(Calendar.HOUR_OF_DAY) == 0;
            int hour = cal.get(Calendar.HOUR_OF_DAY);

            // Draw tick mark with appropriate length
            if (isMidnight) {
                // Midnight tick - longer
                g2.drawLine(x, height - Config.PADDING, x, height - Config.PADDING + Config.TICK_SIZE * 2);
            } else {
                // Regular hour tick - shorter
                g2.drawLine(x, height - Config.PADDING, x, height - Config.PADDING + Config.TICK_SIZE);
            }

            // Format the label based on whether it's midnight or not
            String label;
            if (isMidnight) {
                label = dayFormat.format(new Date(time * 1000L));
            } else {
                label = String.format("%02d:00", hour);
            }

            int labelWidth = metrics.stringWidth(label);

            // Check if we should draw this label (prevent overlap)
            if (isMidnight) {
                // Midnight labels go on a separate line (lower)
                if (x - labelWidth / 2 > lastMidnightLabelEnd) {
                    g2.drawString(label, x - labelWidth / 2,
                            height - Config.PADDING + Config.LABEL_PADDING + metrics.getHeight());
                    lastMidnightLabelEnd = x + labelWidth / 2 + 10;
                }
            } else {
                // Regular hour labels go on the first line
                if (x - labelWidth / 2 > lastRegularLabelEnd) {
                    g2.drawString(label, x - labelWidth / 2,
                            height - Config.PADDING + Config.LABEL_PADDING);
                    lastRegularLabelEnd = x + labelWidth / 2 + 10;
                }
            }
        }
    }

    /**
     * Draw labels for the Y-axis (price).
     */
    private void drawYAxisLabels(Graphics2D g2, int minPrice, int maxPrice, int width, int height) {
        g2.setFont(g2.getFont().deriveFont(Config.FONT_SIZE));
        g2.setColor(Config.TEXT_COLOR);

        int numLabels = 8; // Increase number of labels
        int priceRange = maxPrice - minPrice;
        int priceStep = priceRange / numLabels;

        // Find maximum label width to ensure proper spacing
        int maxLabelWidth = 0;
        for (int i = 0; i <= numLabels; i++) {
            int price = minPrice + i * priceStep;
            String label = UIUtilities.quantityToRSDecimalStack(price, true);
            FontMetrics metrics = g2.getFontMetrics();
            int labelWidth = metrics.stringWidth(label);
            maxLabelWidth = Math.max(maxLabelWidth, labelWidth);
        }

        // Add extra padding for Y axis based on max label width
        int yAxisLabelPadding = maxLabelWidth + Config.LABEL_PADDING;

        for (int i = 0; i <= numLabels; i++) {
            int price = minPrice + i * priceStep;
            int y = priceToY(price, minPrice, maxPrice, height);

            // Draw tick
            g2.drawLine(Config.PADDING - Config.TICK_SIZE, y, Config.PADDING, y);

            // Draw label
            String label = UIUtilities.quantityToRSDecimalStack(price, true);
            FontMetrics metrics = g2.getFontMetrics();
            g2.drawString(label,
                    Config.PADDING - yAxisLabelPadding,
                    y + metrics.getHeight() / 4);
        }
    }

    /**
     * Draw lines connecting a series of points.
     */
    private void drawLines(Graphics2D g2, int[] times, int[] prices,
                           int minTime, int maxTime, int minPrice, int maxPrice,
                           int width, int height) {
        if (times.length < 2) return;

        // Save current clip
        Shape oldClip = g2.getClip();

        // Set clipping to plot area
        g2.setClip(Config.PADDING, Config.PADDING,
                width - 2 * Config.PADDING,
                height - 2 * Config.PADDING);

        for (int i = 0; i < times.length - 1; i++) {
            // Skip line segments where both points are outside the time range
            if ((times[i] < minTime && times[i+1] < minTime) ||
                    (times[i] > maxTime && times[i+1] > maxTime)) {
                continue;
            }

            int x1 = timeToX(times[i], minTime, maxTime, width);
            int y1 = priceToY(prices[i], minPrice, maxPrice, height);
            int x2 = timeToX(times[i + 1], minTime, maxTime, width);
            int y2 = priceToY(prices[i + 1], minPrice, maxPrice, height);

            g2.drawLine(x1, y1, x2, y2);
        }

        // Restore original clip
        g2.setClip(oldClip);
    }


    /**
     * Draw a legend explaining the graph elements.
     *
     * @param g2       The graphics context
     * @param width    Component width
     * @param height   Component height
     * @param isZoomed Whether the graph is currently zoomed
     */
    public void drawLegend(Graphics2D g2, int width, int height, boolean isZoomed) {
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, Config.FONT_SIZE));
        FontMetrics metrics = g2.getFontMetrics();

        // Legend item text labels
        String[] labels = {"Buy (Low)", "Sell (High)", "Buy Prediction", "Sell Prediction"};

        // Calculate legend position - now above the plot area
        int legendY = Config.PADDING / 2; // Half the padding from the top
        int lineLength = 20;
        int itemHeight = 15;
        int itemPadding = 30; // Space between text end and next item start

        // Calculate widths for each legend item based on text length
        int[] itemWidths = new int[labels.length];
        int totalWidth = 0;

        for (int i = 0; i < labels.length; i++) {
            itemWidths[i] = lineLength + 5 + metrics.stringWidth(labels[i]);
            totalWidth += itemWidths[i];
        }

        // Add padding between items to total width
        totalWidth += itemPadding * (labels.length - 1);

        // Calculate starting X position to center the legend
        int legendStartX = (width - totalWidth) / 2;
        int currentX = legendStartX;

        // Low prices
        g2.setColor(Config.LOW_COLOR);
        g2.setStroke(Config.NORMAL_STROKE);
        if (Config.CONNECT_POINTS) {
            g2.drawLine(currentX, legendY + itemHeight/2, currentX + lineLength, legendY + itemHeight/2);
        }
        g2.fillOval(currentX + lineLength / 2 - 2, legendY + itemHeight/2 - 2, 5, 5);
        g2.setColor(Config.TEXT_COLOR);
        g2.drawString(labels[0], currentX + lineLength + 5, legendY + itemHeight/2 + 4);
        currentX += itemWidths[0] + itemPadding;

        // High prices
        g2.setColor(Config.HIGH_COLOR);
        g2.setStroke(Config.NORMAL_STROKE);
        if (Config.CONNECT_POINTS) {
            g2.drawLine(currentX, legendY + itemHeight/2, currentX + lineLength, legendY + itemHeight/2);
        }
        g2.fillOval(currentX + lineLength / 2 - 2, legendY + itemHeight/2 - 2, 5, 5);
        g2.setColor(Config.TEXT_COLOR);
        g2.drawString(labels[1], currentX + lineLength + 5, legendY + itemHeight/2 + 4);
        currentX += itemWidths[1] + itemPadding;

        // Low prediction mean
        g2.setColor(Config.LOW_COLOR);
        g2.setStroke(Config.DOTTED_STROKE);
        g2.drawLine(currentX, legendY + itemHeight/2, currentX + lineLength, legendY + itemHeight/2);
//        g2.fillOval(currentX + lineLength / 2 - 2, legendY + itemHeight/2 - 2, 5, 5);
        g2.setColor(Config.TEXT_COLOR);
        g2.drawString(labels[2], currentX + lineLength + 5, legendY + itemHeight/2 + 4);
        currentX += itemWidths[2] + itemPadding;

        // High prediction mean
        g2.setColor(Config.HIGH_COLOR);
        g2.setStroke(Config.DOTTED_STROKE);
        g2.drawLine(currentX, legendY + itemHeight/2, currentX + lineLength, legendY + itemHeight/2);
//        g2.fillOval(currentX + lineLength / 2 - 2, legendY + itemHeight/2 - 2, 5, 5);
        g2.setColor(Config.TEXT_COLOR);
        g2.drawString(labels[3], currentX + lineLength + 5, legendY + itemHeight/2 + 4);
    }

    public int calculateLeftPadding(Graphics2D g2, int minPrice, int maxPrice) {
        g2.setFont(g2.getFont().deriveFont(Config.FONT_SIZE));
        FontMetrics metrics = g2.getFontMetrics();

        int numLabels = 8;
        int priceRange = maxPrice - minPrice;
        int priceStep = priceRange / numLabels;

        int maxLabelWidth = 0;
        for (int i = 0; i <= numLabels; i++) {
            int price = minPrice + i * priceStep;
            String label = UIUtilities.quantityToRSDecimalStack(price, true);
            int labelWidth = metrics.stringWidth(label);
            maxLabelWidth = Math.max(maxLabelWidth, labelWidth);
        }
        return maxLabelWidth + Config.LABEL_PADDING + Config.TICK_SIZE;
    }
}