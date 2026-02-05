package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.ui.graph.model.Bounds;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.time.LocalDate;
import java.util.*;
import java.util.List;

@Slf4j
public class ProfitGraphPanel extends JPanel {

    // Layout constants (base values that will be scaled)
    private static final int BASE_PADDING_LEFT = 65;
    private static final int BASE_PADDING_RIGHT = 30;
    private static final int BASE_PADDING_TOP = 40;
    private static final int BASE_PADDING_BOTTOM = 40;

    // Scaled layout values
    private final int PADDING_LEFT = BASE_PADDING_LEFT;
    private final int PADDING_RIGHT = BASE_PADDING_RIGHT;
    private final int PADDING_TOP = BASE_PADDING_TOP;
    private final int PADDING_BOTTOM = BASE_PADDING_BOTTOM;

    // Visual constants
    private static final Color BACKGROUND_COLOR = new Color(43, 43, 43);
    private static final Color PLOT_AREA_COLOR = new Color(51, 51, 51);
    private static final Color GRID_COLOR = new Color(85, 85, 85, 90);
    private static final Color AXIS_COLOR = new Color(150, 150, 150);
    private static final Color TEXT_COLOR = new Color(225, 225, 225);

    // Scaled strokes
    private final Stroke LINE_STROKE = new BasicStroke(2f);
    private final Stroke GRID_STROKE = new BasicStroke(
            1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{3f}, 0
    );
    private final Stroke AXIS_STROKE = new BasicStroke(1.0f);

    private final Color lossColor;
    private final Color profitColor;

    // Point size for data points
    private final int POINT_RADIUS = 3;

    // Calculated bounds
    private List<Datapoint> data;
    private long minYValue;
    private long maxYValue;
    private long minY2Value;
    private long maxY2Value;
    private int minXValue;
    private int maxXValue;
    private Rectangle upperPa;
    private Rectangle lowerPa;
    private Bounds lowerPlotBounds;
    private Bounds upperPlotBounds;
    

    public void setData(List<Datapoint> newData) {
        this.data = newData;
        minYValue = 0;
        maxYValue = 1000_000;
        minY2Value = 0;
        maxY2Value = 10_000;
        minXValue = Integer.MAX_VALUE;
        maxXValue = Integer.MIN_VALUE;
        for (Datapoint i : data) {
            if (i.cumulativeProfit < minYValue) {
                minYValue = i.cumulativeProfit;
            } else if (i.cumulativeProfit > maxYValue) {
                maxYValue = i.cumulativeProfit;
            }
            if (i.dailyProfit < minY2Value) {
                minY2Value = i.dailyProfit;
            } else if (i.dailyProfit > maxY2Value) {
                maxY2Value = i.dailyProfit;
            }
            if(i.timestamp() < minXValue) {
                minXValue = (int) i.timestamp();
            }
            if(i.timestamp() > maxXValue) {
                maxXValue = (int) i.timestamp();
            }
        }
        maxXValue += AxisCalculator.DAY_SECONDS / 2;
        minXValue -= AxisCalculator.DAY_SECONDS / 2;
    }

