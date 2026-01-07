package com.flippingcopilot.ui.graph;

import com.flippingcopilot.ui.graph.model.*;

import java.awt.*;
import java.awt.geom.Path2D;
import java.util.List;
import java.util.function.BiFunction;

public class RenderV2 {

    public void drawGrid(Graphics2D g2, Config config, Rectangle pa, Bounds bounds, BiFunction<Rectangle, Long, Integer> toY, TimeAxis xAxis, YAxis yAxis) {
        g2.setColor(config.gridColor);
        g2.setStroke(Config.NORMAL_STROKE);
        for (int t : xAxis.dateOnlyTickTimes) {
            int x = bounds.toX(pa,t);
            g2.drawLine(x, pa.y, x, pa.y + pa.height);
        }
        g2.setStroke(Config.GRID_STROKE);
        for (int t : xAxis.timeOnlyTickTimes) {
            int x = bounds.toX(pa,t);
            g2.drawLine(x, pa.y, x, pa.y + pa.height);
        }
        for (int t : xAxis.gridOnlyTickTimes) {
            int x = bounds.toX(pa,t);
            g2.drawLine(x, pa.y, x, pa.y + pa.height);
        }
        for (long p : yAxis.tickValues) {
            int y = toY.apply(pa,p);
            g2.drawLine(pa.x, y, pa.x+pa.width, y);
        }
        for (long p : yAxis.gridOnlyValues) {
            int y =toY.apply(pa,p);
            g2.drawLine(pa.x, y, pa.x + pa.width, y);
        }
    }

    public void drawAxes(Graphics2D g2, Config config,  Rectangle pa) {
        g2.setColor(config.axisColor);
        g2.setStroke(new BasicStroke(1.0f));
        g2.drawLine(pa.x,  pa.y + pa.height, pa.x + pa.width, pa.y + pa.height);
        g2.drawLine(pa.x, pa.y, pa.x, pa.y +pa.height);
    }

    public void drawXAxisLabels(Graphics2D g2, Config config, Rectangle pa, Bounds bounds, TimeAxis xAxis) {
        g2.setFont(g2.getFont().deriveFont(Config.FONT_SIZE));
        g2.setColor(config.textColor);
        FontMetrics metrics = g2.getFontMetrics();

        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("d MMM");
        java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm");

        // Draw date labels (longer ticks)
        for (int time : xAxis.dateOnlyTickTimes) {
            int x = bounds.toX(pa,time);
            g2.drawLine(x, pa.y + pa.height,  x, pa.y + pa.height + Config.TICK_SIZE * 2);
            String label = dateFormat.format(new java.util.Date(time * 1000L));
            int labelWidth = metrics.stringWidth(label);
            g2.drawString(label,  x - labelWidth / 2, pa.y + pa.height + Config.TICK_SIZE * 2 + 9 + metrics.getHeight());
        }

        if (xAxis.dateOnlyTickTimes.length == 0 && xAxis.timeOnlyTickTimes.length > 0) {
            int x = pa.x + pa.width / 2;
            String label = dateFormat.format(new java.util.Date(bounds.xMid() * 1000L));
            int labelWidth = metrics.stringWidth(label);
            g2.drawString(label,  x - labelWidth / 2, pa.y + pa.height + Config.TICK_SIZE * 2 + 9 + metrics.getHeight());
        }

        // Draw time labels (shorter ticks)
        for (int time : xAxis.timeOnlyTickTimes) {
            int x = bounds.toX(pa,time);
            g2.drawLine(x, pa.y + pa.height, x, pa.y +  pa.height + Config.TICK_SIZE);
            String label = timeFormat.format(new java.util.Date(time * 1000L));
            int labelWidth = metrics.stringWidth(label);
            g2.drawString(label, x - labelWidth / 2, pa.y +  pa.height + Config.TICK_SIZE + metrics.getHeight());
        }
    }

