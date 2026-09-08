package copilot.ui.flipsdialog;

import java.awt.event.*;
import copilot.ui.graph.model.*;
import lombok.extern.slf4j.*;

import javax.swing.*;
import java.awt.*;
import java.time.*;
import java.util.*;
import java.util.List;

@Slf4j
public class ProfitGraphPanel extends JPanel {

    // Layout constants (base values that will be scaled)
    private static final int PADDING_LEFT = 65, PADDING_RIGHT = 30, PADDING_TOP = 40, PADDING_BOTTOM = 40;

    // Point size for data points
    private static final int POINT_RADIUS = 3;

    // Visual constants
    private static final Color BACKGROUND_COLOR = new Color(43, 43, 43);
    private static final Color PLOT_AREA_COLOR = new Color(51, 51, 51);
    private static final Color GRID_COLOR = new Color(85, 85, 85, 90), AXIS_COLOR = new Color(150, 150, 150);
    private static final Color TEXT_COLOR = new Color(225, 225, 225);

    // Scaled strokes
    private static final Stroke LINE_STROKE = new BasicStroke(2f);
    private static final Stroke GRID_STROKE = new BasicStroke(
            1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{3f}, 0
    );
    private static final Stroke AXIS_STROKE = new BasicStroke(1.0f);

    private final Color lossColor, profitColor;

    // Calculated bounds
    private List<Datapoint> data;
    private long minYValue, maxYValue;
    private long minY2Value, maxY2Value;
    private int minXValue, maxXValue;
    private Rectangle upperPa, lowerPa;
    private Bounds lowerPlotBounds, upperPlotBounds;

    public void setData(List<Datapoint> newData) {
        data = newData;
        minYValue = 0;
        maxYValue = 1000_000;
        minY2Value = 0;
        maxY2Value = 10_000;
        minXValue = Integer.MAX_VALUE;
        maxXValue = Integer.MIN_VALUE;
        for (Datapoint i : data) {
            if (i.cumulativeProfit < minYValue) { minYValue = i.cumulativeProfit; } else if (i.cumulativeProfit > maxYValue) {
                maxYValue = i.cumulativeProfit;
            }
            if (i.dailyProfit < minY2Value) { minY2Value = i.dailyProfit; } else if (i.dailyProfit > maxY2Value) {
                maxY2Value = i.dailyProfit;
            }
            if(i.timestamp() < minXValue) { minXValue = (int) i.timestamp(); }
            if(i.timestamp() > maxXValue) { maxXValue = (int) i.timestamp(); }
        }
        maxXValue += AxisCalculator.DAY_SECONDS / 2;
        minXValue -= AxisCalculator.DAY_SECONDS / 2;
    }

