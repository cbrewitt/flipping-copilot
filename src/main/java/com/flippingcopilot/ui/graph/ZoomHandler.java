package com.flippingcopilot.ui.graph;

import lombok.Getter;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;

@Getter
public class ZoomHandler {
    // Selection state
    private Point selectionStart = null;
    private Point selectionEnd = null;
    private boolean isSelecting = false;
    private boolean isZoomed = false;

    // Original data bounds for reset
    private int originalMinTime;
    private int originalMaxTime;
    private int originalMinPrice;
    private int originalMaxPrice;

    // Current view bounds
    private int minTime;
    private int maxTime;
    private int minPrice;
    private int maxPrice;

    // Home button
    private final Rectangle homeButtonRect = new Rectangle();
    private boolean homeButtonHovered = false;

    public ZoomHandler(int minTime, int maxTime, int minPrice, int maxPrice) {
        this.minTime = this.originalMinTime = minTime;
        this.maxTime = this.originalMaxTime = maxTime;
        this.minPrice = this.originalMinPrice = minPrice;
        this.maxPrice = this.originalMaxPrice = maxPrice;
    }

    public void startSelection(Point point) {
        selectionStart = new Point(point);
        selectionEnd = new Point(point);
        isSelecting = true;
    }

    /**
     * Update the end point of the selection.
     *
     * @param point Current point
     * @param minX Minimum X boundary
     * @param maxX Maximum X boundary
     * @param minY Minimum Y boundary
     * @param maxY Maximum Y boundary
     */
    public void updateSelection(Point point, int minX, int maxX, int minY, int maxY) {
        if (!isSelecting) return;

        // Constrain selection to specified boundaries
        selectionEnd = new Point(
                Math.max(minX, Math.min(maxX, point.x)),
                Math.max(minY, Math.min(maxY, point.y))
        );
    }

    /**
     * Attempt to apply the current selection as a zoom.
     *
     * @param timeConverter Function to convert X coordinates to time values
     * @param priceConverter Function to convert Y coordinates to price values
     * @return true if zoom was applied, false otherwise
     */
    public boolean applyZoom(CoordinateConverter timeConverter, CoordinateConverter priceConverter) {
        if (selectionStart == null || selectionEnd == null) return false;

        // Calculate the data values at selection points
        int startTime = timeConverter.toValue(Math.min(selectionStart.x, selectionEnd.x));
        int endTime = timeConverter.toValue(Math.max(selectionStart.x, selectionEnd.x));
        int topPrice = priceConverter.toValue(Math.min(selectionStart.y, selectionEnd.y));
        int bottomPrice = priceConverter.toValue(Math.max(selectionStart.y, selectionEnd.y));

        // Check if selection is large enough
        int width = Math.abs(selectionEnd.x - selectionStart.x);
        int height = Math.abs(selectionEnd.y - selectionStart.y);

        if (width <= 10 || height <= 10) {
            cancelSelection();
            return false;
        }

        // Ensure we have a minimum zoom size
        if (endTime - startTime < (originalMaxTime - originalMinTime) / 20) {
            int midTime = (startTime + endTime) / 2;
            startTime = midTime - (originalMaxTime - originalMinTime) / 40;
            endTime = midTime + (originalMaxTime - originalMinTime) / 40;
        }

        if (topPrice - bottomPrice < (originalMaxPrice - originalMinPrice) / 20) {
            int midPrice = (topPrice + bottomPrice) / 2;
            bottomPrice = midPrice - (originalMaxPrice - originalMinPrice) / 40;
            topPrice = midPrice + (originalMaxPrice - originalMinPrice) / 40;
        }

        // Update the view boundaries
        minTime = startTime;
        maxTime = endTime;
        minPrice = bottomPrice;
        maxPrice = topPrice;

        isZoomed = true;
        cancelSelection();
        return true;
    }

    /**
     * Cancel the current selection without zooming.
     */
    public void cancelSelection() {
        selectionStart = null;
        selectionEnd = null;
        isSelecting = false;
    }

    /**
     * Reset zoom to the original data bounds.
     */
    public void resetZoom() {
        minTime = originalMinTime;
        maxTime = originalMaxTime;
        minPrice = originalMinPrice;
        maxPrice = originalMaxPrice;
        isZoomed = false;
    }

    /**
     * Draw the selection rectangle if currently selecting.
     *
     * @param g2 The graphics context
     */
    public void drawSelectionRectangle(Graphics2D g2) {
        if (!isSelecting || selectionStart == null || selectionEnd == null) return;

        int x = Math.min(selectionStart.x, selectionEnd.x);
        int y = Math.min(selectionStart.y, selectionEnd.y);
        int width = Math.abs(selectionEnd.x - selectionStart.x);
        int height = Math.abs(selectionEnd.y - selectionStart.y);

        // Draw filled rectangle with semi-transparent color
        g2.setColor(Config.SELECTION_COLOR);
        g2.fillRect(x, y, width, height);

        // Draw border
        g2.setColor(Config.SELECTION_BORDER_COLOR);
        g2.setStroke(Config.SELECTION_STROKE);
        g2.drawRect(x, y, width, height);
    }

    /**
     * Draw the home button in the top right corner of the plot area.
     *
     * @param g2 The graphics context
     * @param x X coordinate for button
     * @param y Y coordinate for button
     */
    public void drawHomeButton(Graphics2D g2, int x, int y) {
        if (!isZoomed) return;

        // Update the button rectangle for hit testing
        homeButtonRect.setBounds(x, y, Config.HOME_BUTTON_SIZE, Config.HOME_BUTTON_SIZE);

        // Draw button background
        g2.setColor(homeButtonHovered ? Config.HOME_BUTTON_HOVER_COLOR : Config.HOME_BUTTON_COLOR);

        Shape buttonShape = new RoundRectangle2D.Float(
                x, y,
                Config.HOME_BUTTON_SIZE, Config.HOME_BUTTON_SIZE,
                6, 6
        );
        g2.fill(buttonShape);

        // Draw home icon (simple house shape)
        g2.setColor(java.awt.Color.WHITE);
        g2.setStroke(new java.awt.BasicStroke(1.5f));

        int margin = 4;
        int houseX = x + margin;
        int houseY = y + margin;
        int houseWidth = Config.HOME_BUTTON_SIZE - 2 * margin;
        int houseHeight = Config.HOME_BUTTON_SIZE - 2 * margin;

        // Roof
        int[] xPoints = {houseX, houseX + houseWidth / 2, houseX + houseWidth};
        int[] yPoints = {houseY + houseHeight / 2, houseY, houseY + houseHeight / 2};
        g2.fillPolygon(xPoints, yPoints, 3);

        // House body
        g2.fillRect(houseX + houseWidth / 5, houseY + houseHeight / 2,
                3 * houseWidth / 5, houseHeight / 2);
    }

    public boolean isOverHomeButton(Point point) {
        return homeButtonRect.contains(point);
    }

    public boolean setHomeButtonHovered(boolean hovered) {
        boolean changed = homeButtonHovered != hovered;
        homeButtonHovered = hovered;
        return changed;
    }
}