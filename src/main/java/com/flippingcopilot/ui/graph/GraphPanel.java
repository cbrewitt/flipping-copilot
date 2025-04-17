package com.flippingcopilot.ui.graph;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;

/**
 * A panel for displaying RuneScape price data with predictions.
 * Designed to be shown in a resizable RuneLite dialog.
 * Now with zooming capabilities and modular architecture.
 */
public class GraphPanel extends JPanel {
    public final String itemName;

    // Component references
    private final DataManager dataManager;
    private final Renderer renderer;
    private final ZoomHandler zoomHandler;
    private final DatapointTooltip tooltip;

    // For point hovering
    private Point mousePosition = null;
    private Datapoint hoveredPoint = null;

    // Dynamic padding based on price labels
    private int leftPadding = Config.PADDING;

    /**
     * Creates a new PriceGraphPanel for the given data.
     *
     * @param data The price data to display
     */
    public GraphPanel(Data data) {
        this.itemName = data.name;

        // Initialize components
        this.dataManager = new DataManager(data);
        this.renderer = new Renderer();
        this.zoomHandler = new ZoomHandler(
                dataManager.getMinTime(),
                dataManager.getMaxTime(),
                dataManager.getMinPrice(),
                dataManager.getMaxPrice()
        );
        this.tooltip = new DatapointTooltip();

        // Set up panel
        setBackground(Config.BACKGROUND_COLOR);
        setPreferredSize(new Dimension(500, 300));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        setupMouseListeners();
    }