    public void drawYAxisLabels(Graphics2D g2, Config config, Rectangle pa, BiFunction<Rectangle, Long, Integer> toY, YAxis yAxis, boolean skipLast) {
        g2.setFont(g2.getFont().deriveFont(Config.FONT_SIZE));
        g2.setColor(config.textColor);
        FontMetrics metrics = g2.getFontMetrics();
        for (long v : yAxis.tickValues) {
            int y = toY.apply(pa, v);
            g2.drawLine(pa.x - Config.TICK_SIZE,y, pa.x, y);
            String label = com.flippingcopilot.ui.UIUtilities.quantityToRSDecimalStack(v, true);
            if(!skipLast || v != yAxis.tickValues[yAxis.tickValues.length-1]) {
                g2.drawString(label, pa.x - metrics.stringWidth(label) - Config.LABEL_PADDING, y + metrics.getHeight() / 3);
            }
        }
    }


    public void drawPredictionIQR(Graphics2D g2d,Config config, Rectangle pa,  Bounds bounds, int[] times, int[] lowerPrices, int[] upperPrices, boolean isLow) {
        if (times.length < 2) return;

        // Set appropriate color
        g2d.setColor(isLow ? config.lowShadeColor : config.highShadeColor);

        // Create path for shaded area with clipping
        Path2D path = new Path2D.Double();
        boolean started = false;

        // Start at the first point that's in range
        for (int i = 0; i < times.length; i++) {
            int time = times[i];
            if (time >= bounds.xMin && time <=bounds.xMax) {
                int x = bounds.toX(pa,time);
                int y = bounds.toY(pa,lowerPrices[i]);

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
            if (time >= bounds.xMin && time <= bounds.xMax) {
                int x = bounds.toX(pa,time);
                int y = bounds.toY(pa,upperPrices[i]);
                path.lineTo(x, y);
            }
        }

        // Close the path if we drew anything
        if (started) {
            path.closePath();
            g2d.fill(path);
        }
    }

    public void drawLines(Graphics2D g2d,
                          Rectangle pa,
                          Bounds bounds,
                          List<Datapoint> datapoints,
                          Color color,
                          Stroke stroke) {
        if (datapoints.isEmpty()) return;

        g2d.setStroke(stroke);
        g2d.setColor(color);
        java.awt.Shape originalClip = g2d.getClip();
        g2d.setClip(pa.x, pa.y, pa.width, pa.height);

        java.awt.geom.Path2D.Float path = new java.awt.geom.Path2D.Float();

        // Start the path at the first point
        int x = bounds.toX(pa,datapoints.get(0).time);
        int y = bounds.toY(pa,datapoints.get(0).price);
        path.moveTo(x, y);

        for (Datapoint d : datapoints.subList(1, datapoints.size())) {
            x = bounds.toX(pa,d.time);
            y = bounds.toY(pa,d.price);
            path.lineTo(x, y);
        }

        g2d.draw(path);
        g2d.setClip(originalClip);
    }

    public void drawStartPoints(Graphics2D plotAreaG2,
                                Rectangle pa,
                                Bounds bounds, List<Datapoint> startPoints,
                                Color color,
                                int size
    ) {
        if (startPoints.isEmpty()) return;

        plotAreaG2.setColor(color);
        java.awt.Shape originalClip = plotAreaG2.getClip();
        plotAreaG2.setClip(pa.x, pa.y, pa.width, pa.height);

        for (Datapoint d : startPoints) {
            if (d.time < bounds.xMin || d.time > bounds.xMax) {
                continue;
            }
            int x = bounds.toX(pa,d.time);
            int y = bounds.toY(pa,d.price);

            Stroke originalStroke = plotAreaG2.getStroke();
            float strokeWidth = Math.max(1, size / 5);
            plotAreaG2.setStroke(new BasicStroke(strokeWidth));

            int halfSize = size / 2;
            plotAreaG2.drawLine(x - halfSize, y, x + halfSize, y);
            plotAreaG2.drawLine(x, y - halfSize, x, y + halfSize);
            plotAreaG2.drawLine(x - halfSize, y - halfSize, x + halfSize, y + halfSize);
            plotAreaG2.drawLine(x + halfSize, y - halfSize, x - halfSize, y + halfSize);
            plotAreaG2.setStroke(originalStroke);
        }
        plotAreaG2.setClip(originalClip); // restore original clip
    }

    public void drawPoints(Graphics2D g2d,
                            Rectangle pa,
                            Bounds bounds,
                            List<Datapoint> datapoints,
                            Color color,
                            int size
    ) {
        if (datapoints.isEmpty()) return;
        g2d.setColor(color);
        java.awt.Shape originalClip = g2d.getClip();
        g2d.setClip(pa.x, pa.y, pa.width, pa.height);

        // Draw each point as a filled oval
        for (Datapoint d : datapoints) {
            if (d.time < bounds.xMin || d.time > bounds.xMax) {
                continue;
            }
            int x = bounds.toX(pa,d.time);
            int y = bounds.toY(pa,d.price);
            int ovalX = x - size / 2;
            int ovalY = y - size / 2;
            if (d.type == Datapoint.Type.PREDICTION || d.type == Datapoint.Type.INSTA_SELL_BUY) {
                g2d.fillOval(ovalX, ovalY, size, size);
            } else {
                // rectangle for 5m/1h averages
                int timeDelta = d.type == Datapoint.Type.FIVE_MIN_AVERAGE ? Constants.FIVE_MIN_SECONDS : Constants.HOUR_SECONDS;
                int w = bounds.toW(pa, timeDelta);
                g2d.fillRect(x, y, w + size, size);
            }
        }
        g2d.setClip(originalClip); // restore original clip
    }

    public void drawLegend(Graphics2D g2, Config config, Rectangle pa, boolean addPredictionLabels) {
        int xMid = pa.x + pa.width / 2;
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, Config.FONT_SIZE));
        FontMetrics metrics = g2.getFontMetrics();

