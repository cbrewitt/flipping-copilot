package copilot.ui.graph;
import static copilot.ui.graph.model.Config.*;

import copilot.ui.graph.model.*;
import copilot.ui.graph.model.Config;
import copilot.ui.graph.model.Constants;
import copilot.util.*;

import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;


public class ReferenceZoomHandler {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ReferenceZoomHandler.class);

    public static class ZoomPreset {
        public ZoomPreset(String label, int spanSeconds, int horizonSeconds) {
            this.label = label; this.spanSeconds = spanSeconds; this.horizonSeconds = horizonSeconds;
        }
        public final String label;
        public final int spanSeconds, horizonSeconds;
        public final Rectangle buttonRect = new Rectangle();
        public Bounds bounds;
    }

    private static final int MIN_TIME_DELTA = 60*60;
    private static final long MIN_PRICE_DELTA = 5;

    private Point selectionStart = null;

    private Point selectionEnd = null;
    private boolean isSelecting = false;

    private final Rectangle homeButtonRect = new Rectangle(), maxButtonRect = new Rectangle();
    private final Rectangle zoomInButtonRect = new Rectangle(), zoomOutButtonRect = new Rectangle();

    public final List<ZoomPreset> presets = Arrays.asList(
            new ZoomPreset("Month", 30 * Constants.DAY_SECONDS, 0),
            new ZoomPreset("Week", 7 * Constants.DAY_SECONDS, 0),
            new ZoomPreset("Day", Constants.DAY_SECONDS, 6 * Constants.HOUR_SECONDS),
            new ZoomPreset("8h", 8 * Constants.HOUR_SECONDS, 2 * Constants.HOUR_SECONDS));

    public Bounds maxViewBounds, homeViewBounds;

    public void startSelection(Point point) {
        selectionStart = new Point(point);
        selectionEnd = null;
        isSelecting = true;
    }

    public void applySelection(Rectangle pa, Bounds bounds) {
        if (selectionStart == null || selectionEnd == null) return;

        Rectangle selection = selectionBounds(pa);

        int newTimeMin = bounds.xMin + (int)(((long)bounds.xDelta() * (long)(selection.x - pa.x)) / ((long) pa.width));
        int newTimeMax = bounds.xMin + (int)(((long)bounds.xDelta() * (long)(selection.x + selection.width - pa.x)) / ((long) pa.width));

        long newPriceMax = interpolatePrice(bounds, selection.y - pa.y, pa.height);
        long newPriceMin = interpolatePrice(bounds, selection.y + selection.height - pa.y, pa.height);

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

        bounds.xMin = newTimeMin; bounds.xMax = newTimeMax; bounds.yMin = newPriceMin; bounds.yMax = newPriceMax;

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
        if (isOver(homeButtonRect, p)) { copyBounds(bounds, homeViewBounds); } else if (isOver(maxButtonRect, p)) {
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
        long quotient = bounds.yDelta() / pixelRange, remainder = bounds.yDelta() % pixelRange;
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
        target.xMin = source.xMin; target.xMax = source.xMax; target.yMin = source.yMin; target.yMax = source.yMax;
        target.y2Min = source.y2Min; target.y2Max = source.y2Max;
    }

    public void cancelSelection() {
        selectionStart = null;
        selectionEnd = null;
        isSelecting = false;
    }

    public void drawSelectionRectangle(Graphics2D graphics, Rectangle pa) {
        if (!isSelecting || selectionStart == null || selectionEnd == null) return;

        Rectangle selection = selectionBounds(pa);

        graphics.setColor(SELECTION_COLOR);
        graphics.fillRect(selection.x, selection.y, selection.width, selection.height);

        graphics.setColor(SELECTION_BORDER_COLOR); graphics.setStroke(SELECTION_STROKE);
        graphics.drawRect(selection.x, selection.y, selection.width, selection.height);
    }

    private Rectangle selectionBounds(Rectangle pa) {
        int x1 = MathUtil.clamp(Math.min(selectionStart.x, selectionEnd.x), pa.x, pa.x+pa.width);
        int y1 = MathUtil.clamp(Math.min(selectionStart.y, selectionEnd.y), pa.y, pa.y+pa.height);
        int x2 = MathUtil.clamp(Math.max(selectionStart.x, selectionEnd.x), pa.x, pa.x+pa.width);
        int y2 = MathUtil.clamp(Math.max(selectionStart.y, selectionEnd.y), pa.y, pa.y+pa.height);
        return new Rectangle(x1, y1, x2 - x1, y2 - y1);
    }

    public void drawButtons(Graphics2D graphics, Rectangle pa, Point p) {
        int size = GRAPH_BUTTON_SIZE, x = pa.x + pa.width - size - GRAPH_BUTTON_MARGIN;
        int y = pa.y + GRAPH_BUTTON_MARGIN;

        // Draw home button
        drawButtonBackground(graphics, homeButtonRect, x, y, size, isOver(homeButtonRect, p));
        drawHomeIcon(graphics, homeButtonRect);

        // Draw max button
        x -= size + GRAPH_BUTTON_MARGIN;
        drawButtonBackground(graphics, maxButtonRect, x, y, size, isOver(maxButtonRect, p));
        // Draw max icon (four outward arrows)
        drawMaxIcon(graphics, maxButtonRect);

        // Draw zoom in (+) button
        x -= size + GRAPH_BUTTON_MARGIN;
        drawButtonBackground(graphics, zoomInButtonRect, x, y, size, isOver(zoomInButtonRect, p));
        // Draw + symbol
        drawPlusMinusIcon(graphics, zoomInButtonRect, true);

        // Draw zoom out (-) button
        x -= size + GRAPH_BUTTON_MARGIN;
        drawButtonBackground(graphics, zoomOutButtonRect, x, y, size, isOver(zoomOutButtonRect, p));
        // Draw - symbol
        drawPlusMinusIcon(graphics, zoomOutButtonRect, false);

        // Draw the preset buttons (wider than the others), right to left so the longest span ends up leftmost
        int textButtonWidth = size * 2;
        for (int i = presets.size() - 1; i >= 0; i--) {
            var preset = presets.get(i);
            x -= textButtonWidth + GRAPH_BUTTON_MARGIN;
            drawButtonBackground(graphics, preset.buttonRect, x, y, textButtonWidth, isOver(preset.buttonRect, p));
            drawCenteredText(graphics, preset.buttonRect, preset.label);
        }
    }

    private void drawButtonBackground(Graphics2D graphics, Rectangle rect, int x, int y, int width, boolean hovered) {
        rect.setBounds(x, y, width, GRAPH_BUTTON_SIZE);
        graphics.setColor(hovered ? GRAPH_BUTTON_HOVER_COLOR : GRAPH_BUTTON_COLOR);
        graphics.fill(new RoundRectangle2D.Float(x, y, width, GRAPH_BUTTON_SIZE, 6, 6));
    }

    private void drawHomeIcon(Graphics2D graphics, Rectangle rect) {
        int margin = 4, houseX = rect.x + margin, houseY = rect.y + margin;
        int houseWidth = rect.width - 2 * margin, houseHeight = rect.height - 2 * margin;
        graphics.setColor(Color.WHITE); graphics.setStroke(new BasicStroke(1.5f));

        // Roof
        graphics.fillPolygon(
                new int[]{houseX, houseX + houseWidth / 2, houseX + houseWidth},
                new int[]{houseY + houseHeight / 2, houseY, houseY + houseHeight / 2},
                3);

        // House body
        graphics.fillRect(houseX + houseWidth / 5, houseY + houseHeight / 2, 3 * houseWidth / 5, houseHeight / 2);
    }

    private void drawMaxIcon(Graphics2D graphics, Rectangle rect) {
        int centerX = rect.x + rect.width / 2, centerY = rect.y + rect.height / 2;
        int arrowSize = rect.width / 2 - 7;
        graphics.setColor(Color.WHITE); graphics.setStroke(new BasicStroke(1.5f));

        // Draw a simple expand icon (four outward arrows)
        // Top-left arrow
        // Top-right arrow
        // Bottom-left arrow
        // Bottom-right arrow
        for (int sx : new int[]{-1, 1}) {
            for (int sy : new int[]{-1, 1}) {
                int endX = centerX + sx * arrowSize, endY = centerY + sy * arrowSize;
                graphics.drawLine(centerX + sx * 2, centerY + sy * 2, endX, endY);
                graphics.drawLine(endX, centerY + sy * 2, endX, endY);
                graphics.drawLine(centerX + sx * 2, endY, endX, endY);
            }
        }
    }

    private void drawPlusMinusIcon(Graphics2D graphics, Rectangle rect, boolean plus) {
        int margin = 4, iconSize = rect.width - 2 * margin, x = rect.x + margin, y = rect.y + margin;
        graphics.setColor(Color.WHITE); graphics.setStroke(new BasicStroke(2.0f));

        // Horizontal line (minus symbol)
        graphics.drawLine(x + iconSize / 4, y + iconSize / 2, x + 3 * iconSize / 4, y + iconSize / 2);
        if (plus) {
            // Vertical line
            graphics.drawLine(x + iconSize / 2, y + iconSize / 4, x + iconSize / 2, y + 3 * iconSize / 4);
        }
    }

    private void drawCenteredText(Graphics2D graphics, Rectangle rect, String text) {
        graphics.setColor(Color.WHITE);
        // Use the same font as for the Week button - plain instead of bold
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 12));
        var fm = graphics.getFontMetrics();
        graphics.drawString(text,
                rect.x + (rect.width - fm.stringWidth(text)) / 2,
                rect.y + (rect.height + fm.getHeight()) / 2 - 2);
    }

    private boolean isOver(Rectangle rect, Point point) { return point != null && rect.contains(point); }
}
