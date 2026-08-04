package com.flippingcopilot.ui.graph;

import com.flippingcopilot.ui.graph.model.Bounds;
import com.flippingcopilot.ui.graph.model.Config;
import com.flippingcopilot.ui.graph.model.Constants;
import com.flippingcopilot.util.MathUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Getter
public class ZoomHandler {

    @RequiredArgsConstructor
    public static class ZoomPreset {
        public final String label;
        public final int spanSeconds;
        public final int horizonSeconds;
        public final Rectangle buttonRect = new Rectangle();
        public Bounds bounds;
    }

    private static final int MIN_TIME_DELTA = 60*60;
    private static final long MIN_PRICE_DELTA = 5;
    @Setter
    private Point selectionStart = null;
    @Setter
    private Point selectionEnd = null;
    private boolean isSelecting = false;

    private final Rectangle homeButtonRect = new Rectangle();
    private final Rectangle maxButtonRect = new Rectangle();
    private final Rectangle zoomInButtonRect = new Rectangle();
    private final Rectangle zoomOutButtonRect = new Rectangle();

    public final List<ZoomPreset> presets = Arrays.asList(
            new ZoomPreset("Month", 30 * Constants.DAY_SECONDS, 0),
            new ZoomPreset("Week", 7 * Constants.DAY_SECONDS, 0),
            new ZoomPreset("Day", Constants.DAY_SECONDS, 6 * Constants.HOUR_SECONDS),
            new ZoomPreset("8h", 8 * Constants.HOUR_SECONDS, 2 * Constants.HOUR_SECONDS));

    public Bounds maxViewBounds;
    public Bounds homeViewBounds;

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

        long newPriceMax = interpolatePrice(bounds, y1 - pa.y, pa.height);
        long newPriceMin = interpolatePrice(bounds, y2 - pa.y, pa.height);

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

    /**
     * Applies the view of whichever zoom button is under the point, if any.
     * Returns true when a button was hit (and the bounds were changed).
     */
    public boolean applyButtonView(Point p, Bounds bounds) {
        for (ZoomPreset preset : presets) {
            if (isOver(preset.buttonRect, p)) {
                copyBounds(bounds, preset.bounds);
                return true;
            }
        }
        if (isOver(homeButtonRect, p)) {
            copyBounds(bounds, homeViewBounds);
        } else if (isOver(maxButtonRect, p)) {
            copyBounds(bounds, maxViewBounds);
        } else if (isOver(zoomInButtonRect, p)) {
            applyZoomIn(bounds);
        } else if (isOver(zoomOutButtonRect, p)) {
            applyZoomOut(bounds);
        } else {
            return false;
        }
        return true;
    }

    private void applyZoomIn(Bounds bounds) {
        bounds.xMin = Math.min(bounds.xMax - MIN_TIME_DELTA, bounds.xMin + (int) (bounds.xDelta()*0.2));

    }

    private void applyZoomOut( Bounds bounds) {
        int td = bounds.xDelta();
        bounds.xMin= Math.max(maxViewBounds.xMin, bounds.xMin- (int) (td*0.2));
        bounds.xMax = Math.min(maxViewBounds.xMax, bounds.xMax + (int) (td*0.2));
        long pd = bounds.yDelta();
        bounds.yMin = Math.max(maxViewBounds.yMin, subtractSaturated(bounds.yMin, pd / 10));
        bounds.yMax = Math.min(maxViewBounds.yMax, addSaturated(bounds.yMax, pd / 10));
        bounds.y2Max = Math.min(maxViewBounds.y2Max, addSaturated(bounds.y2Max, pd / 20));
    }

    private long interpolatePrice(Bounds bounds, int pixelOffset, int pixelRange) {
        long quotient = bounds.yDelta() / pixelRange;
        long remainder = bounds.yDelta() % pixelRange;
        long offset = quotient * pixelOffset + (remainder * pixelOffset) / pixelRange;
        return bounds.yMax - offset;
    }

    private long addSaturated(long value, long delta) {
        return value > Long.MAX_VALUE - delta ? Long.MAX_VALUE : value + delta;
    }

    private long subtractSaturated(long value, long delta) {
        return value < Long.MIN_VALUE + delta ? Long.MIN_VALUE : value - delta;
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

    public void drawButtons(Graphics2D g2d, Rectangle pa, Point p) {
        int size = Config.GRAPH_BUTTON_SIZE;
        int x = pa.x + pa.width - size - Config.GRAPH_BUTTON_MARGIN;
        int y = pa.y + Config.GRAPH_BUTTON_MARGIN;

        // Draw home button
        drawButtonBackground(g2d, homeButtonRect, x, y, size, isOver(homeButtonRect, p));
        drawHomeIcon(g2d, homeButtonRect);

        // Draw max button
        x -= size + Config.GRAPH_BUTTON_MARGIN;
        drawButtonBackground(g2d, maxButtonRect, x, y, size, isOver(maxButtonRect, p));
        // Draw max icon (four outward arrows)
        drawMaxIcon(g2d, maxButtonRect);

        // Draw zoom in (+) button
        x -= size + Config.GRAPH_BUTTON_MARGIN;
        drawButtonBackground(g2d, zoomInButtonRect, x, y, size, isOver(zoomInButtonRect, p));
        // Draw + symbol
        drawPlusMinusIcon(g2d, zoomInButtonRect, true);

        // Draw zoom out (-) button
        x -= size + Config.GRAPH_BUTTON_MARGIN;
        drawButtonBackground(g2d, zoomOutButtonRect, x, y, size, isOver(zoomOutButtonRect, p));
        // Draw - symbol
        drawPlusMinusIcon(g2d, zoomOutButtonRect, false);

        // Draw the preset buttons (wider than the others), right to left so the longest span ends up leftmost
        int textButtonWidth = size * 2;
        for (int i = presets.size() - 1; i >= 0; i--) {
            ZoomPreset preset = presets.get(i);
            x -= textButtonWidth + Config.GRAPH_BUTTON_MARGIN;
            drawButtonBackground(g2d, preset.buttonRect, x, y, textButtonWidth, isOver(preset.buttonRect, p));
            drawCenteredText(g2d, preset.buttonRect, preset.label);
        }
    }

    private void drawButtonBackground(Graphics2D g2d, Rectangle rect, int x, int y, int width, boolean hovered) {
        rect.setBounds(x, y, width, Config.GRAPH_BUTTON_SIZE);
        g2d.setColor(hovered ? Config.GRAPH_BUTTON_HOVER_COLOR : Config.GRAPH_BUTTON_COLOR);
        g2d.fill(new RoundRectangle2D.Float(x, y, width, Config.GRAPH_BUTTON_SIZE, 6, 6));
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
}
