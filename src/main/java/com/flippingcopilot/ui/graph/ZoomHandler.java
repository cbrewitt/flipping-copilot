package com.flippingcopilot.ui.graph;

import com.flippingcopilot.ui.graph.model.Bounds;
import com.flippingcopilot.ui.graph.model.Config;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;

@Slf4j
@Getter
public class ZoomHandler {

    private static final int MIN_TIME_DELTA = 60*60;
    private static final int MIN_PRICE_DELTA = 5;
    @Setter
    private Point selectionStart = null;
    @Setter
    private Point selectionEnd = null;
    private boolean isSelecting = false;

    private final Rectangle homeButtonRect = new Rectangle();
    private final Rectangle maxButtonRect = new Rectangle();
    private final Rectangle zoomInButtonRect = new Rectangle();
    private final Rectangle zoomOutButtonRect = new Rectangle();
    private final Rectangle weekButtonRect = new Rectangle();
    private final Rectangle monthButtonRect = new Rectangle();

    public Bounds maxViewBounds;
    public Bounds homeViewBounds;
    public Bounds weekViewBounds;
    public Bounds monthViewBounds;

    public void startSelection(Point point) {
        selectionStart = new Point(point);
        selectionEnd = null;
        isSelecting = true;
    }

    public void applySelection(PlotArea pa) {
        if (selectionStart == null || selectionEnd == null) return;

        int selectionX1 = Math.max(Math.min(selectionStart.x, selectionEnd.x), 0);
        int selectionX2 = Math.min(Math.max(selectionStart.x, selectionEnd.x), pa.w);

        int selectionY1 = Math.max(Math.min(selectionStart.y, selectionEnd.y), 0);
        int selectionY2 = Math.min(Math.max(selectionStart.y, selectionEnd.y), pa.h);

        int newTimeMin = pa.bounds.xMin + (int)(((long)pa.bounds.xDelta() * (long)(selectionX1)) / pa.w);
        int newTimeMax = pa.bounds.xMin + (int)(((long)pa.bounds.xDelta() * (long)(selectionX2)) / pa.w);

        int newPriceMax = pa.bounds.yMax - (int)(((long)pa.bounds.yDelta() * (long)(selectionY1)) / pa.h);
        int newPriceMin = pa.bounds.yMax - (int)(((long)pa.bounds.yDelta() * (long)(selectionY2)) / pa.h);

        if (newTimeMax - newTimeMin < MIN_TIME_DELTA) {
            log.debug("zoomed time delta {}s too small", newTimeMax - newTimeMin);
            cancelSelection();
            return;
        }

        if (newPriceMax - newPriceMin < MIN_PRICE_DELTA) {
            log.debug("zoomed price delta {}s too small", newPriceMax - newPriceMin);
            cancelSelection();
            return;
        }

        pa.bounds = new Bounds(newTimeMin, newTimeMax, newPriceMin, newPriceMax);

        cancelSelection();
    }

    public void applyZoomIn(PlotArea pa) {
        pa.bounds.xMin = Math.min(pa.bounds.xMax - MIN_TIME_DELTA, pa.bounds.xMin + (int) (pa.bounds.xDelta()*0.2));

    }

    public void applyZoomOut(PlotArea pa) {
        int td = pa.bounds.xDelta();
        pa.bounds.xMin= Math.max(maxViewBounds.xMin, pa.bounds.xMin- (int) (td*0.2));
        pa.bounds.xMax = Math.min(maxViewBounds.xMax, pa.bounds.xMax + (int) (td*0.2));
        int pd = pa.bounds.yDelta();
        pa.bounds.yMin = Math.max(maxViewBounds.yMin, pa.bounds.yMin - (int) (pd*0.1));
        pa.bounds.yMax = Math.min(maxViewBounds.yMax, pa.bounds.yMax + (int) (pd*0.1));
    }

    public void applyHomeView(PlotArea pa) {
        pa.bounds = homeViewBounds.copy();
    }

    public void applyMaxView(PlotArea pa) {
        pa.bounds = maxViewBounds.copy();
    }

