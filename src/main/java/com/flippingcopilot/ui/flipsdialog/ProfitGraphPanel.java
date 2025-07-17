package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.ui.graph.model.Bounds;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ProfitGraphPanel extends JPanel {

    // Layout constants
    private static final int PADDING_LEFT = 80;
    private static final int PADDING_RIGHT = 30;
    private static final int PADDING_TOP = 30;
    private static final int PADDING_BOTTOM = 50;

    // Visual constants
    private static final Color BACKGROUND_COLOR = new Color(43, 43, 43);
    private static final Color PLOT_AREA_COLOR = new Color(51, 51, 51);
    private static final Color GRID_COLOR = new Color(85, 85, 85, 90);
    private static final Color AXIS_COLOR = new Color(150, 150, 150);
    private static final Color TEXT_COLOR = new Color(225, 225, 225);
    private static final Color ZERO_LINE_COLOR = new Color(200, 200, 200);

    private static final float FONT_SIZE = 16f;
    private static final Stroke LINE_STROKE = new BasicStroke(2f);
    private static final Stroke GRID_STROKE = new BasicStroke(
            0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{3}, 0
    );
    private final Color lossColor;
    private final Color profitColor;

    // Calculated bounds
    private Bounds bounds;
    private List<ProfitDataPoint> data;


    public void setData(List<ProfitDataPoint> newData, Bounds bounds) {
        this.data = newData;
        this.bounds = bounds;
    }

    public static class ProfitDataPoint {
        public final long timestamp; // Unix timestamp in seconds
        public final long profit;    // Profit value in gp

        public ProfitDataPoint(long timestamp, long profit) {
            this.timestamp = timestamp;
            this.profit = profit;
        }
    }

    public ProfitGraphPanel(Color profitColor, Color lossColor) {
        this.data = new ArrayList<>();
        this.bounds = new Bounds();
        this.bounds.yMax = 10_000_000;
        this.profitColor = profitColor;
        this.lossColor = lossColor;

        setBackground(BACKGROUND_COLOR);
        setPreferredSize(new Dimension(600, 400));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        int plotWidth = width - PADDING_LEFT - PADDING_RIGHT;
        int plotHeight = height - PADDING_TOP - PADDING_BOTTOM;

        g2.setColor(PLOT_AREA_COLOR);
        g2.fillRect(PADDING_LEFT, PADDING_TOP, plotWidth, plotHeight);

        drawGrid(g2, plotWidth, plotHeight);
        drawAxes(g2, plotWidth, plotHeight);

        if (!data.isEmpty()) {
            drawProfitLine(g2, plotWidth, plotHeight);
        }

        drawAxisLabels(g2, plotWidth, plotHeight);
    }

    private void drawGrid(Graphics2D g2, int plotWidth, int plotHeight) {
        g2.setColor(GRID_COLOR);
        g2.setStroke(GRID_STROKE);

        // Vertical grid lines (time)
        int numVerticalLines = Math.min(10, Math.max(4, plotWidth / 100));
        for (int i = 0; i <= numVerticalLines; i++) {
            int x = PADDING_LEFT + (plotWidth * i / numVerticalLines);
            g2.drawLine(x, PADDING_TOP, x, PADDING_TOP + plotHeight);
        }

        // Horizontal grid lines (profit)
        int numHorizontalLines = Math.min(10, Math.max(4, plotHeight / 50));
        for (int i = 0; i <= numHorizontalLines; i++) {
            int y = PADDING_TOP + (plotHeight * i / numHorizontalLines);
            g2.drawLine(PADDING_LEFT, y, PADDING_LEFT + plotWidth, y);
        }

        g2.setColor(ZERO_LINE_COLOR);
        g2.setStroke(new BasicStroke(1.5f));
        int zeroY = profitToY(0, plotHeight);
        g2.drawLine(PADDING_LEFT, zeroY, PADDING_LEFT + plotWidth, zeroY);
    }

    private void drawAxes(Graphics2D g2, int plotWidth, int plotHeight) {
        g2.setColor(AXIS_COLOR);
        g2.setStroke(new BasicStroke(1.0f));

        g2.drawLine(PADDING_LEFT, PADDING_TOP + plotHeight,
                PADDING_LEFT + plotWidth, PADDING_TOP + plotHeight);

        g2.drawLine(PADDING_LEFT, PADDING_TOP,
                PADDING_LEFT, PADDING_TOP + plotHeight);
    }

    private void drawProfitLine(Graphics2D g2, int plotWidth, int plotHeight) {
        if (data.size() < 2) {
            // Just draw a point if we only have one data point
            if (data.size() == 1) {
                ProfitDataPoint point = data.get(0);
                int x = timeToX(point.timestamp, plotWidth);
                int y = profitToY(point.profit, plotHeight);

                g2.setColor(point.profit >= 0 ? profitColor : lossColor);
                g2.fillOval(x - 3, y - 3, 6, 6);
            }
            return;
        }

        g2.setStroke(LINE_STROKE);

        // Create the line path
        Path2D.Float path = new Path2D.Float();
        boolean started = false;

        for (int i = 0; i < data.size(); i++) {
            ProfitDataPoint point = data.get(i);
            int x = timeToX(point.timestamp, plotWidth);
            int y = profitToY(point.profit, plotHeight);

            if (!started) {
                path.moveTo(x, y);
                started = true;
            } else {
                // Draw segment with appropriate color
                ProfitDataPoint prevPoint = data.get(i - 1);
                int prevX = timeToX(prevPoint.timestamp, plotWidth);
                int prevY = profitToY(prevPoint.profit, plotHeight);

                // Determine color based on whether we're above or below zero
                boolean currentPositive = point.profit >= 0;
                boolean prevPositive = prevPoint.profit >= 0;

                if (currentPositive == prevPositive) {
                    // Same sign, simple line
                    g2.setColor(currentPositive ? profitColor : lossColor);
                    g2.drawLine(prevX, prevY, x, y);
                } else {
                    // Crossing zero, need to interpolate
                    double ratio = Math.abs((double)prevPoint.profit) /
                            (Math.abs(prevPoint.profit) + Math.abs(point.profit));
                    int crossX = prevX + (int)((x - prevX) * ratio);
                    int crossY = profitToY(0, plotHeight);

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
        g2.setStroke(new BasicStroke(1.0f));
        for (ProfitDataPoint point : data) {
            int x = timeToX(point.timestamp, plotWidth);
            int y = profitToY(point.profit, plotHeight);

            g2.setColor(point.profit >= 0 ? profitColor : lossColor);
            g2.fillOval(x - 3, y - 3, 6, 6);

        }
    }

    private void drawAxisLabels(Graphics2D g2, int plotWidth, int plotHeight) {
        g2.setFont(g2.getFont().deriveFont(FONT_SIZE));
        g2.setColor(TEXT_COLOR);
        FontMetrics fm = g2.getFontMetrics();

        // X-axis labels (time)
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d");

        int numLabels = Math.min(8, Math.max(2, plotWidth / 100));
        for (int i = 0; i <= numLabels; i++) {
            long time = bounds.xMin + (long) bounds.xDelta() * i / numLabels;
            int x = PADDING_LEFT + (plotWidth * i / numLabels);

            Date date = new Date(time * 1000L);
            String label;
            label = dateFormat.format(date);

            int labelWidth = fm.stringWidth(label);
            g2.drawString(label, x - labelWidth / 2,
                    PADDING_TOP + plotHeight + 20);
        }

        // Y-axis labels (profit)
        NumberFormat nf = NumberFormat.getInstance();
        int numYLabels = Math.min(10, Math.max(4, plotHeight / 40));

        for (int i = 0; i <= numYLabels; i++) {
            long profit = bounds.yMin + (bounds.yDelta()) * i / numYLabels;
            int y = PADDING_TOP + plotHeight - (plotHeight * i / numYLabels);

            String label = formatProfit(profit);
            int labelWidth = fm.stringWidth(label);

            g2.drawString(label, PADDING_LEFT - labelWidth - 10,
                    y + fm.getHeight() / 3);
        }
    }

    private String formatProfit(long profit) {
        String sign = profit >= 0 ? "" : "-";

        if (Math.abs(profit) >= 1_000_000_000) {
            return sign + String.format("%.2fB", profit / 1_000_000_000.0);
        } else if (Math.abs(profit) >= 1_000_000) {
            return sign + String.format("%.1fM", profit / 1_000_000.0);
        } else if (Math.abs(profit) >= 1_000) {
            return sign + String.format("%.1fK", profit / 1_000.0);
        } else {
            return sign + profit;
        }
    }

    private int timeToX(long time, int plotWidth) {
        if (bounds.xDelta() == 0) return PADDING_LEFT + plotWidth / 2;
        return PADDING_LEFT + (int)((time - bounds.xMin) * plotWidth / bounds.xDelta());
    }

    private int profitToY(long profit, int plotHeight) {
        if (bounds.yDelta() == 0) return PADDING_TOP + plotHeight / 2;
        return PADDING_TOP + plotHeight -
                (int)((profit - bounds.yMin) * plotHeight / (bounds.yDelta()));
    }
}