    public ProfitGraphPanel(Color profitColor, Color lossColor) {
        data = new ArrayList<>(); this.profitColor = profitColor; this.lossColor = lossColor;

        setBackground(BACKGROUND_COLOR);
        setPreferredSize(new Dimension(600, 400));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Point p = e.getPoint();
                Datapoint winner = null;
                int winnerDist = Integer.MAX_VALUE;
                boolean rePaintNeeded = false;
                if(upperPlotBounds != null && upperPa != null && lowerPa != null && (upperPa.contains(p) || lowerPa.contains(p))){
                    // find the closest by x-axis only (could use binary search but not worth the hassle)
                    for (Datapoint dp : data) {
                        int dist = Math.abs(upperPlotBounds.toX(upperPa, dp.timestamp()) - p.x);
                        dp.isCumulativeProfitHovered = false;
                        if(dist < winnerDist) {
                            winnerDist = dist;
                            winner = dp;
                        }
                    }
                    for (Datapoint dp : data) {
                        boolean prevIsCumulativeProfitHovered = dp.isCumulativeProfitHovered;
                        boolean prevIsDailyProfitHovered = dp.isDailyProfitHovered;
                        dp.isCumulativeProfitHovered = (winner == dp) && (upperPa.contains(p));
                        dp.isDailyProfitHovered = (winner == dp) && (lowerPa.contains(p));
                        if (prevIsDailyProfitHovered != dp.isDailyProfitHovered || prevIsCumulativeProfitHovered != dp.isCumulativeProfitHovered) {
                            rePaintNeeded = true;
                        }
                    }

                } else {
                    for (Datapoint d : data) {
                        if (d.isDailyProfitHovered) {
                            d.isDailyProfitHovered = false;
                            rePaintNeeded = true;
                        }
                        if (d.isCumulativeProfitHovered) {
                            d.isCumulativeProfitHovered = false;
                            rePaintNeeded = true;
                        }
                    }
                }
                if(rePaintNeeded) { repaint(); }
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D graphics = (Graphics2D) g;
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int width = getWidth(), height = getHeight(), paWidth = width - PADDING_LEFT - PADDING_RIGHT;
        int availablePaHeight = height -2*PADDING_TOP - PADDING_BOTTOM;
        int upperPaHeight = (int) (0.7*availablePaHeight), lowerPaHeight = availablePaHeight - upperPaHeight;
        
        upperPa = new Rectangle(PADDING_LEFT, PADDING_TOP, paWidth, upperPaHeight);
        lowerPa = new Rectangle(PADDING_LEFT, PADDING_TOP + upperPaHeight+ PADDING_BOTTOM, paWidth, lowerPaHeight);

        graphics.setColor(PLOT_AREA_COLOR);
        graphics.fillRect(upperPa.x, upperPa.y, upperPa.width, upperPa.height);
        graphics.fillRect(lowerPa.x, lowerPa.y, lowerPa.width, lowerPa.height);

        var yTicks = AxisCalculator.calculateYTicks(minYValue, maxYValue, 11);
        upperPlotBounds = new Bounds();
        upperPlotBounds.yMin = yTicks.get(0).value; upperPlotBounds.yMax = yTicks.get(yTicks.size()-1).value;
        upperPlotBounds.xMin = minXValue; upperPlotBounds.xMax = maxXValue;
        var xTicks = AxisCalculator.calculateXTicks(minXValue, maxXValue);

        drawGrid(graphics, upperPa, xTicks, yTicks, upperPlotBounds);
        drawYAxisLabels(graphics, upperPa, yTicks, upperPlotBounds);
        drawTitle(graphics, upperPa, "Cumulative profit over time");

        if (!data.isEmpty()) { drawProfitLine(graphics, upperPa, upperPlotBounds); }
        
        var yTicks2 = AxisCalculator.calculateYTicks(minY2Value, maxY2Value,6);
        lowerPlotBounds = upperPlotBounds.copy();
        lowerPlotBounds.yMin = yTicks2.get(0).value; lowerPlotBounds.yMax = yTicks2.get(yTicks2.size()-1).value;

        drawGrid(graphics, lowerPa, xTicks, yTicks2, lowerPlotBounds);
        drawXAxisLabels(graphics, lowerPa, xTicks,  lowerPlotBounds);
        drawYAxisLabels(graphics, lowerPa, yTicks2, lowerPlotBounds);
        drawTitle(graphics, lowerPa, "Daily profit/loss");
        if (!data.isEmpty()) { drawDailyProfitBars(graphics, lowerPlotBounds); }
    }

    private void drawTitle(Graphics2D graphics, Rectangle pa, String text) {
        // Use a slightly larger font for the title
        graphics.setFont(Font.getFont(Font.MONOSPACED)); graphics.setColor(TEXT_COLOR);
        var fm = graphics.getFontMetrics();
        int textWidth = fm.stringWidth(text), x = pa.x + (pa.width - textWidth) / 2, y = pa.y - 5;
        graphics.drawString(text, x, y);
    }

    private void drawGrid(Graphics2D graphics, Rectangle pa, List<Tick> xTicks, List<Tick> yTicks, Bounds bounds) {
        graphics.setColor(GRID_COLOR); graphics.setStroke(GRID_STROKE);
        for (Tick t : xTicks) {
            int x = bounds.toX(pa, (int) t.value);
            graphics.drawLine(x, pa.y, x, pa.y + pa.height);
        }
        for (Tick t : yTicks) {
            int y = bounds.toY(pa, t.value);
            graphics.drawLine(pa.x, y, pa.x + pa.width, y);
        }
        graphics.setColor(AXIS_COLOR); graphics.setStroke(AXIS_STROKE);
        graphics.drawLine(pa.x, pa.y, pa.x, pa.y + pa.height);
    }

