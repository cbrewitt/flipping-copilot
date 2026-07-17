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
    private final Rectangle dayButtonRect = new Rectangle();
    private final Rectangle eightHourButtonRect = new Rectangle();
    private final Rectangle emaButtonRect = new Rectangle();
    private final Rectangle bbButtonRect = new Rectangle();

    public Bounds maxViewBounds;
    public Bounds homeViewBounds;
    public Bounds weekViewBounds;
    public Bounds monthViewBounds;
    public Bounds dayViewBounds;
    public Bounds eightHourViewBounds;

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
        copyBounds(bounds, homeViewBounds);
    }

    public void applyMaxView(Bounds bounds) {
        copyBounds(bounds, maxViewBounds);
    }

    public void applyWeekView(Bounds bounds) {
        copyBounds(bounds, weekViewBounds);
    }

    public void applyMonthView(Bounds bounds) {
        copyBounds(bounds, monthViewBounds);
    }

    public void applyDayView(Bounds bounds) {
        copyBounds(bounds, dayViewBounds);
    }

    public void applyEightHourView(Bounds bounds) {
        copyBounds(bounds, eightHourViewBounds);
    }

    private void copyBounds(Bounds target, Bounds source) {
        target.xMin = source.xMin;
        target.xMax = source.xMax;
        target.yMin = source.yMin;
        target.yMax = source.yMax;
        target.y2Min = source.y2Min;
        target.y2Max = source.y2Max;
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

    public void drawButtons(Graphics2D g2d, Rectangle pa, Point p, Config config) {
        int size = Config.GRAPH_BUTTON_SIZE;
        int x = pa.x + pa.width - size - Config.GRAPH_BUTTON_MARGIN;
        int y = pa.y + Config.GRAPH_BUTTON_MARGIN;

        // Draw home button
        drawButtonBackground(g2d, homeButtonRect, x, y, size, isOverHomeButton(p));
        drawHomeIcon(g2d, homeButtonRect);

        // Draw max button
        x -= size + Config.GRAPH_BUTTON_MARGIN;
        drawButtonBackground(g2d, maxButtonRect, x, y, size, isOverMaxButton(p));
        // Draw max icon (four outward arrows)
        drawMaxIcon(g2d, maxButtonRect);

        // Draw zoom in (+) button
        x -= size + Config.GRAPH_BUTTON_MARGIN;
        drawButtonBackground(g2d, zoomInButtonRect, x, y, size, isOverZoomInButton(p));
        // Draw + symbol
        drawPlusMinusIcon(g2d, zoomInButtonRect, true);

        // Draw zoom out (-) button
        x -= size + Config.GRAPH_BUTTON_MARGIN;
        drawButtonBackground(g2d, zoomOutButtonRect, x, y, size, isOverZoomOutButton(p));
        // Draw - symbol
        drawPlusMinusIcon(g2d, zoomOutButtonRect, false);

        // Width for text buttons (8h, Day, Week, Month)
        int textButtonWidth = size * 2;

        // Text buttons ordered by span, shortest (8h) rightmost: left-to-right Month, Week, Day, 8h.
        // Draw 8h button (rightmost of the text buttons)
        x -= textButtonWidth + Config.GRAPH_BUTTON_MARGIN;
        drawButtonBackground(g2d, eightHourButtonRect, x, y, textButtonWidth, isOverEightHourButton(p));
        // Draw 8h text
        drawCenteredText(g2d, eightHourButtonRect, "8h");

        // Draw Day button
        x -= textButtonWidth + Config.GRAPH_BUTTON_MARGIN;
        drawButtonBackground(g2d, dayButtonRect, x, y, textButtonWidth, isOverDayButton(p));
        // Draw Day text
        drawCenteredText(g2d, dayButtonRect, "Day");

        // Draw Week button (wider than the others)
        x -= textButtonWidth + Config.GRAPH_BUTTON_MARGIN;
        drawButtonBackground(g2d, weekButtonRect, x, y, textButtonWidth, isOverWeekButton(p));
        // Draw Week text
        drawCenteredText(g2d, weekButtonRect, "Week");

        // Draw Month button (wider than the others)
        x -= textButtonWidth + Config.GRAPH_BUTTON_MARGIN;
        drawButtonBackground(g2d, monthButtonRect, x, y, textButtonWidth, isOverMonthButton(p));
        // Draw Month text
        drawCenteredText(g2d, monthButtonRect, "Month");

        // Second row: indicator toggle buttons, right-aligned beneath the zoom row.
        // Drawn right-to-left so they read left-to-right as EMA, BB.
        int y2 = pa.y + Config.GRAPH_BUTTON_MARGIN + size + Config.GRAPH_BUTTON_MARGIN;
        int x2 = pa.x + pa.width - textButtonWidth - Config.GRAPH_BUTTON_MARGIN;
        drawToggleButtonBackground(g2d, bbButtonRect, x2, y2, textButtonWidth, isOverBbButton(p), config.isShowBollinger());
        drawCenteredText(g2d, bbButtonRect, "BB");

        x2 -= textButtonWidth + Config.GRAPH_BUTTON_MARGIN;
        drawToggleButtonBackground(g2d, emaButtonRect, x2, y2, textButtonWidth, isOverEmaButton(p), config.isShowEma());
        drawCenteredText(g2d, emaButtonRect, "EMA");
    }

    private void drawButtonBackground(Graphics2D g2d, Rectangle rect, int x, int y, int width, boolean hovered) {
        rect.setBounds(x, y, width, Config.GRAPH_BUTTON_SIZE);
        g2d.setColor(hovered ? Config.GRAPH_BUTTON_HOVER_COLOR : Config.GRAPH_BUTTON_COLOR);
        g2d.fill(new RoundRectangle2D.Float(x, y, width, Config.GRAPH_BUTTON_SIZE, 6, 6));
    }

    private void drawToggleButtonBackground(Graphics2D g2d, Rectangle rect, int x, int y, int width, boolean hovered, boolean active) {
        rect.setBounds(x, y, width, Config.GRAPH_BUTTON_SIZE);
        g2d.setColor(active || hovered ? Config.GRAPH_BUTTON_HOVER_COLOR : Config.GRAPH_BUTTON_COLOR);
        g2d.fill(new RoundRectangle2D.Float(x, y, width, Config.GRAPH_BUTTON_SIZE, 6, 6));
        if (active) {
            g2d.setColor(Color.WHITE);
            g2d.setStroke(new BasicStroke(1f));
            g2d.draw(new RoundRectangle2D.Float(x, y, width, Config.GRAPH_BUTTON_SIZE, 6, 6));
        }
    }

    private void drawHomeIcon(Graphics2D g2d, Rectangle rect) {
        int margin = 4;
        int houseX = rect.x + margin;
        int houseY = rect.y + margin;
        int houseWidth = rect.width - 2 * margin;
        int houseHeight = rect.height - 2 * margin;
        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke(1.5f));

        // Roof
        g2d.fillPolygon(
                new int[]{houseX, houseX + houseWidth / 2, houseX + houseWidth},
                new int[]{houseY + houseHeight / 2, houseY, houseY + houseHeight / 2},
                3);

        // House body
        g2d.fillRect(houseX + houseWidth / 5, houseY + houseHeight / 2,
                3 * houseWidth / 5, houseHeight / 2);
    }

    private void drawMaxIcon(Graphics2D g2d, Rectangle rect) {
        int centerX = rect.x + rect.width / 2;
        int centerY = rect.y + rect.height / 2;
        int arrowSize = rect.width / 2 - 7;
        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke(1.5f));

        // Draw a simple expand icon (four outward arrows)
        // Top-left arrow
        // Top-right arrow
        // Bottom-left arrow
        // Bottom-right arrow
        for (int sx : new int[]{-1, 1}) {
            for (int sy : new int[]{-1, 1}) {
                int endX = centerX + sx * arrowSize;
                int endY = centerY + sy * arrowSize;
                g2d.drawLine(centerX + sx * 2, centerY + sy * 2, endX, endY);
                g2d.drawLine(endX, centerY + sy * 2, endX, endY);
                g2d.drawLine(centerX + sx * 2, endY, endX, endY);
            }
        }
    }

    private void drawPlusMinusIcon(Graphics2D g2d, Rectangle rect, boolean plus) {
        int margin = 4;
        int iconSize = rect.width - 2 * margin;
        int x = rect.x + margin;
        int y = rect.y + margin;
        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke(2.0f));

        // Horizontal line (minus symbol)
        g2d.drawLine(x + iconSize / 4, y + iconSize / 2, x + 3 * iconSize / 4, y + iconSize / 2);
        if (plus) {
            // Vertical line
            g2d.drawLine(x + iconSize / 2, y + iconSize / 4, x + iconSize / 2, y + 3 * iconSize / 4);
        }
    }

    private void drawCenteredText(Graphics2D g2d, Rectangle rect, String text) {
        g2d.setColor(Color.WHITE);
        // Use the same font as for the Week button - plain instead of bold
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 12));
        FontMetrics fm = g2d.getFontMetrics();
        g2d.drawString(text,
                rect.x + (rect.width - fm.stringWidth(text)) / 2,
                rect.y + (rect.height + fm.getHeight()) / 2 - 2);
    }

    private boolean isOver(Rectangle rect, Point point) {
        return point != null && rect.contains(point);
    }

    public boolean isOverHomeButton(Point point) {
        return isOver(homeButtonRect, point);
    }

    public boolean isOverMaxButton(Point point) {
        return isOver(maxButtonRect, point);
    }

    public boolean isOverZoomInButton(Point point) {
        return isOver(zoomInButtonRect, point);
    }

    public boolean isOverZoomOutButton(Point point) {
        return isOver(zoomOutButtonRect, point);
    }

    public boolean isOverWeekButton(Point point) {
        return isOver(weekButtonRect, point);
    }

    public boolean isOverMonthButton(Point point) {
        return isOver(monthButtonRect, point);
    }

    public boolean isOverDayButton(Point point) {
        return isOver(dayButtonRect, point);
    }

    public boolean isOverEightHourButton(Point point) {
        return isOver(eightHourButtonRect, point);
    }

    public boolean isOverEmaButton(Point point) {
        return isOver(emaButtonRect, point);
    }

    public boolean isOverBbButton(Point point) {
        return isOver(bbButtonRect, point);
    }
}