    /**
     * Set up mouse listeners for hover detection, selection, and home button
     */
    private void setupMouseListeners() {
        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mousePosition = e.getPoint();

                // Check if mouse is over home button
                boolean wasHomeButtonHovered = zoomHandler.isOverHomeButton(mousePosition);
                boolean homeButtonHovered = zoomHandler.setHomeButtonHovered(
                        zoomHandler.isOverHomeButton(mousePosition)
                );

                if (wasHomeButtonHovered != homeButtonHovered) {
                    repaint(zoomHandler.getHomeButtonRect()); // Only repaint the button area
                }

                // If not selecting, check for point hovering
                if (!zoomHandler.isSelecting()) {
                    hoveredPoint = findClosestPoint(mousePosition);
                } else {
                    hoveredPoint = null;
                }

                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                // Check if clicking home button
                if (zoomHandler.isOverHomeButton(e.getPoint())) {
                    zoomHandler.resetZoom();
                    repaint();
                    return;
                }

                // Only start selection if within plot area
                if (isInPlotArea(e.getPoint())) {
                    zoomHandler.startSelection(e.getPoint());
                    hoveredPoint = null;
                    setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                    repaint();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (zoomHandler.isSelecting()) {
                    zoomHandler.updateSelection(
                            e.getPoint(),
                            leftPadding,
                            getWidth() - Config.PADDING,
                            Config.PADDING,
                            getHeight() - Config.PADDING
                    );
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (zoomHandler.isSelecting()) {
                    setCursor(Cursor.getDefaultCursor());

                    boolean zoomApplied = zoomHandler.applyZoom(
                            x -> xToTime(x),
                            y -> yToPrice(y)
                    );

                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                mousePosition = null;
                hoveredPoint = null;
                zoomHandler.setHomeButtonHovered(false);
                repaint();
            }
        };

        addMouseMotionListener(mouseAdapter);
        addMouseListener(mouseAdapter);
    }

    /**
     * Check if a point is within the plot area
     */
    private boolean isInPlotArea(Point p) {
        return p.x >= leftPadding && p.x <= getWidth() - Config.PADDING &&
                p.y >= Config.PADDING && p.y <= getHeight() - Config.PADDING;
    }

    /**
     * Find the closest data point to the mouse cursor
     */
    private Datapoint findClosestPoint(Point mousePos) {
        if (mousePos == null || !isInPlotArea(mousePos)) return null;

        return dataManager.findClosestPoint(
                mousePos,
                Config.HOVER_RADIUS,
                zoomHandler.getMinTime(),
                zoomHandler.getMaxTime(),
                zoomHandler.getMinPrice(),
                zoomHandler.getMaxPrice(),
                getWidth(),
                getHeight()
        );
    }

    /**
     * Convert X coordinate to time value.
     */
    private int xToTime(int x) {
        double ratio = (double) (x - leftPadding) / (getWidth() - leftPadding - Config.PADDING);
        return (int) (zoomHandler.getMinTime() + ratio * (zoomHandler.getMaxTime() - zoomHandler.getMinTime()));
    }

    /**
     * Convert Y coordinate to price value.
     */
    private int yToPrice(int y) {
        double ratio = (double) (getHeight() - Config.PADDING - y) / (getHeight() - 2 * Config.PADDING);
        return (int) (zoomHandler.getMinPrice() + ratio * (zoomHandler.getMaxPrice() - zoomHandler.getMinPrice()));
    }

    /**
     * Update the renderer and other components to use the calculated left padding
     */
    private void updatePadding(Graphics2D g2) {
        leftPadding = Math.max(
                Config.PADDING,
                renderer.calculateLeftPadding(g2, zoomHandler.getMinPrice(), zoomHandler.getMaxPrice())
        );
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Data data = dataManager.getData();
        if (data == null) return;

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Calculate dynamic left padding based on the price range
        updatePadding(g2);

        int minTime = zoomHandler.getMinTime();
        int maxTime = zoomHandler.getMaxTime();
        int minPrice = zoomHandler.getMinPrice();
        int maxPrice = zoomHandler.getMaxPrice();
        int width = getWidth();
        int height = getHeight();

        // First draw the legend above the plot area
        renderer.drawLegend(g2, width, height, zoomHandler.isZoomed());

        // Draw the plot area background with dynamic padding
        g2.setColor(Config.PLOT_AREA_COLOR);
        g2.fillRect(leftPadding, Config.PADDING,
                width - leftPadding - Config.PADDING,
                height - 2 * Config.PADDING);

        // Draw grid lines
        drawGridWithDynamicPadding(g2, minTime, maxTime, minPrice, maxPrice, width, height);

        // Draw axes with dynamic padding
        drawAxesWithDynamicPadding(g2, minTime, maxTime, minPrice, maxPrice, width, height);

        // Draw IQR areas for predictions
        if (data.predictionTimes != null && data.predictionTimes.length > 0) {
            // Low prediction IQR
            drawPredictionIQRWithDynamicPadding(
                    g2, data.predictionTimes, data.predictionLowIQRLower, data.predictionLowIQRUpper, true,
                    minTime, maxTime, minPrice, maxPrice, width, height
            );

            // High prediction IQR
            drawPredictionIQRWithDynamicPadding(
                    g2, data.predictionTimes, data.predictionHighIQRLower, data.predictionHighIQRUpper, false,
                    minTime, maxTime, minPrice, maxPrice, width, height
            );
        }

        // Draw historical data with dynamic padding
        drawHistoricalDataWithDynamicPadding(
                g2, data.lowTimes, data.lowPrices, true,
                minTime, maxTime, minPrice, maxPrice, width, height
        );

        drawHistoricalDataWithDynamicPadding(
                g2, data.highTimes, data.highPrices, false,
                minTime, maxTime, minPrice, maxPrice, width, height
        );

        // Draw prediction mean lines with dynamic padding
        if (data.predictionTimes != null && data.predictionTimes.length > 0) {
            // Low prediction means
            drawPredictionMeansWithDynamicPadding(
                    g2, data.predictionTimes, data.predictionLowMeans, true,
                    minTime, maxTime, minPrice, maxPrice, width, height
            );

            // High prediction means
            drawPredictionMeansWithDynamicPadding(
                    g2, data.predictionTimes, data.predictionHighMeans, false,
                    minTime, maxTime, minPrice, maxPrice, width, height
            );
        }

        // Draw home button if zoomed
        if (zoomHandler.isZoomed()) {
            int x = getWidth() - Config.PADDING - Config.HOME_BUTTON_SIZE - Config.HOME_BUTTON_MARGIN;
            int y = Config.PADDING + Config.HOME_BUTTON_MARGIN;
            zoomHandler.drawHomeButton(g2, x, y);
        }

        // Draw selection rectangle if selecting
        zoomHandler.drawSelectionRectangle(g2);

        // Draw tooltip for hovered point
        if (hoveredPoint != null) {
            tooltip.draw(g2, hoveredPoint, width, height);
        }
    }

    /**
     * Helper method to draw grid with dynamic padding
     */
    private void drawGridWithDynamicPadding(Graphics2D g2, int minTime, int maxTime, int minPrice, int maxPrice,
                                            int width, int height) {
        g2.setColor(Config.GRID_COLOR);
        g2.setStroke(Config.GRID_STROKE);

        // Get all the 3-hour interval times and midnight times within the visible range
        java.util.List<Long> gridTimes = getFixedIntervalTimes(minTime, maxTime);

        // Draw vertical grid lines for all 3-hour intervals
        for (long time : gridTimes) {
            int x = timeToXWithDynamicPadding((int)time, minTime, maxTime, width);

            // Draw the grid line
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(time * 1000L);
            boolean isMidnight = cal.get(java.util.Calendar.HOUR_OF_DAY) == 0;

            // Use slightly thicker stroke for midnight
            if (isMidnight) {
                Stroke originalStroke = g2.getStroke();
                g2.setStroke(new BasicStroke(1.0f));
                g2.drawLine(x, Config.PADDING, x, height - Config.PADDING);
                g2.setStroke(originalStroke);
            } else {
                g2.drawLine(x, Config.PADDING, x, height - Config.PADDING);
            }
        }

        // Horizontal grid lines (price)
        int numYLines = 8;
        int priceRange = maxPrice - minPrice;
        int priceStep = priceRange / numYLines;

        for (int i = 1; i < numYLines; i++) {
            int price = minPrice + i * priceStep;
            int y = priceToYWithDynamicPadding(price, minPrice, maxPrice, height);
            g2.drawLine(leftPadding, y, width - Config.PADDING, y);
        }
    }

    /**
     * Helper method to get fixed interval times (copied from Renderer for simplicity)
     */
    private java.util.List<Long> getFixedIntervalTimes(int minTime, int maxTime) {
        java.util.List<Long> times = new java.util.ArrayList<>();
        int hourInterval = 6;
        java.util.Calendar calendar = java.util.Calendar.getInstance();

        // Start with the first midnight before or at minTime
        calendar.setTimeInMillis(minTime * 1000L);
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);

        // If minTime is past midnight, move to the previous day
        if (calendar.getTimeInMillis()/1000L > minTime) {
            calendar.add(java.util.Calendar.DAY_OF_MONTH, -1);
        }

        // Add all times in 3-hour intervals until we pass maxTime
        while (calendar.getTimeInMillis()/1000L <= maxTime) {
            // Add this time if it's within or at the range boundary
            if (calendar.getTimeInMillis()/1000L >= minTime) {
                times.add(calendar.getTimeInMillis()/1000L);
            }

            // Add 3 hours
            calendar.add(java.util.Calendar.HOUR_OF_DAY, hourInterval);
        }

        return times;
    }

