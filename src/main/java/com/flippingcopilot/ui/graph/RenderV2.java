package com.flippingcopilot.ui.graph;

import com.flippingcopilot.ui.graph.model.*;

import java.awt.*;
import java.awt.geom.Path2D;
import java.util.List;

public class RenderV2 {

    public void drawGrid(Graphics2D plotAreaG2,Config config,  PlotArea pa, TimeAxis xAxis, PriceAxis yAxis) {
        plotAreaG2.setColor(config.gridColor);
        plotAreaG2.setStroke(Config.GRID_STROKE);
        for (int t : xAxis.dateOnlyTickTimes) {
            Stroke originalStroke = plotAreaG2.getStroke();
            int x = pa.timeToX(t);
            plotAreaG2.setStroke(new BasicStroke(1.0f));
            plotAreaG2.drawLine(x, 0, x, pa.h);
            plotAreaG2.setStroke(originalStroke);
        }
        for (int t : xAxis.timeOnlyTickTimes) {
            int x = pa.timeToX(t);
            plotAreaG2.drawLine(x, 0, x, pa.h);
        }
        for (int t : xAxis.gridOnlyTickTimes) {
            int x = pa.timeToX(t);
            plotAreaG2.drawLine(x, 0, x, pa.h);
        }
        for (int p : yAxis.tickPrices) {
            int y = pa.priceToY(p);
            plotAreaG2.drawLine(0, y, pa.w, y);
        }
        for (int p : yAxis.gridOnlyPrices) {
            int y = pa.priceToY(p);
            plotAreaG2.drawLine(0, y, pa.w, y);
        }
    }

    public void drawAxes(Graphics2D g2, Config config,  PlotArea pa, TimeAxis xAxis, PriceAxis yAxis) {
        g2.setColor(config.axisColor);
        g2.setStroke(new BasicStroke(1.0f));
        g2.drawLine(pa.leftPadding,  pa.topPadding + pa.h, pa.leftPadding + pa.w, pa.topPadding + pa.h);
        g2.drawLine(pa.leftPadding, pa.topPadding, pa.leftPadding, pa.topPadding +pa.h);
        drawXAxisLabels(g2,config, pa, xAxis);
        drawYAxisLabels(g2, config,pa, yAxis);
    }

    public void drawXAxisLabels(Graphics2D g2, Config config, PlotArea pa, TimeAxis xAxis) {
        g2.setFont(g2.getFont().deriveFont(Config.FONT_SIZE));
        g2.setColor(config.textColor);
        FontMetrics metrics = g2.getFontMetrics();

        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("d MMM");
        java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm");

        // Draw date labels (longer ticks)
        for (int time : xAxis.dateOnlyTickTimes) {
            int x = pa.timeToX(time);
            g2.drawLine(pa.leftPadding + x, pa.topPadding + pa.h, pa.leftPadding + x, pa.topPadding + pa.h + Config.TICK_SIZE * 2);
            String label = dateFormat.format(new java.util.Date(time * 1000L));
            int labelWidth = metrics.stringWidth(label);
            g2.drawString(label, pa.leftPadding + x - labelWidth / 2, pa.topPadding + pa.h + Config.TICK_SIZE * 2 + 9 + metrics.getHeight());
        }

        // Draw time labels (shorter ticks)
        for (int time : xAxis.timeOnlyTickTimes) {
            int x = pa.timeToX(time);

            g2.drawLine(pa.leftPadding +x, pa.topPadding + pa.h, pa.leftPadding +x, pa.topPadding +  pa.h + Config.TICK_SIZE);

            String label = timeFormat.format(new java.util.Date(time * 1000L));
            int labelWidth = metrics.stringWidth(label);
            g2.drawString(label, pa.leftPadding + x - labelWidth / 2, pa.topPadding +  pa.h + Config.TICK_SIZE + metrics.getHeight());
        }
    }

    public void drawYAxisLabels(Graphics2D g2, Config config, PlotArea pa, PriceAxis yAxis) {
        g2.setFont(g2.getFont().deriveFont(Config.FONT_SIZE));
        g2.setColor(config.textColor);
        FontMetrics metrics = g2.getFontMetrics();
        for (int price : yAxis.tickPrices) {
            int y = pa.priceToY(price);
            g2.drawLine(pa.leftPadding - Config.TICK_SIZE,pa.topPadding + y, pa.leftPadding, pa.topPadding+ y);

            // Format and draw the price label
            String label = com.flippingcopilot.ui.UIUtilities.quantityToRSDecimalStack(price, true);
            g2.drawString(label,
                    pa.leftPadding - metrics.stringWidth(label) - Config.LABEL_PADDING,
                    pa.topPadding + y + metrics.getHeight() / 3);
        }
    }


