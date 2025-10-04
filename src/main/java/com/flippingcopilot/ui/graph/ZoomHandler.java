package com.flippingcopilot.ui.graph;

import com.flippingcopilot.ui.graph.model.Bounds;
import com.flippingcopilot.ui.graph.model.Config;
import com.flippingcopilot.util.MathUtil;
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

    public void applySelection(Rectangle pa, Bounds bounds) {
        if (selectionStart == null || selectionEnd == null) return;

        int x1 = MathUtil.clamp(Math.min(selectionStart.x, selectionEnd.x), pa.x, pa.x+pa.width);
        int y1 = MathUtil.clamp(Math.min(selectionStart.y, selectionEnd.y), pa.y, pa.y+pa.height);
        int x2 = MathUtil.clamp(Math.max(selectionStart.x, selectionEnd.x), pa.x, pa.x+pa.width);
        int y2 = MathUtil.clamp(Math.max(selectionStart.y, selectionEnd.y), pa.y, pa.y+pa.height);


        int newTimeMin = bounds.xMin + (int)(((long)bounds.xDelta() * (long)(x1 - pa.x)) / ((long) pa.width));
        int newTimeMax = bounds.xMin + (int)(((long)bounds.xDelta() * (long)(x2 - pa.x)) / ((long) pa.width));

        int newPriceMax = (int) (bounds.yMax - ((bounds.yDelta() * (long)(y1 - pa.y)) / ((long) pa.height)));
        int newPriceMin = (int) (bounds.yMax - ((bounds.yDelta() * (long)(y2 - pa.y)) / ((long) pa.height)));

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

        bounds.xMin = newTimeMin;
        bounds.xMax = newTimeMax;
        bounds.yMin = newPriceMin;
        bounds.yMax = newPriceMax;

        cancelSelection();
    }

    public void applyZoomIn(Bounds bounds) {
        bounds.xMin = Math.min(bounds.xMax - MIN_TIME_DELTA, bounds.xMin + (int) (bounds.xDelta()*0.2));

    }

    public void applyZoomOut( Bounds bounds) {
        int td = bounds.xDelta();
        bounds.xMin= Math.max(maxViewBounds.xMin, bounds.xMin- (int) (td*0.2));
        bounds.xMax = Math.min(maxViewBounds.xMax, bounds.xMax + (int) (td*0.2));
        int pd = (int) bounds.yDelta();
        bounds.yMin = Math.max(maxViewBounds.yMin, bounds.yMin - (int) (pd*0.1));
        bounds.yMax = Math.min(maxViewBounds.yMax, bounds.yMax + (int) (pd*0.1));
        bounds.y2Max = Math.min(maxViewBounds.y2Max, bounds.y2Max + (int) (pd*0.05));
    }

    public void applyHomeView(Bounds bounds) {
        bounds.xMin = homeViewBounds.xMin;
        bounds.xMax = homeViewBounds.xMax;
        bounds.yMin = homeViewBounds.yMin;
        bounds.yMax = homeViewBounds.yMax;
        bounds.y2Min = homeViewBounds.y2Min;
        bounds.y2Max = homeViewBounds.y2Max;;
    }

    public void applyMaxView(Bounds bounds) {
        bounds.xMin = maxViewBounds.xMin;
        bounds.xMax = maxViewBounds.xMax;
        bounds.yMin = maxViewBounds.yMin;
        bounds.yMax = maxViewBounds.yMax;
        bounds.y2Min = maxViewBounds.y2Min;
        bounds.y2Max = maxViewBounds.y2Max;;
    }

    public void applyWeekView(Bounds bounds) {
        bounds.xMin = weekViewBounds.xMin;
        bounds.xMax = weekViewBounds.xMax;
        bounds.yMin = weekViewBounds.yMin;
        bounds.yMax = weekViewBounds.yMax;
        bounds.y2Min = weekViewBounds.y2Min;
        bounds.y2Max = weekViewBounds.y2Max;
    }

    public void applyMonthView(Bounds bounds) {
        bounds.xMin = monthViewBounds.xMin;
        bounds.xMax = monthViewBounds.xMax;
        bounds.yMin = monthViewBounds.yMin;
        bounds.yMax = monthViewBounds.yMax;
        bounds.y2Min = monthViewBounds.y2Min;
        bounds.y2Max = monthViewBounds.y2Max;;
    }

    public void cancelSelection() {
        selectionStart = null;
        selectionEnd = null;
        isSelecting = false;
    }

    public void drawSelectionRectangle(Graphics2D g2d, Rectangle pa) {
        if (!isSelecting || selectionStart == null || selectionEnd == null) return;

        int x1 = MathUtil.clamp(Math.min(selectionStart.x, selectionEnd.x), pa.x, pa.x+pa.width);
        int y1 = MathUtil.clamp(Math.min(selectionStart.y, selectionEnd.y), pa.y, pa.y+pa.height);
        int x2 = MathUtil.clamp(Math.max(selectionStart.x, selectionEnd.x), pa.x, pa.x+pa.width);
        int y2 = MathUtil.clamp(Math.max(selectionStart.y, selectionEnd.y), pa.y, pa.y+pa.height);

        g2d.setColor(Config.SELECTION_COLOR);
        g2d.fillRect(x1, y1, x2-x1, y2 - y1);

        g2d.setColor(Config.SELECTION_BORDER_COLOR);
        g2d.setStroke(Config.SELECTION_STROKE);
        g2d.drawRect(x1, y1, x2-x1, y2 - y1);
    }

    public void drawButtons(Graphics2D g2d, Rectangle pa, Point p) {

        int x = pa.x + pa.width - Config.GRAPH_BUTTON_SIZE - Config.GRAPH_BUTTON_MARGIN;
        int y = pa.y + Config.GRAPH_BUTTON_MARGIN;

        // Width for text buttons (Week and Month)
        int textButtonWidth = Config.GRAPH_BUTTON_SIZE * 2;

        homeButtonRect.setBounds(x,  y, Config.GRAPH_BUTTON_SIZE, Config.GRAPH_BUTTON_SIZE);
        g2d.setColor(isOverHomeButton(p) ? Config.GRAPH_BUTTON_HOVER_COLOR : Config.GRAPH_BUTTON_COLOR);
        Shape homeButtonShape = new RoundRectangle2D.Float(
                x, y,
                Config.GRAPH_BUTTON_SIZE, Config.GRAPH_BUTTON_SIZE,
                6, 6
        );
        g2d.fill(homeButtonShape);
        g2d.setColor(java.awt.Color.WHITE);
        g2d.setStroke(new java.awt.BasicStroke(1.5f));

        int margin = 4;
        int houseX = x + margin;
        int houseY = y + margin;
        int houseWidth = Config.GRAPH_BUTTON_SIZE - 2 * margin;
        int houseHeight = Config.GRAPH_BUTTON_SIZE - 2 * margin;

        // Roof
        int[] xPoints = {houseX, houseX + houseWidth / 2, houseX + houseWidth};
        int[] yPoints = {houseY + houseHeight / 2, houseY, houseY + houseHeight / 2};
        g2d.fillPolygon(xPoints, yPoints, 3);

        // House body
        g2d.fillRect(houseX + houseWidth / 5, houseY + houseHeight / 2,
                3 * houseWidth / 5, houseHeight / 2);

        // Draw max button
        int maxButtonX = x - Config.GRAPH_BUTTON_SIZE - Config.GRAPH_BUTTON_MARGIN;
        maxButtonRect.setBounds(maxButtonX, y, Config.GRAPH_BUTTON_SIZE, Config.GRAPH_BUTTON_SIZE);

        g2d.setColor(isOverMaxButton(p)  ? Config.GRAPH_BUTTON_HOVER_COLOR : Config.GRAPH_BUTTON_COLOR);
        Shape maxButtonShape = new RoundRectangle2D.Float(
                maxButtonX, y,
                Config.GRAPH_BUTTON_SIZE, Config.GRAPH_BUTTON_SIZE,
                6, 6
        );
        g2d.fill(maxButtonShape);

        // Draw max icon (four outward arrows)
        g2d.setColor(java.awt.Color.WHITE);
        g2d.setStroke(new java.awt.BasicStroke(1.5f));

        int maxMargin = 7;
        int centerX = maxButtonX + Config.GRAPH_BUTTON_SIZE / 2;
        int centerY = y + Config.GRAPH_BUTTON_SIZE / 2;
        int arrowSize = Config.GRAPH_BUTTON_SIZE / 2 - maxMargin;

        // Draw a simple expand icon (four outward arrows)
        // Top-left arrow
        g2d.drawLine(centerX - 2, centerY - 2, centerX - arrowSize, centerY - arrowSize);
        g2d.drawLine(centerX - arrowSize, centerY - 2, centerX - arrowSize, centerY - arrowSize);
        g2d.drawLine(centerX - 2, centerY - arrowSize, centerX - arrowSize, centerY - arrowSize);

        // Top-right arrow
        g2d.drawLine(centerX + 2, centerY - 2, centerX + arrowSize, centerY - arrowSize);
        g2d.drawLine(centerX + arrowSize, centerY - 2, centerX + arrowSize, centerY - arrowSize);
        g2d.drawLine(centerX + 2, centerY - arrowSize, centerX + arrowSize, centerY - arrowSize);

        // Bottom-left arrow
        g2d.drawLine(centerX - 2, centerY + 2, centerX - arrowSize, centerY + arrowSize);
        g2d.drawLine(centerX - arrowSize, centerY + 2, centerX - arrowSize, centerY + arrowSize);
        g2d.drawLine(centerX - 2, centerY + arrowSize, centerX - arrowSize, centerY + arrowSize);

        // Bottom-right arrow
        g2d.drawLine(centerX + 2, centerY + 2, centerX + arrowSize, centerY + arrowSize);
        g2d.drawLine(centerX + arrowSize, centerY + 2, centerX + arrowSize, centerY + arrowSize);
        g2d.drawLine(centerX + 2, centerY + arrowSize, centerX + arrowSize, centerY + arrowSize);

        // Draw zoom in (+) button
        int zoomInButtonX = maxButtonX - Config.GRAPH_BUTTON_SIZE - Config.GRAPH_BUTTON_MARGIN;
        zoomInButtonRect.setBounds(zoomInButtonX, y, Config.GRAPH_BUTTON_SIZE, Config.GRAPH_BUTTON_SIZE);

        g2d.setColor(isOverZoomInButton(p)  ? Config.GRAPH_BUTTON_HOVER_COLOR : Config.GRAPH_BUTTON_COLOR);
        Shape zoomInButtonShape = new RoundRectangle2D.Float(
                zoomInButtonX, y,
                Config.GRAPH_BUTTON_SIZE, Config.GRAPH_BUTTON_SIZE,
                6, 6
        );
        g2d.fill(zoomInButtonShape);

        // Draw + symbol
        g2d.setColor(java.awt.Color.WHITE);
        g2d.setStroke(new java.awt.BasicStroke(2.0f));

        int plusSize = Config.GRAPH_BUTTON_SIZE - 2 * margin;
        int plusX = zoomInButtonX + margin;
        int plusY = y + margin;

        // Horizontal line
        g2d.drawLine(
                plusX + plusSize / 4,
                plusY + plusSize / 2,
                plusX + 3 * plusSize / 4,
                plusY + plusSize / 2
        );

        // Vertical line
        g2d.drawLine(
                plusX + plusSize / 2,
                plusY + plusSize / 4,
                plusX + plusSize / 2,
                plusY + 3 * plusSize / 4
        );

        // Draw zoom out (-) button
        int zoomOutButtonX = zoomInButtonX - Config.GRAPH_BUTTON_SIZE - Config.GRAPH_BUTTON_MARGIN;
        zoomOutButtonRect.setBounds(zoomOutButtonX, y, Config.GRAPH_BUTTON_SIZE, Config.GRAPH_BUTTON_SIZE);

        g2d.setColor(isOverZoomOutButton(p) ? Config.GRAPH_BUTTON_HOVER_COLOR : Config.GRAPH_BUTTON_COLOR);
        Shape zoomOutButtonShape = new RoundRectangle2D.Float(
                zoomOutButtonX, y,
                Config.GRAPH_BUTTON_SIZE, Config.GRAPH_BUTTON_SIZE,
                6, 6
        );
        g2d.fill(zoomOutButtonShape);

        // Draw - symbol
        g2d.setColor(java.awt.Color.WHITE);
        g2d.setStroke(new java.awt.BasicStroke(2.0f));

        int minusSize = Config.GRAPH_BUTTON_SIZE - 2 * margin;
        int minusX = zoomOutButtonX + margin;
        int minusY = y + margin;

        // Horizontal line (minus symbol)
        g2d.drawLine(
                minusX + minusSize / 4,
                minusY + minusSize / 2,
                minusX + 3 * minusSize / 4,
                minusY + minusSize / 2
        );

        // Draw Week button (wider than the others)
        int weekButtonX = zoomOutButtonX - textButtonWidth - Config.GRAPH_BUTTON_MARGIN;
        weekButtonRect.setBounds(weekButtonX, y, textButtonWidth, Config.GRAPH_BUTTON_SIZE);

        g2d.setColor(isOverWeekButton(p) ? Config.GRAPH_BUTTON_HOVER_COLOR : Config.GRAPH_BUTTON_COLOR);
        Shape weekButtonShape = new RoundRectangle2D.Float(
                weekButtonX, y,
                textButtonWidth, Config.GRAPH_BUTTON_SIZE,
                6, 6
        );
        g2d.fill(weekButtonShape);

        // Draw Week text
        g2d.setColor(java.awt.Color.WHITE);
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 12));
        FontMetrics fm = g2d.getFontMetrics();
        String weekText = "Week";
        int textWidth = fm.stringWidth(weekText);
        int textHeight = fm.getHeight();
        g2d.drawString(weekText,
                weekButtonX + (textButtonWidth - textWidth) / 2,
                y + (Config.GRAPH_BUTTON_SIZE + textHeight) / 2 - 2);

        // Draw Month button (wider than the others)
        int monthButtonX = weekButtonX - textButtonWidth - Config.GRAPH_BUTTON_MARGIN;
        monthButtonRect.setBounds(monthButtonX, y, textButtonWidth, Config.GRAPH_BUTTON_SIZE);

        g2d.setColor(isOverMonthButton(p) ? Config.GRAPH_BUTTON_HOVER_COLOR : Config.GRAPH_BUTTON_COLOR);
        Shape monthButtonShape = new RoundRectangle2D.Float(
                monthButtonX, y,
                textButtonWidth, Config.GRAPH_BUTTON_SIZE,
                6, 6
        );
        g2d.fill(monthButtonShape);

        // Draw Month text
        g2d.setColor(java.awt.Color.WHITE);
        // Use the same font as for the Week button - plain instead of bold
        String monthText = "Month";
        textWidth = fm.stringWidth(monthText);
        g2d.drawString(monthText,
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