    public ProfitGraphPanel(Color profitColor, Color lossColor) {
        this.data = new ArrayList<>();
        this.profitColor = profitColor;
        this.lossColor = lossColor;

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
                if(rePaintNeeded) {
                    repaint();
                }
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        
        int width = getWidth();
        int height = getHeight();
        int paWidth = width - PADDING_LEFT - PADDING_RIGHT;
        int availablePaHeight = height -2*PADDING_TOP - PADDING_BOTTOM;
        int upperPaHeight = (int) (0.7*availablePaHeight);
        int lowerPaHeight = availablePaHeight - upperPaHeight;
        
        upperPa = new Rectangle(PADDING_LEFT, PADDING_TOP, paWidth, upperPaHeight);
        lowerPa = new Rectangle(PADDING_LEFT, PADDING_TOP + upperPaHeight+ PADDING_BOTTOM, paWidth, lowerPaHeight);
        
        
        g2.setColor(PLOT_AREA_COLOR);
        g2.fillRect(upperPa.x, upperPa.y, upperPa.width, upperPa.height);
        g2.fillRect(lowerPa.x, lowerPa.y, lowerPa.width, lowerPa.height);

        List<Tick> yTicks = AxisCalculator.calculateYTicks(minYValue, maxYValue, 11);
        upperPlotBounds = new Bounds();
        upperPlotBounds.yMin = yTicks.get(0).value;
        upperPlotBounds.yMax = yTicks.get(yTicks.size()-1).value;
        upperPlotBounds.xMin = minXValue;
        upperPlotBounds.xMax = maxXValue;
        List<Tick> xTicks = AxisCalculator.calculateXTicks(minXValue, maxXValue);

        drawGrid(g2, upperPa, xTicks, yTicks, upperPlotBounds);
        drawYAxisLabels(g2, upperPa, yTicks, upperPlotBounds);
        drawTitle(g2, upperPa, "Cumulative profit over time");

        if (!data.isEmpty()) {
            drawProfitLine(g2, upperPa, upperPlotBounds);
        }
        
        List<Tick> yTicks2 = AxisCalculator.calculateYTicks(minY2Value, maxY2Value,6);
        lowerPlotBounds = upperPlotBounds.copy();
        lowerPlotBounds.yMin = yTicks2.get(0).value;
        lowerPlotBounds.yMax = yTicks2.get(yTicks2.size()-1).value;

        drawGrid(g2, lowerPa, xTicks, yTicks2, lowerPlotBounds);
        drawXAxisLabels(g2, lowerPa, xTicks,  lowerPlotBounds);
        drawYAxisLabels(g2, lowerPa, yTicks2, lowerPlotBounds);
        drawTitle(g2, lowerPa, "Daily profit/loss");
        if (!data.isEmpty()) {
            drawDailyProfitBars(g2, lowerPlotBounds);
        }
    }

    private void drawTitle(Graphics2D g2, Rectangle pa, String text) {
        // Use a slightly larger font for the title
        g2.setFont(Font.getFont(Font.MONOSPACED));
        g2.setColor(TEXT_COLOR);
        FontMetrics fm = g2.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int x = pa.x + (pa.width - textWidth) / 2;
        int y = pa.y - 5;
        g2.drawString(text, x, y);
    }

    private void drawGrid(Graphics2D g2, Rectangle pa, List<Tick> xTicks, List<Tick> yTicks, Bounds bounds) {
        g2.setColor(GRID_COLOR);
        g2.setStroke(GRID_STROKE);
        for (Tick t : xTicks) {
            int x = bounds.toX(pa, (int) t.value);
            g2.drawLine(x, pa.y, x, pa.y + pa.height);
        }
        for (Tick t : yTicks) {
            int y = bounds.toY(pa, t.value);
            g2.drawLine(pa.x, y, pa.x + pa.width, y);
        }
        g2.setColor(AXIS_COLOR);
        g2.setStroke(AXIS_STROKE);
//        int y = bounds.toY(pa, 0);
//        g2.drawLine(pa.x, y, pa.x + pa.width, y);
        g2.drawLine(pa.x, pa.y, pa.x, pa.y + pa.height);
    }