    public void drawPredictionIQR(Graphics2D plotAreaG2, Config config, PlotArea pa, int[] times, int[] lowerPrices, int[] upperPrices, boolean isLow) {
        if (times.length < 2) return;

        // Set appropriate color
        plotAreaG2.setColor(isLow ? config.lowShadeColor : config.highShadeColor);

        // Create path for shaded area with clipping
        Path2D path = new Path2D.Double();
        boolean started = false;

        // Start at the first point that's in range
        for (int i = 0; i < times.length; i++) {
            int time = times[i];
            if (time >= pa.bounds.xMin && time <=pa.bounds.xMax) {
                int x = pa.timeToX(time);
                int y = pa.priceToY(lowerPrices[i]);

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
            if (time >= pa.bounds.xMin && time <= pa.bounds.xMax) {
                int x = pa.timeToX(time);
                int y = pa.priceToY(upperPrices[i]);
                path.lineTo(x, y);
            }
        }

        // Close the path if we drew anything
        if (started) {
            path.closePath();
            plotAreaG2.fill(path);
        }
    }

    public void drawLines(Graphics2D plotAreaG2,
                          PlotArea pa,
                          List<Datapoint> datapoints,
                          Color color,
                          Stroke stroke) {
        if (datapoints.isEmpty()) return;

        // Set the specified stroke and color
        plotAreaG2.setStroke(stroke);
        plotAreaG2.setColor(color);

        // Create a clip rectangle matching the plot area bounds
        java.awt.Shape originalClip = plotAreaG2.getClip();
        plotAreaG2.setClip(0, 0, pa.w, pa.h);

        // Create path for line segments
        java.awt.geom.Path2D.Float path = new java.awt.geom.Path2D.Float();

        // Start the path at the first point
        int x = pa.timeToX(datapoints.get(0).time);
        int y = pa.priceToY(datapoints.get(0).price);
        path.moveTo(x, y);

        for (Datapoint d : datapoints.subList(1, datapoints.size())) {
            x = pa.timeToX(d.time);
            y = pa.priceToY(d.price);
            path.lineTo(x, y);
        }

        plotAreaG2.draw(path);
        plotAreaG2.setClip(originalClip);
    }

    public void drawStartPoints(Graphics2D plotAreaG2,
                                PlotArea pa,
                                List<Datapoint> startPoints,
                                Color color,
                                int size
    ) {
        if (startPoints.isEmpty()) return;

        // Save original color
        Color originalColor = plotAreaG2.getColor();

        // Set the specified color
        plotAreaG2.setColor(color);

        // Create a clip rectangle matching the plot area bounds
        java.awt.Shape originalClip = plotAreaG2.getClip();
        plotAreaG2.setClip(0, 0, pa.w, pa.h);

        // Draw each start point as a filled oval
        for (Datapoint d : startPoints) {
            if (d.time < pa.bounds.xMin || d.time > pa.bounds.xMax) {
                continue;
            }

            // Get current point coordinates
            int x = pa.timeToX(d.time);
            int y = pa.priceToY(d.price);

            // Draw an asterisk (*) at the point
            // Save the original stroke
            Stroke originalStroke = plotAreaG2.getStroke();

            // Set stroke based on size
            float strokeWidth = Math.max(1, size / 5);
            plotAreaG2.setStroke(new BasicStroke(strokeWidth));

            // Draw the lines of the asterisk (*)
            int halfSize = size / 2;

            // Horizontal line
            plotAreaG2.drawLine(x - halfSize, y, x + halfSize, y);

            // Vertical line
            plotAreaG2.drawLine(x, y - halfSize, x, y + halfSize);

            // Diagonal line from top-left to bottom-right
            plotAreaG2.drawLine(x - halfSize, y - halfSize, x + halfSize, y + halfSize);

            // Diagonal line from top-right to bottom-left
            plotAreaG2.drawLine(x + halfSize, y - halfSize, x - halfSize, y + halfSize);

            // Restore the original stroke
            plotAreaG2.setStroke(originalStroke);
        }

        // Restore original clip and color
        plotAreaG2.setClip(originalClip);
        plotAreaG2.setColor(originalColor);
    }

    public void drawPoints(Graphics2D plotAreaG2,
                            PlotArea pa,
                            List<Datapoint> datapoints,
                            Color color,
                            int size
    ) {
        if (datapoints.isEmpty()) return;

        // Save original color
        Color originalColor = plotAreaG2.getColor();

        // Set the specified color
        plotAreaG2.setColor(color);

        // Create a clip rectangle matching the plot area bounds
        java.awt.Shape originalClip = plotAreaG2.getClip();
        plotAreaG2.setClip(0, 0, pa.w, pa.h);

        // Draw each point as a filled oval
        for (Datapoint d : datapoints) {
            if (d.time < pa.bounds.xMin || d.time > pa.bounds.xMax) {
                continue;
            }

            // Get current point coordinates
            int x = pa.timeToX(d.time);
            int y = pa.priceToY(d.price);

            // Calculate the top-left corner for the oval (centered on x,y)
            int ovalX = x - size / 2;
            int ovalY = y - size / 2;

            if (d.type == Datapoint.Type.PREDICTION || d.type == Datapoint.Type.INSTA_SELL_BUY) {
                // filled oval for latest price points
                plotAreaG2.fillOval(ovalX, ovalY, size, size);
            } else {
                // rectangle for 5m/1h averages
                int timeDelta = d.type == Datapoint.Type.FIVE_MIN_AVERAGE ? Constants.FIVE_MIN_SECONDS : Constants.HOUR_SECONDS;
                int w = pa.timeDeltaToXDelta(timeDelta);
                plotAreaG2.fillRect(x, y, w + size, size);
            }
        }

        // Restore original clip and color
        plotAreaG2.setClip(originalClip);
        plotAreaG2.setColor(originalColor);
    }

    public void drawLegend(Graphics2D g2, Config config, PlotArea pa) {
        int xMid = pa.leftPadding + pa.w / 2;
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, Config.FONT_SIZE));
        FontMetrics metrics = g2.getFontMetrics();