    public void applyWeekView(PlotArea pa) {
        pa.bounds = weekViewBounds.copy();
    }

    public void applyMonthView(PlotArea pa) {
        pa.bounds = monthViewBounds.copy();
    }

    public void cancelSelection() {
        selectionStart = null;
        selectionEnd = null;
        isSelecting = false;
    }

    public void drawSelectionRectangle(Graphics2D plotAreaG2) {
        if (!isSelecting || selectionStart == null || selectionEnd == null) return;

        int x = Math.min(selectionStart.x, selectionEnd.x);
        int y = Math.min(selectionStart.y, selectionEnd.y);
        int width = Math.abs(selectionEnd.x - selectionStart.x);
        int height = Math.abs(selectionEnd.y - selectionStart.y);

        plotAreaG2.setColor(Config.SELECTION_COLOR);
        plotAreaG2.fillRect(x, y, width, height);

        plotAreaG2.setColor(Config.SELECTION_BORDER_COLOR);
        plotAreaG2.setStroke(Config.SELECTION_STROKE);
        plotAreaG2.drawRect(x, y, width, height);
    }

    public void drawButtons(Graphics2D plotAreaG2, PlotArea pa, Point p) {

        int x = pa.w - Config.GRAPH_BUTTON_SIZE - Config.GRAPH_BUTTON_MARGIN;
        int y = Config.GRAPH_BUTTON_MARGIN;

        // Width for text buttons (Week and Month)
        int textButtonWidth = Config.GRAPH_BUTTON_SIZE * 2;

        // Draw home button
        homeButtonRect.setBounds(x, y, Config.GRAPH_BUTTON_SIZE, Config.GRAPH_BUTTON_SIZE);

        plotAreaG2.setColor(isOverHomeButton(p) ? Config.GRAPH_BUTTON_HOVER_COLOR : Config.GRAPH_BUTTON_COLOR);
        Shape homeButtonShape = new RoundRectangle2D.Float(
                x, y,
                Config.GRAPH_BUTTON_SIZE, Config.GRAPH_BUTTON_SIZE,
                6, 6
        );
        plotAreaG2.fill(homeButtonShape);
        plotAreaG2.setColor(java.awt.Color.WHITE);
        plotAreaG2.setStroke(new java.awt.BasicStroke(1.5f));

        int margin = 4;
        int houseX = x + margin;
        int houseY = y + margin;
        int houseWidth = Config.GRAPH_BUTTON_SIZE - 2 * margin;
        int houseHeight = Config.GRAPH_BUTTON_SIZE - 2 * margin;

        // Roof
        int[] xPoints = {houseX, houseX + houseWidth / 2, houseX + houseWidth};
        int[] yPoints = {houseY + houseHeight / 2, houseY, houseY + houseHeight / 2};
        plotAreaG2.fillPolygon(xPoints, yPoints, 3);

        // House body
        plotAreaG2.fillRect(houseX + houseWidth / 5, houseY + houseHeight / 2,
                3 * houseWidth / 5, houseHeight / 2);

        // Draw max button
        int maxButtonX = x - Config.GRAPH_BUTTON_SIZE - Config.GRAPH_BUTTON_MARGIN;
        maxButtonRect.setBounds(maxButtonX, y, Config.GRAPH_BUTTON_SIZE, Config.GRAPH_BUTTON_SIZE);

        plotAreaG2.setColor(isOverMaxButton(p)  ? Config.GRAPH_BUTTON_HOVER_COLOR : Config.GRAPH_BUTTON_COLOR);
        Shape maxButtonShape = new RoundRectangle2D.Float(
                maxButtonX, y,
                Config.GRAPH_BUTTON_SIZE, Config.GRAPH_BUTTON_SIZE,
                6, 6
        );
        plotAreaG2.fill(maxButtonShape);

        // Draw max icon (four outward arrows)
        plotAreaG2.setColor(java.awt.Color.WHITE);
        plotAreaG2.setStroke(new java.awt.BasicStroke(1.5f));

        int maxMargin = 7;
        int centerX = maxButtonX + Config.GRAPH_BUTTON_SIZE / 2;
        int centerY = y + Config.GRAPH_BUTTON_SIZE / 2;
        int arrowSize = Config.GRAPH_BUTTON_SIZE / 2 - maxMargin;

        // Draw a simple expand icon (four outward arrows)
        // Top-left arrow
        plotAreaG2.drawLine(centerX - 2, centerY - 2, centerX - arrowSize, centerY - arrowSize);
        plotAreaG2.drawLine(centerX - arrowSize, centerY - 2, centerX - arrowSize, centerY - arrowSize);
        plotAreaG2.drawLine(centerX - 2, centerY - arrowSize, centerX - arrowSize, centerY - arrowSize);

        // Top-right arrow
        plotAreaG2.drawLine(centerX + 2, centerY - 2, centerX + arrowSize, centerY - arrowSize);
        plotAreaG2.drawLine(centerX + arrowSize, centerY - 2, centerX + arrowSize, centerY - arrowSize);
        plotAreaG2.drawLine(centerX + 2, centerY - arrowSize, centerX + arrowSize, centerY - arrowSize);

        // Bottom-left arrow
        plotAreaG2.drawLine(centerX - 2, centerY + 2, centerX - arrowSize, centerY + arrowSize);
        plotAreaG2.drawLine(centerX - arrowSize, centerY + 2, centerX - arrowSize, centerY + arrowSize);
        plotAreaG2.drawLine(centerX - 2, centerY + arrowSize, centerX - arrowSize, centerY + arrowSize);

        // Bottom-right arrow
        plotAreaG2.drawLine(centerX + 2, centerY + 2, centerX + arrowSize, centerY + arrowSize);
        plotAreaG2.drawLine(centerX + arrowSize, centerY + 2, centerX + arrowSize, centerY + arrowSize);
        plotAreaG2.drawLine(centerX + 2, centerY + arrowSize, centerX + arrowSize, centerY + arrowSize);

        // Draw zoom in (+) button
        int zoomInButtonX = maxButtonX - Config.GRAPH_BUTTON_SIZE - Config.GRAPH_BUTTON_MARGIN;
        zoomInButtonRect.setBounds(zoomInButtonX, y, Config.GRAPH_BUTTON_SIZE, Config.GRAPH_BUTTON_SIZE);

        plotAreaG2.setColor(isOverZoomInButton(p)  ? Config.GRAPH_BUTTON_HOVER_COLOR : Config.GRAPH_BUTTON_COLOR);
        Shape zoomInButtonShape = new RoundRectangle2D.Float(
                zoomInButtonX, y,
                Config.GRAPH_BUTTON_SIZE, Config.GRAPH_BUTTON_SIZE,
                6, 6
        );
        plotAreaG2.fill(zoomInButtonShape);

        // Draw + symbol
        plotAreaG2.setColor(java.awt.Color.WHITE);
        plotAreaG2.setStroke(new java.awt.BasicStroke(2.0f));

        int plusSize = Config.GRAPH_BUTTON_SIZE - 2 * margin;
        int plusX = zoomInButtonX + margin;
        int plusY = y + margin;

        // Horizontal line
        plotAreaG2.drawLine(
                plusX + plusSize / 4,
                plusY + plusSize / 2,
                plusX + 3 * plusSize / 4,
                plusY + plusSize / 2
        );

        // Vertical line
        plotAreaG2.drawLine(
                plusX + plusSize / 2,
                plusY + plusSize / 4,
                plusX + plusSize / 2,
                plusY + 3 * plusSize / 4
        );

        // Draw zoom out (-) button
        int zoomOutButtonX = zoomInButtonX - Config.GRAPH_BUTTON_SIZE - Config.GRAPH_BUTTON_MARGIN;
        zoomOutButtonRect.setBounds(zoomOutButtonX, y, Config.GRAPH_BUTTON_SIZE, Config.GRAPH_BUTTON_SIZE);

        plotAreaG2.setColor(isOverZoomOutButton(p) ? Config.GRAPH_BUTTON_HOVER_COLOR : Config.GRAPH_BUTTON_COLOR);
        Shape zoomOutButtonShape = new RoundRectangle2D.Float(
                zoomOutButtonX, y,
                Config.GRAPH_BUTTON_SIZE, Config.GRAPH_BUTTON_SIZE,
                6, 6
        );
        plotAreaG2.fill(zoomOutButtonShape);

        // Draw - symbol
        plotAreaG2.setColor(java.awt.Color.WHITE);
        plotAreaG2.setStroke(new java.awt.BasicStroke(2.0f));

        int minusSize = Config.GRAPH_BUTTON_SIZE - 2 * margin;
        int minusX = zoomOutButtonX + margin;
        int minusY = y + margin;

        // Horizontal line (minus symbol)
        plotAreaG2.drawLine(
                minusX + minusSize / 4,
                minusY + minusSize / 2,
                minusX + 3 * minusSize / 4,
                minusY + minusSize / 2
        );

        // Draw Week button (wider than the others)
        int weekButtonX = zoomOutButtonX - textButtonWidth - Config.GRAPH_BUTTON_MARGIN;
        weekButtonRect.setBounds(weekButtonX, y, textButtonWidth, Config.GRAPH_BUTTON_SIZE);

        plotAreaG2.setColor(isOverWeekButton(p) ? Config.GRAPH_BUTTON_HOVER_COLOR : Config.GRAPH_BUTTON_COLOR);
        Shape weekButtonShape = new RoundRectangle2D.Float(
                weekButtonX, y,
                textButtonWidth, Config.GRAPH_BUTTON_SIZE,
                6, 6
        );
        plotAreaG2.fill(weekButtonShape);

        // Draw Week text
        plotAreaG2.setColor(java.awt.Color.WHITE);
        plotAreaG2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        FontMetrics fm = plotAreaG2.getFontMetrics();
        String weekText = "Week";
        int textWidth = fm.stringWidth(weekText);
        int textHeight = fm.getHeight();
        plotAreaG2.drawString(weekText,
                weekButtonX + (textButtonWidth - textWidth) / 2,
                y + (Config.GRAPH_BUTTON_SIZE + textHeight) / 2 - 2);

        // Draw Month button (wider than the others)
        int monthButtonX = weekButtonX - textButtonWidth - Config.GRAPH_BUTTON_MARGIN;
        monthButtonRect.setBounds(monthButtonX, y, textButtonWidth, Config.GRAPH_BUTTON_SIZE);

        plotAreaG2.setColor(isOverMonthButton(p) ? Config.GRAPH_BUTTON_HOVER_COLOR : Config.GRAPH_BUTTON_COLOR);
        Shape monthButtonShape = new RoundRectangle2D.Float(
                monthButtonX, y,
                textButtonWidth, Config.GRAPH_BUTTON_SIZE,
                6, 6
        );
        plotAreaG2.fill(monthButtonShape);

        // Draw Month text
        plotAreaG2.setColor(java.awt.Color.WHITE);
        // Use the same font as for the Week button - plain instead of bold
        String monthText = "Month";
        textWidth = fm.stringWidth(monthText);
        plotAreaG2.drawString(monthText,
                monthButtonX + (textButtonWidth - textWidth) / 2,
                y + (Config.GRAPH_BUTTON_SIZE + textHeight) / 2 - 2);
    }

    public boolean isOverHomeButton(Point point) {
        return homeButtonRect.contains(point);
    }

    public boolean isOverMaxButton(Point point) {
        return maxButtonRect.contains(point);
    }

    public boolean isOverZoomInButton(Point point) {
        return zoomInButtonRect.contains(point);
    }

    public boolean isOverZoomOutButton(Point point) {
        return zoomOutButtonRect.contains(point);
    }

    public boolean isOverWeekButton(Point point) {
        return weekButtonRect.contains(point);
    }

    public boolean isOverMonthButton(Point point) {
        return monthButtonRect.contains(point);
    }
}