    private void drawProfitLine(Graphics2D g2, Rectangle pa, Bounds bounds) {
        if (data.size() < 2) {
            // Just draw a point if we only have one data point
            if (data.size() == 1) {
                Datapoint point = data.get(0);
                int x = bounds.toX(pa, point.timestamp());
                int y = bounds.toY(pa, point.cumulativeProfit);

                g2.setColor(point.cumulativeProfit >= 0 ? profitColor : lossColor);
                g2.fillOval(x - POINT_RADIUS, y - POINT_RADIUS, POINT_RADIUS * 2, POINT_RADIUS * 2);
            }
            return;
        }

        g2.setStroke(LINE_STROKE);

        // Create the line path
        Path2D.Float path = new Path2D.Float();
        boolean started = false;

        for (int i = 0; i < data.size(); i++) {
            Datapoint point = data.get(i);
            int x = bounds.toX(pa, point.timestamp());
            int y = bounds.toY(pa, point.cumulativeProfit);

            if (!started) {
                path.moveTo(x, y);
                started = true;
            } else {
                // Draw segment with appropriate color
                Datapoint prevPoint = data.get(i - 1);
                int prevX = bounds.toX(pa, (int) prevPoint.timestamp());
                int prevY = bounds.toY(pa, prevPoint.cumulativeProfit);

                // Determine color based on whether we're above or below zero
                boolean currentPositive = point.cumulativeProfit >= 0;
                boolean prevPositive = prevPoint.cumulativeProfit >= 0;

                if (currentPositive == prevPositive) {
                    // Same sign, simple line
                    g2.setColor(currentPositive ? profitColor : lossColor);
                    g2.drawLine(prevX, prevY, x, y);
                } else {
                    // Crossing zero, need to interpolate
                    double ratio = Math.abs((double)prevPoint.cumulativeProfit) /
                            (Math.abs(prevPoint.cumulativeProfit) + Math.abs(point.cumulativeProfit));
                    int crossX = prevX + (int)((x - prevX) * ratio);
                    int crossY = bounds.toY(pa, 0);

                    // Draw first segment
                    g2.setColor(prevPositive ? profitColor : lossColor);
                    g2.drawLine(prevX, prevY, crossX, crossY);

                    // Draw second segment
                    g2.setColor(currentPositive ? profitColor : lossColor);
                    g2.drawLine(crossX, crossY, x, y);
                }

                path.lineTo(x, y);
            }
        }

        // Draw points
        Runnable drawToolTip = () -> {};
        g2.setStroke(AXIS_STROKE);
        for (Datapoint dp : data) {
            int x = bounds.toX(pa, dp.timestamp());
            int y = bounds.toY(pa, dp.cumulativeProfit);

            g2.setColor(dp.cumulativeProfit >= 0 ? profitColor : lossColor);
            g2.fillOval(x - POINT_RADIUS, y - POINT_RADIUS, POINT_RADIUS * 2, POINT_RADIUS * 2);
            if(dp.isCumulativeProfitHovered) {
                drawToolTip = () -> {
                    g2.setColor(Color.WHITE);
                    g2.drawOval(x - POINT_RADIUS, y - POINT_RADIUS, POINT_RADIUS * 2, POINT_RADIUS * 2);
                    Point p = new Point(bounds.toX(upperPa, dp.timestamp()), bounds.toY(upperPa, dp.cumulativeProfit));
                    drawToolTip(dp.t, dp.cumulativeProfit, g2, p);
                };
            }
        }
        drawToolTip.run(); // draw tool tip after so it's on top
    }


    private void drawXAxisLabels(Graphics2D g2, Rectangle pa, List<Tick> xTicks, Bounds bounds) {
        g2.setColor(TEXT_COLOR);
        FontMetrics fm = g2.getFontMetrics();
        for (Tick t : xTicks) {
            int x = bounds.toX(pa, (int) t.value);
            int labelWidth = fm.stringWidth(t.label);
            g2.drawString(t.label, x - labelWidth / 2,  pa.y + pa.height + 20);
        }
    }
    private void drawYAxisLabels(Graphics2D g2, Rectangle pa,  List<Tick> yTicks, Bounds bounds) {
        g2.setColor(TEXT_COLOR);
        FontMetrics boldFm = g2.getFontMetrics();
        for (Tick t : yTicks) {
            int y = bounds.toY(pa, t.value);
            int labelWidth = boldFm.stringWidth(t.label);
            g2.drawString(t.label, PADDING_LEFT - labelWidth - 10,
                    y + boldFm.getHeight() / 3);
        }
    }