    private void drawProfitLine(Graphics2D graphics, Rectangle pa, Bounds bounds) {
        if (data.size() < 2) {
            // Just draw a point if we only have one data point
            if (data.size() == 1) {
                var point = data.get(0);
                int x = bounds.toX(pa, point.timestamp()), y = bounds.toY(pa, point.cumulativeProfit);

                graphics.setColor(point.cumulativeProfit >= 0 ? profitColor : lossColor);
                graphics.fillOval(x - POINT_RADIUS, y - POINT_RADIUS, POINT_RADIUS * 2, POINT_RADIUS * 2);
            }
            return;
        }

        graphics.setStroke(LINE_STROKE);

        // Draw segment with appropriate color
        for (int i = 1; i < data.size(); i++) {
            var point = data.get(i);
            int x = bounds.toX(pa, point.timestamp()), y = bounds.toY(pa, point.cumulativeProfit);
            var prevPoint = data.get(i - 1);
            int prevX = bounds.toX(pa, (int) prevPoint.timestamp());
            int prevY = bounds.toY(pa, prevPoint.cumulativeProfit);

            // Determine color based on whether we're above or below zero
            boolean currentPositive = point.cumulativeProfit >= 0;
            boolean prevPositive = prevPoint.cumulativeProfit >= 0;

            if (currentPositive == prevPositive) {
                // Same sign, simple line
                graphics.setColor(currentPositive ? profitColor : lossColor);
                graphics.drawLine(prevX, prevY, x, y);
            } else {
                // Crossing zero, need to interpolate
                double ratio = Math.abs((double)prevPoint.cumulativeProfit) /
                        (Math.abs(prevPoint.cumulativeProfit) + Math.abs(point.cumulativeProfit));
                int crossX = prevX + (int)((x - prevX) * ratio), crossY = bounds.toY(pa, 0);
                // Draw first segment
                graphics.setColor(prevPositive ? profitColor : lossColor);
                graphics.drawLine(prevX, prevY, crossX, crossY);
                // Draw second segment
                graphics.setColor(currentPositive ? profitColor : lossColor);
                graphics.drawLine(crossX, crossY, x, y);
            }
        }

        // Draw points
        Runnable drawToolTip = () -> {};
        graphics.setStroke(AXIS_STROKE);
        for (Datapoint dp : data) {
            int x = bounds.toX(pa, dp.timestamp()), y = bounds.toY(pa, dp.cumulativeProfit);

            graphics.setColor(dp.cumulativeProfit >= 0 ? profitColor : lossColor);
            graphics.fillOval(x - POINT_RADIUS, y - POINT_RADIUS, POINT_RADIUS * 2, POINT_RADIUS * 2);
            if(dp.isCumulativeProfitHovered) {
                drawToolTip = () -> {
                    graphics.setColor(Color.WHITE);
                    graphics.drawOval(x - POINT_RADIUS, y - POINT_RADIUS, POINT_RADIUS * 2, POINT_RADIUS * 2);
                    var p = new Point(bounds.toX(upperPa, dp.timestamp()), bounds.toY(upperPa, dp.cumulativeProfit));
                    drawToolTip(dp.t, dp.cumulativeProfit, graphics, p);
                };
            }
        }
        drawToolTip.run(); // draw tool tip after so it's on top
    }

    private void drawXAxisLabels(Graphics2D graphics, Rectangle pa, List<Tick> xTicks, Bounds bounds) {
        graphics.setColor(TEXT_COLOR);
        var fm = graphics.getFontMetrics();
        for (Tick t : xTicks) {
            int x = bounds.toX(pa, (int) t.value), labelWidth = fm.stringWidth(t.label);
            graphics.drawString(t.label, x - labelWidth / 2,  pa.y + pa.height + 20);
        }
    }
    private void drawYAxisLabels(Graphics2D graphics, Rectangle pa,  List<Tick> yTicks, Bounds bounds) {
        graphics.setColor(TEXT_COLOR);
        var boldFm = graphics.getFontMetrics();
        for (Tick t : yTicks) {
            int y = bounds.toY(pa, t.value), labelWidth = boldFm.stringWidth(t.label);
            graphics.drawString(t.label, PADDING_LEFT - labelWidth - 10, y + boldFm.getHeight() / 3);
        }
    }