        // Legend item text labels - now with additional entries
        String[] labels = {"Lows (insta-sell)", "Highs (insta-buy)", "Low prediction", "High prediction"};
        String[] labels2 = {"Low IQR", "High IQR"};  // New line for IQR entries

        // Calculate legend position - now above the plot area
        int legendY = pa.topPadding / 2; // Half the padding from the top
        int lineLength = 20;
        int itemHeight = 15;
        int itemPadding = 30; // Space between text end and next item start
        int lineSpacing = 20; // Space between first and second row

        // Calculate widths for each legend item based on text length
        int[] itemWidths = new int[labels.length];
        int totalWidth = 0;

        for (int i = 0; i < labels.length; i++) {
            itemWidths[i] = lineLength + 5 + metrics.stringWidth(labels[i]);
            totalWidth += itemWidths[i];
        }

        // Add padding between items to total width
        totalWidth += itemPadding * (labels.length - 1);

        // Calculate starting X position to center the legend
        int legendStartX = xMid - totalWidth / 2;
        int currentX = legendStartX;

        // First row of legend items

        // Low prices
        g2.setColor(config.lowColor);
        g2.setStroke(Config.NORMAL_STROKE);
        if (config.connectPoints) {
            g2.drawLine(currentX, legendY + itemHeight/2, currentX + lineLength, legendY + itemHeight/2);
        }
        g2.fillOval(currentX + lineLength / 2 - 2, legendY + itemHeight/2 - 2, 5, 5);
        g2.setColor(config.textColor);
        g2.drawString(labels[0], currentX + lineLength + 5, legendY + itemHeight/2 + 4);
        currentX += itemWidths[0] + itemPadding;

        // High prices
        g2.setColor(config.highColor);
        g2.setStroke(Config.NORMAL_STROKE);
        if (config.connectPoints) {
            g2.drawLine(currentX, legendY + itemHeight/2, currentX + lineLength, legendY + itemHeight/2);
        }
        g2.fillOval(currentX + lineLength / 2 - 2, legendY + itemHeight/2 - 2, 5, 5);
        g2.setColor(config.textColor);
        g2.drawString(labels[1], currentX + lineLength + 5, legendY + itemHeight/2 + 4);
        currentX += itemWidths[1] + itemPadding;

        // Low prediction mean
        g2.setColor(config.lowColor);
        g2.setStroke(Config.DOTTED_STROKE);
        g2.drawLine(currentX, legendY + itemHeight/2, currentX + lineLength, legendY + itemHeight/2);
        g2.setColor(config.textColor);
        g2.drawString(labels[2], currentX + lineLength + 5, legendY + itemHeight/2 + 4);
        currentX += itemWidths[2] + itemPadding;

        // High prediction mean
        g2.setColor(config.highColor);
        g2.setStroke(Config.DOTTED_STROKE);
        g2.drawLine(currentX, legendY + itemHeight/2, currentX + lineLength, legendY + itemHeight/2);
        g2.setColor(config.textColor);
        g2.drawString(labels[3], currentX + lineLength + 5, legendY + itemHeight/2 + 4);

        // Second row with IQR entries
        // Calculate widths for second row items
        int[] itemWidths2 = new int[labels2.length];
        int totalWidth2 = 0;

        for (int i = 0; i < labels2.length; i++) {
            itemWidths2[i] = lineLength + 5 + metrics.stringWidth(labels2[i]);
            totalWidth2 += itemWidths2[i];
        }

        // Add padding between items to total width for second row
        totalWidth2 += itemPadding * (labels2.length - 1);

        // Calculate starting X position to center the second row
        int legendStartX2 = xMid - totalWidth2 / 2;
        int currentX2 = legendStartX2;
        int legendY2 = legendY + itemHeight + 13; // Position for second row

        // Low IQR
        g2.setColor(config.lowShadeColor);
        // Draw a small filled rectangle to represent the shaded area
        g2.fillRect(currentX2, legendY2 + itemHeight/2 - 5, lineLength, 10);
        g2.setColor(config.textColor);
        g2.drawString(labels2[0], currentX2 + lineLength + 5, legendY2 + itemHeight/2 + 4);
        currentX2 += itemWidths2[0] + itemPadding;

        // High IQR
        g2.setColor(config.highShadeColor);
        // Draw a small filled rectangle to represent the shaded area
        g2.fillRect(currentX2, legendY2 + itemHeight/2 - 5, lineLength, 10);
        g2.setColor(config.textColor);
        g2.drawString(labels2[1], currentX2 + lineLength + 5, legendY2 + itemHeight/2 + 4);
    }
}