    private void drawDailyProfitBars(Graphics2D g2,  Bounds bounds) {
        if (data.isEmpty()) {
            return;
        }
        Runnable drawToolTip = () -> {};
        for (Datapoint dp : data) {
            Color barColor = dp.dailyProfit >= 0 ? profitColor : lossColor;
            g2.setColor(new Color(barColor.getRed(), barColor.getGreen(), barColor.getBlue(), 180)); // 70% opacity
            Rectangle bar = profitBarRect(dp, bounds);
            g2.fillRect(bar.x, bar.y, bar.width, bar.height);
            g2.setColor(new Color(barColor.getRed(), barColor.getGreen(), barColor.getBlue()));
            g2.drawRect(bar.x, bar.y, bar.width, bar.height);
            if(dp.isDailyProfitHovered) {
                drawToolTip = () -> {
                    g2.setColor(Color.WHITE);
                    g2.drawRect(bar.x, bar.y, bar.width, bar.height);
                    Point p = new Point(bounds.toX(lowerPa, dp.timestamp()), bounds.toY(lowerPa, dp.dailyProfit));
                    drawToolTip(dp.t, dp.dailyProfit, g2, p);
                };
            }
        }
        drawToolTip.run(); // draw tool tip after so it's on top
    }

    private Rectangle profitBarRect(Datapoint dp,  Bounds bounds) {
        int x1 = bounds.toX(lowerPa, dp.timestamp()- AxisCalculator.DAY_SECONDS / 2);
        int x2 = bounds.toX(lowerPa, dp.timestamp() + AxisCalculator.DAY_SECONDS / 2);
        int y1 =  bounds.toY(lowerPa, dp.dailyProfit);
        int y2 = bounds.toY(lowerPa, 0);
        if (dp.dailyProfit >= 0) {
            return new Rectangle(x1, y1, x2-x1, y2-y1);
        } else {
            return new Rectangle(x1, y2, x2-x1, y1-y2);
        }
    }

    private void drawToolTip(LocalDate t, long v, Graphics2D g2, Point p) {
        // Use exact same constants as DatapointTooltip
        final Color TOOLTIP_BACKGROUND = new Color(43, 43, 43);
        final Color TOOLTIP_BORDER = new Color(150, 150, 150);
        final int TOOLTIP_PADDING = 8; // Don't scale - use exact value from DatapointTooltip

        // Prepare tooltip text
        String dailyProfitStr = String.format("%,d", v);
        String dateStr = t.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"));

        FontMetrics fm = g2.getFontMetrics();

        int dateWidth = fm.stringWidth(dateStr);
        int profitWidth = fm.stringWidth(dailyProfitStr);
        int textWidth = Math.max(dateWidth, profitWidth);
        int textHeight = fm.getHeight() * 2; // Two lines of text

        int tooltipWidth = textWidth + TOOLTIP_PADDING * 2;
        int tooltipHeight = textHeight + TOOLTIP_PADDING * 2;

        int tooltipX = p.x;
        int tooltipY = p.y;

        if (tooltipX < lowerPa.x) {
            tooltipX = lowerPa.x + 5;
        } else if (tooltipX + tooltipWidth > lowerPa.x + lowerPa.width) {
            tooltipX = lowerPa.x + lowerPa.width - tooltipWidth - 5;
        }
        g2.setColor(TOOLTIP_BACKGROUND);
        g2.fillRoundRect(tooltipX, tooltipY, tooltipWidth, tooltipHeight, 8, 8);

        g2.setColor(TOOLTIP_BORDER);
        g2.setStroke(new BasicStroke(1.0f));
        g2.drawRoundRect(tooltipX, tooltipY, tooltipWidth, tooltipHeight, 8, 8);
        g2.setColor(TEXT_COLOR);
        int yPos = tooltipY + TOOLTIP_PADDING + fm.getAscent();
        g2.drawString(dailyProfitStr, tooltipX + TOOLTIP_PADDING, yPos);
        yPos += fm.getHeight();
        g2.drawString(dateStr, tooltipX + TOOLTIP_PADDING, yPos);
    }
}