    private void drawDailyProfitBars(Graphics2D graphics,  Bounds bounds) {
        if (data.isEmpty()) { return; }
        Runnable drawToolTip = () -> {};
        for (Datapoint dp : data) {
            Color barColor = dp.dailyProfit >= 0 ? profitColor : lossColor;
            graphics.setColor(new Color(barColor.getRed(), barColor.getGreen(), barColor.getBlue(), 180)); // 70% opacity
            Rectangle bar = profitBarRect(dp, bounds);
            graphics.fillRect(bar.x, bar.y, bar.width, bar.height);
            graphics.setColor(new Color(barColor.getRed(), barColor.getGreen(), barColor.getBlue()));
            graphics.drawRect(bar.x, bar.y, bar.width, bar.height);
            if(dp.isDailyProfitHovered) {
                drawToolTip = () -> {
                    graphics.setColor(Color.WHITE);
                    graphics.drawRect(bar.x, bar.y, bar.width, bar.height);
                    var p = new Point(bounds.toX(lowerPa, dp.timestamp()), bounds.toY(lowerPa, dp.dailyProfit));
                    drawToolTip(dp.t, dp.dailyProfit, graphics, p);
                };
            }
        }
        drawToolTip.run(); // draw tool tip after so it's on top
    }

    private Rectangle profitBarRect(Datapoint dp,  Bounds bounds) {
        int x1 = bounds.toX(lowerPa, dp.timestamp()- AxisCalculator.DAY_SECONDS / 2);
        int x2 = bounds.toX(lowerPa, dp.timestamp() + AxisCalculator.DAY_SECONDS / 2);
        int y1 =  bounds.toY(lowerPa, dp.dailyProfit), y2 = bounds.toY(lowerPa, 0);
        if (dp.dailyProfit >= 0) { return new Rectangle(x1, y1, x2-x1, y2-y1); } else {
            return new Rectangle(x1, y2, x2-x1, y1-y2);
        }
    }

    private void drawToolTip(LocalDate t, long v, Graphics2D graphics, Point p) {
        // Tooltip styling local to this panel - deliberately not the graph Config colours
        final var TOOLTIP_BACKGROUND = new Color(43, 43, 43);
        final var TOOLTIP_BORDER = new Color(150, 150, 150);
        final int TOOLTIP_PADDING = 8; // Don't scale

        // Prepare tooltip text
        String dailyProfitStr = String.format("%,d", v);
        String dateStr = t.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"));

        var fm = graphics.getFontMetrics();

        int dateWidth = fm.stringWidth(dateStr), profitWidth = fm.stringWidth(dailyProfitStr);
        int textWidth = Math.max(dateWidth, profitWidth);
        int textHeight = fm.getHeight() * 2; // Two lines of text

        int tooltipWidth = textWidth + TOOLTIP_PADDING * 2, tooltipHeight = textHeight + TOOLTIP_PADDING * 2;

        int tooltipX = p.x, tooltipY = p.y;

        if (tooltipX < lowerPa.x) { tooltipX = lowerPa.x + 5; } else if (tooltipX + tooltipWidth > lowerPa.x + lowerPa.width) {
            tooltipX = lowerPa.x + lowerPa.width - tooltipWidth - 5;
        }
        graphics.setColor(TOOLTIP_BACKGROUND);
        graphics.fillRoundRect(tooltipX, tooltipY, tooltipWidth, tooltipHeight, 8, 8);

        graphics.setColor(TOOLTIP_BORDER); graphics.setStroke(new BasicStroke(1.0f));
        graphics.drawRoundRect(tooltipX, tooltipY, tooltipWidth, tooltipHeight, 8, 8);
        graphics.setColor(TEXT_COLOR);
        int yPos = tooltipY + TOOLTIP_PADDING + fm.getAscent();
        graphics.drawString(dailyProfitStr, tooltipX + TOOLTIP_PADDING, yPos);
        yPos += fm.getHeight();
        graphics.drawString(dateStr, tooltipX + TOOLTIP_PADDING, yPos);
    }
}