    /**
     * Helper method to draw axes with dynamic padding
     */
    private void drawAxesWithDynamicPadding(Graphics2D g2, int minTime, int maxTime, int minPrice, int maxPrice,
                                            int width, int height) {
        g2.setColor(Config.AXIS_COLOR);
        g2.setStroke(new BasicStroke(1.0f));

        // X-axis
        g2.drawLine(leftPadding, height - Config.PADDING,
                width - Config.PADDING, height - Config.PADDING);

        // Y-axis
        g2.drawLine(leftPadding, Config.PADDING,
                leftPadding, height - Config.PADDING);

        // X-axis labels
        drawXAxisLabelsWithDynamicPadding(g2, minTime, maxTime, width, height);

        // Y-axis labels
        drawYAxisLabelsWithDynamicPadding(g2, minPrice, maxPrice, width, height);
    }

    /**
     * Helper method to draw X axis labels with dynamic padding
     */
    private void drawXAxisLabelsWithDynamicPadding(Graphics2D g2, int minTime, int maxTime, int width, int height) {
        g2.setFont(g2.getFont().deriveFont(Config.FONT_SIZE));
        g2.setColor(Config.TEXT_COLOR);

        // Get all the fixed interval times
        java.util.List<Long> gridTimes = getFixedIntervalTimes(minTime, maxTime);
        FontMetrics metrics = g2.getFontMetrics();
        int lastRegularLabelEnd = -1000; // Track the end of the last regular label
        int lastMidnightLabelEnd = -1000; // Track the end of the last midnight label

        // Draw labels for all grid times
        for (long time : gridTimes) {
            int x = timeToXWithDynamicPadding((int)time, minTime, maxTime, width);

            // Determine if this is a midnight time (00:00)
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(time * 1000L);
            boolean isMidnight = cal.get(java.util.Calendar.HOUR_OF_DAY) == 0;
            int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);

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
                label = new java.text.SimpleDateFormat("d MMM").format(new java.util.Date(time * 1000L));
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
     * Helper method to draw Y axis labels with dynamic padding
     */
    private void drawYAxisLabelsWithDynamicPadding(Graphics2D g2, int minPrice, int maxPrice, int width, int height) {
        g2.setFont(g2.getFont().deriveFont(Config.FONT_SIZE));
        g2.setColor(Config.TEXT_COLOR);

        int numLabels = 8; // Number of labels to display
        int priceRange = maxPrice - minPrice;
        int priceStep = priceRange / numLabels;

        // Y-axis labels with proper padding
        for (int i = 0; i <= numLabels; i++) {
            int price = minPrice + i * priceStep;
            int y = priceToYWithDynamicPadding(price, minPrice, maxPrice, height);

            // Draw tick
            g2.drawLine(leftPadding - Config.TICK_SIZE, y, leftPadding, y);

            // Draw label
            String label = com.flippingcopilot.ui.UIUtilities.quantityToRSDecimalStack(price, true);
            FontMetrics metrics = g2.getFontMetrics();
            g2.drawString(label,
                    leftPadding - metrics.stringWidth(label) - Config.LABEL_PADDING / 2,
                    y + metrics.getHeight() / 4);
        }
    }

    /**
     * Helper method to draw historical data with dynamic padding
     */
    private void drawHistoricalDataWithDynamicPadding(Graphics2D g2, int[] times, int[] prices, boolean isLow,
                                                      int minTime, int maxTime, int minPrice, int maxPrice,
                                                      int width, int height) {
        if (times.length == 0) return;

        // Set appropriate color
        g2.setColor(isLow ? Config.LOW_COLOR : Config.HIGH_COLOR);
        g2.setStroke(Config.NORMAL_STROKE);

        // Draw connecting lines only if configured to do so
        if (Config.CONNECT_POINTS) {
            drawLinesWithDynamicPadding(g2, times, prices, minTime, maxTime, minPrice, maxPrice, width, height);
        }

        // Draw the points
        for (int i = 0; i < times.length; i++) {
            int x = timeToXWithDynamicPadding(times[i], minTime, maxTime, width);
            int y = priceToYWithDynamicPadding(prices[i], minPrice, maxPrice, height);

            // Only draw points that are within the plot area
            if (x >= leftPadding && x <= width - Config.PADDING &&
                    y >= Config.PADDING && y <= height - Config.PADDING) {
                g2.fillOval(x - Config.POINT_SIZE / 2, y - Config.POINT_SIZE / 2,
                        Config.POINT_SIZE, Config.POINT_SIZE);
            }
        }
    }

    /**
     * Helper method to draw prediction means with dynamic padding
     */
    private void drawPredictionMeansWithDynamicPadding(Graphics2D g2, int[] times, int[] prices, boolean isLow,
                                                       int minTime, int maxTime, int minPrice, int maxPrice,
                                                       int width, int height) {
        if (times.length == 0) return;

        // Set appropriate color and dotted stroke
        g2.setColor(isLow ? Config.LOW_COLOR : Config.HIGH_COLOR);
        g2.setStroke(Config.DOTTED_STROKE);

        // Draw connecting lines only
        drawLinesWithDynamicPadding(g2, times, prices, minTime, maxTime, minPrice, maxPrice, width, height);

        // Draw dots for the prediction mean points
        for (int i = 0; i < times.length; i++) {
            int x = timeToXWithDynamicPadding(times[i], minTime, maxTime, width);
            int y = priceToYWithDynamicPadding(prices[i], minPrice, maxPrice, height);

//            // Only draw points that are within the plot area
//            if (x >= leftPadding && x <= width - Config.PADDING &&
//                    y >= Config.PADDING && y <= height - Config.PADDING) {
//                g2.fillOval(x - Config.POINT_SIZE / 2, y - Config.POINT_SIZE / 2,
//                        Config.POINT_SIZE, Config.POINT_SIZE);
//            }
        }
    }

    /**
     * Helper method to draw prediction IQR with dynamic padding
     */
    private void drawPredictionIQRWithDynamicPadding(Graphics2D g2, int[] times, int[] lowerPrices, int[] upperPrices, boolean isLow,
                                                     int minTime, int maxTime, int minPrice, int maxPrice,
                                                     int width, int height) {
        if (times.length < 2) return;

        // Set appropriate color
        g2.setColor(isLow ? Config.LOW_SHADE_COLOR : Config.HIGH_SHADE_COLOR);

        // Create path for shaded area with clipping
        Path2D path = new Path2D.Double();
        boolean started = false;

        // Start at the first point that's in range
        for (int i = 0; i < times.length; i++) {
            int time = times[i];
            if (time >= minTime && time <= maxTime) {
                int x = timeToXWithDynamicPadding(time, minTime, maxTime, width);
                int y = priceToYWithDynamicPadding(lowerPrices[i], minPrice, maxPrice, height);

                if (!started) {
                    path.moveTo(x, y);
                    started = true;
                } else {
                    path.lineTo(x, y);
                }
            }
        }

        // Draw the upper bound from right to left (for points in range)
        for (int i = times.length - 1; i >= 0; i--) {
            int time = times[i];
            if (time >= minTime && time <= maxTime) {
                int x = timeToXWithDynamicPadding(time, minTime, maxTime, width);
                int y = priceToYWithDynamicPadding(upperPrices[i], minPrice, maxPrice, height);
                path.lineTo(x, y);
            }
        }

        // Close the path if we drew anything
        if (started) {
            path.closePath();

            // Set clipping to plot area to prevent drawing outside
            Shape oldClip = g2.getClip();
            g2.setClip(leftPadding, Config.PADDING,
                    width - leftPadding - Config.PADDING,
                    height - 2 * Config.PADDING);

            g2.fill(path);

            // Restore original clip
            g2.setClip(oldClip);
        }
    }

    /**
     * Helper method to draw lines with dynamic padding
     */
    private void drawLinesWithDynamicPadding(Graphics2D g2, int[] times, int[] prices,
                                             int minTime, int maxTime, int minPrice, int maxPrice,
                                             int width, int height) {
        if (times.length < 2) return;

        // Save current clip
        Shape oldClip = g2.getClip();

        // Set clipping to plot area
        g2.setClip(leftPadding, Config.PADDING,
                width - leftPadding - Config.PADDING,
                height - 2 * Config.PADDING);

        for (int i = 0; i < times.length - 1; i++) {
            // Skip line segments where both points are outside the time range
            if ((times[i] < minTime && times[i+1] < minTime) ||
                    (times[i] > maxTime && times[i+1] > maxTime)) {
                continue;
            }

            int x1 = timeToXWithDynamicPadding(times[i], minTime, maxTime, width);
            int y1 = priceToYWithDynamicPadding(prices[i], minPrice, maxPrice, height);
            int x2 = timeToXWithDynamicPadding(times[i + 1], minTime, maxTime, width);
            int y2 = priceToYWithDynamicPadding(prices[i + 1], minPrice, maxPrice, height);

            g2.drawLine(x1, y1, x2, y2);
        }

        // Restore original clip
        g2.setClip(oldClip);
    }

    /**
     * Convert a time value to X coordinate with dynamic left padding.
     */
    private int timeToXWithDynamicPadding(int time, int minTime, int maxTime, int width) {
        // Constrain the time value to the min/max range
        time = Math.max(minTime, Math.min(maxTime, time));
        double ratio = (double) (time - minTime) / (maxTime - minTime);
        return leftPadding + (int) (ratio * (width - leftPadding - Config.PADDING));
    }

    /**
     * Convert a price value to Y coordinate.
     */
    private int priceToYWithDynamicPadding(int price, int minPrice, int maxPrice, int height) {
        // Constrain the price value to the min/max range
        price = Math.max(minPrice, Math.min(maxPrice, price));
        double ratio = (double) (price - minPrice) / (maxPrice - minPrice);
        return height - Config.PADDING - (int) (ratio * (height - 2 * Config.PADDING));
    }
}