        // Legend item text labels
        String[] labels = addPredictionLabels
                ? new String[]{"Lows (insta-sell)", "Highs (insta-buy)", "Low prediction", "High prediction", "Low IQR", "High IQR"}
                : new String[]{"Lows (insta-sell)", "Highs (insta-buy)"};

        // Calculate legend position - above the plot area
        int legendY = pa.y / 2;
        int lineLength = 20;
        int itemHeight = 15;
        int itemPadding = 30; // Space between text end and next item start

        // Calculate widths for each legend item based on text length
        int[] itemWidths = new int[labels.length];
        int totalWidth = 0;

        for (int i = 0; i < labels.length; i++) {
            itemWidths[i] = lineLength + 5 + metrics.stringWidth(labels[i]);
            totalWidth += itemWidths[i];
        }

        totalWidth += itemPadding * (labels.length - 1);

        int legendStartX = xMid - totalWidth / 2;
        int currentX = legendStartX;

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

        if (addPredictionLabels) {
            // Low prediction
            g2.setColor(config.lowColor);
            g2.setStroke(Config.DOTTED_STROKE);
            g2.drawLine(currentX, legendY + itemHeight/2, currentX + lineLength, legendY + itemHeight/2);
            g2.setColor(config.textColor);
            g2.drawString(labels[2], currentX + lineLength + 5, legendY + itemHeight/2 + 4);
            currentX += itemWidths[2] + itemPadding;

            // High prediction
            g2.setColor(config.highColor);
            g2.setStroke(Config.DOTTED_STROKE);
            g2.drawLine(currentX, legendY + itemHeight/2, currentX + lineLength, legendY + itemHeight/2);
            g2.setColor(config.textColor);
            g2.drawString(labels[3], currentX + lineLength + 5, legendY + itemHeight/2 + 4);
            currentX += itemWidths[3] + itemPadding;

            // Low IQR
            g2.setColor(config.lowShadeColor);
            g2.fillRect(currentX, legendY + itemHeight/2 - 5, lineLength, 10);
            g2.setColor(config.textColor);
            g2.drawString(labels[4], currentX + lineLength + 5, legendY + itemHeight/2 + 4);
            currentX += itemWidths[4] + itemPadding;

            // High IQR
            g2.setColor(config.highShadeColor);
            g2.fillRect(currentX, legendY + itemHeight/2 - 5, lineLength, 10);
            g2.setColor(config.textColor);
            g2.drawString(labels[5], currentX + lineLength + 5, legendY + itemHeight/2 + 4);
        }
    }

    public void drawVolumeBars(Graphics2D g2d, Config config, Rectangle pa, Bounds bounds, List<Datapoint> volumes, Datapoint hoveredPoint) {
        java.awt.Shape originalClip = g2d.getClip();
        g2d.setClip(pa.x, pa.y, pa.width, pa.height);
        Color high= new Color(config.highColor.getRed(), config.highColor.getGreen(), config.highColor.getBlue(), 128);
        Color low = new Color(config.lowColor.getRed(), config.lowColor.getGreen(), config.lowColor.getBlue(), 128);
        for (Datapoint v : volumes) {
            int x1 = bounds.toX(pa, v.time);
            int x2 = bounds.toX(pa, v.time + Constants.HOUR_SECONDS);
            int y1 = bounds.toY2(pa, v.highVolume + v.lowVolume);
            int y2 = bounds.toY2(pa, v.lowVolume);
            int y3 = bounds.toY2(pa, 0);
            g2d.setColor(high);
            g2d.fillRect(x1, y1, x2-x1, y2-y1);
            g2d.setColor(low);
            g2d.fillRect(x1, y2, x2-x1, y3-y2);
            if (hoveredPoint == v) {
                g2d.setColor(Color.WHITE);
                g2d.setStroke(Config.THICK_STROKE);
                g2d.drawRect(x1, y1, x2 - x1, y3 - y1);
            } else {
                g2d.setColor(Color.GRAY);
                g2d.setStroke(Config.THIN_STROKE);
                g2d.drawRect(x1, y1, x2 - x1, y3 - y1);
            }
        }
        g2d.setClip(originalClip); // restore original clip
    }

    public void drawTxsDatapoints(Graphics2D g2d,
                                  Rectangle pa,
                                  Bounds bounds,
                                  List<Datapoint> txDatapoints,
                                  Datapoint hoveredPoint,
                                  Config config) {
        if (txDatapoints == null || txDatapoints.isEmpty()) return;

        java.awt.Shape originalClip = g2d.getClip();
        g2d.setClip(pa.x, pa.y, pa.width, pa.height);

        int circleSize = 24;
        int radius = circleSize / 2;

        g2d.setFont(g2d.getFont().deriveFont(Font.BOLD, 14f));
        FontMetrics metrics = g2d.getFontMetrics();

        for (Datapoint d : txDatapoints) {
            if (d.time < bounds.xMin || d.time > bounds.xMax) {
                continue;
            }

            int x = bounds.toX(pa, d.time);
            int y = bounds.toY(pa, d.price);

            g2d.setColor(d.isLow ? config.lowColor : config.highColor);
            g2d.fillOval(x - radius, y - radius, circleSize, circleSize);

            g2d.setColor(hoveredPoint == d ? Color.BLACK : Color.WHITE);
            g2d.setStroke(new BasicStroke(2.0f));
            g2d.drawOval(x - radius, y - radius, circleSize, circleSize);

            String text = d.isLow ? "B" : "S";
            int textWidth = metrics.stringWidth(text);
            int textHeight = metrics.getAscent();

            g2d.setColor(Color.WHITE);
            g2d.drawString(text,
                    x - textWidth / 2,
                    y + textHeight / 3); // Adjust vertical centering
        }

        g2d.setClip(originalClip);
    }
}
