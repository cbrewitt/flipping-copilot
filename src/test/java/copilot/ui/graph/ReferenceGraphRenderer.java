package copilot.ui.graph;
import static copilot.ui.graph.model.Config.*;

import copilot.ui.*;
import copilot.ui.graph.model.*;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.awt.geom.*;
import java.util.List;
import java.util.function.*;

public class ReferenceGraphRenderer {

    public void drawGrid(Graphics2D graphics, Config config, Rectangle pa, Bounds bounds, BiFunction<Rectangle, Long, Integer> toY, TimeAxis xAxis, YAxis yAxis) {
        graphics.setColor(config.gridColor); graphics.setStroke(NORMAL_STROKE);
        for (int t : xAxis.dateOnlyTickTimes) {
            int x = bounds.toX(pa,t);
            graphics.drawLine(x, pa.y, x, pa.y + pa.height);
        }
        graphics.setStroke(GRID_STROKE);
        for (int t : xAxis.timeOnlyTickTimes) {
            int x = bounds.toX(pa,t);
            graphics.drawLine(x, pa.y, x, pa.y + pa.height);
        }
        for (int t : xAxis.gridOnlyTickTimes) {
            int x = bounds.toX(pa,t);
            graphics.drawLine(x, pa.y, x, pa.y + pa.height);
        }
        for (long p : yAxis.tickValues) {
            int y = toY.apply(pa,p);
            graphics.drawLine(pa.x, y, pa.x+pa.width, y);
        }
        for (long p : yAxis.gridOnlyValues) {
            int y =toY.apply(pa,p);
            graphics.drawLine(pa.x, y, pa.x + pa.width, y);
        }
    }

    public void drawAxes(Graphics2D graphics, Config config,  Rectangle pa) {
        graphics.setColor(config.axisColor); graphics.setStroke(new BasicStroke(1.0f));
        graphics.drawLine(pa.x,  pa.y + pa.height, pa.x + pa.width, pa.y + pa.height);
        graphics.drawLine(pa.x, pa.y, pa.x, pa.y +pa.height);
    }

    public void drawXAxisLabels(Graphics2D graphics, Config config, Rectangle pa, Bounds bounds, TimeAxis xAxis) {
        graphics.setFont(graphics.getFont().deriveFont(FONT_SIZE)); graphics.setColor(config.textColor);
        var metrics = graphics.getFontMetrics();

        SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM");

        // Draw date labels (longer ticks)
        for (int time : xAxis.dateOnlyTickTimes) {
            int x = bounds.toX(pa,time);
            graphics.drawLine(x, pa.y + pa.height,  x, pa.y + pa.height + TICK_SIZE * 2);
            String label = dateFormat.format(new Date(time * 1000L));
            int labelWidth = metrics.stringWidth(label);
            graphics.drawString(label,  x - labelWidth / 2, pa.y + pa.height + TICK_SIZE * 2 + 9 + metrics.getHeight());
        }

        if (xAxis.dateOnlyTickTimes.length == 0 && xAxis.timeOnlyTickTimes.length > 0) {
            int x = pa.x + pa.width / 2;
            String label = dateFormat.format(new Date(bounds.xMid() * 1000L));
            int labelWidth = metrics.stringWidth(label);
            graphics.drawString(label,  x - labelWidth / 2, pa.y + pa.height + TICK_SIZE * 2 + 9 + metrics.getHeight());
        }

        // Draw time labels (shorter ticks)
        for (int time : xAxis.timeOnlyTickTimes) {
            int x = bounds.toX(pa,time);
            graphics.drawLine(x, pa.y + pa.height, x, pa.y +  pa.height + TICK_SIZE);
            String label = Constants.MINUTE_TIME_FORMAT.format(new Date(time * 1000L));
            int labelWidth = metrics.stringWidth(label);
            graphics.drawString(label, x - labelWidth / 2, pa.y +  pa.height + TICK_SIZE + metrics.getHeight());
        }
    }

    public void drawYAxisLabels(Graphics2D graphics, Config config, Rectangle pa, BiFunction<Rectangle, Long, Integer> toY, YAxis yAxis, boolean skipLast) {
        graphics.setFont(graphics.getFont().deriveFont(FONT_SIZE)); graphics.setColor(config.textColor);
        var metrics = graphics.getFontMetrics();
        for (long v : yAxis.tickValues) {
            int y = toY.apply(pa, v);
            graphics.drawLine(pa.x - TICK_SIZE,y, pa.x, y);
            String label = UIUtilities.quantityToRSDecimalStack(v, true);
            if(!skipLast || v != yAxis.tickValues[yAxis.tickValues.length-1]) {
                graphics.drawString(label, pa.x - metrics.stringWidth(label) - LABEL_PADDING, y + metrics.getHeight() / 3);
            }
        }
    }

    public void drawPredictionIQR(Graphics2D graphics,Config config, Rectangle pa, Bounds bounds, int[] times, long[] lowerPrices, long[] upperPrices, boolean isLow) {
        if (times.length < 2) return;

        // Set appropriate color
        graphics.setColor(isLow ? config.lowShadeColor : config.highShadeColor);

        // Create path for shaded area with clipping
        Path2D path = new Path2D.Double();
        boolean started = false;

        // Start at the first point that's in range
        for (int i = 0; i < times.length; i++) {
            int time = times[i];
            if (time >= bounds.xMin && time <=bounds.xMax) {
                int x = bounds.toX(pa,time), y = bounds.toY(pa,lowerPrices[i]);

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
                int x = bounds.toX(pa,time), y = bounds.toY(pa,upperPrices[i]);
                path.lineTo(x, y);
            }
        }

        // Close the path if we drew anything
        if (started) {
            path.closePath();
            graphics.fill(path);
        }
    }

    public void drawLines(Graphics2D graphics,
                          Rectangle pa,
                          Bounds bounds,
                          List<Datapoint> datapoints,
                          Color color,
                          Stroke stroke) {
        if (datapoints.isEmpty()) return;

        graphics.setStroke(stroke); graphics.setColor(color);
        var originalClip = graphics.getClip();
        graphics.setClip(pa.x, pa.y, pa.width, pa.height);

        Path2D.Float path = new Path2D.Float();

        // Start the path at the first point
        int x = bounds.toX(pa,datapoints.get(0).time), y = bounds.toY(pa,datapoints.get(0).price);
        path.moveTo(x, y);

        for (Datapoint d : datapoints.subList(1, datapoints.size())) {
            x = bounds.toX(pa,d.time);
            y = bounds.toY(pa,d.price);
            path.lineTo(x, y);
        }

        graphics.draw(path);
        graphics.setClip(originalClip);
    }

    public void drawStartPoints(Graphics2D graphics, Rectangle area, Bounds bounds,
                                List<Datapoint> points, Color color, int size) {
        drawPoints(graphics, area, bounds, points, color, size, true);
    }

    public void drawPoints(Graphics2D graphics, Rectangle area, Bounds bounds,
                           List<Datapoint> points, Color color, int size) {
        drawPoints(graphics, area, bounds, points, color, size, false);
    }

    private void drawPoints(Graphics2D graphics, Rectangle area, Bounds bounds,
                            List<Datapoint> points, Color color, int size, boolean starts) {
        if (points.isEmpty()) return;
        graphics.setColor(color);
        Shape originalClip = graphics.getClip();
        graphics.setClip(area.x, area.y, area.width, area.height);
        for (Datapoint point : points) {
            if (point.time < bounds.xMin || point.time > bounds.xMax) continue;
            int x = bounds.toX(area, point.time), y = bounds.toY(area, point.price);
            int half = size / 2;
            if (starts) {
                Stroke originalStroke = graphics.getStroke();
                graphics.setStroke(new BasicStroke(Math.max(1, size / 5)));
                graphics.drawLine(x - half, y, x + half, y);
                graphics.drawLine(x, y - half, x, y + half);
                graphics.drawLine(x - half, y - half, x + half, y + half);
                graphics.drawLine(x + half, y - half, x - half, y + half);
                graphics.setStroke(originalStroke);
            } else if (point.type == Datapoint.Type.PREDICTION || point.type == Datapoint.Type.INSTA_SELL_BUY) {
                graphics.fillOval(x - half, y - half, size, size);
            } else {
                int duration = point.type == Datapoint.Type.FIVE_MIN_AVERAGE ? Constants.FIVE_MIN_SECONDS : Constants.HOUR_SECONDS;
                graphics.fillRect(x, y, bounds.toW(area, duration) + size, size);
            }
        }
        graphics.setClip(originalClip);
    }

    public void drawLegend(Graphics2D graphics, Config config, Rectangle pa, boolean predictions) {
        graphics.setFont(graphics.getFont().deriveFont(Font.PLAIN, FONT_SIZE));
        var metrics = graphics.getFontMetrics();
        String[] labels = {"Lows (insta-sell)", "Highs (insta-buy)",
                "Low prediction", "High prediction", "Low IQR", "High IQR"};
        int count = predictions ? labels.length : 2;
        int lineLength = 20, padding = 30;
        int[] widths = new int[count];
        int totalWidth = padding * (count - 1);
        for (int i = 0; i < count; i++) {
            widths[i] = lineLength + 5 + metrics.stringWidth(labels[i]);
            totalWidth += widths[i];
        }
        int x = pa.x + pa.width / 2 - totalWidth / 2;
        int y = pa.y / 2 + 15 / 2;
        for (int i = 0; i < count; i++) {
            boolean low = i % 2 == 0;
            if (i >= 4) {
                graphics.setColor(low ? config.lowShadeColor : config.highShadeColor);
                graphics.fillRect(x, y - 5, lineLength, 10);
            } else {
                graphics.setColor(low ? config.lowColor : config.highColor);
                graphics.setStroke(i < 2 ? NORMAL_STROKE : DOTTED_STROKE);
                if (i >= 2 || config.connectPoints) { graphics.drawLine(x, y, x + lineLength, y); }
                if (i < 2) { graphics.fillOval(x + lineLength / 2 - 2, y - 2, 5, 5); }
            }
            graphics.setColor(config.textColor);
            graphics.drawString(labels[i], x + lineLength + 5, y + 4);
            x += widths[i] + padding;
        }
    }

    public void drawVolumeBars(Graphics2D graphics, Config config, Rectangle pa, Bounds bounds, List<Datapoint> volumes, Datapoint hoveredPoint) {
        var originalClip = graphics.getClip();
        graphics.setClip(pa.x, pa.y, pa.width, pa.height);
        Color high= new Color(config.highColor.getRed(), config.highColor.getGreen(), config.highColor.getBlue(), 128);
        var low = new Color(config.lowColor.getRed(), config.lowColor.getGreen(), config.lowColor.getBlue(), 128);
        for (Datapoint v : volumes) {
            int x1 = bounds.toX(pa, v.time), x2 = bounds.toX(pa, v.time + Constants.HOUR_SECONDS);
            int y1 = bounds.toY2(pa, v.highVolume + v.lowVolume), y2 = bounds.toY2(pa, v.lowVolume);
            int y3 = bounds.toY2(pa, 0);
            graphics.setColor(high);
            graphics.fillRect(x1, y1, x2-x1, y2-y1);
            graphics.setColor(low);
            graphics.fillRect(x1, y2, x2-x1, y3-y2);
            if (hoveredPoint == v) {
                graphics.setColor(Color.WHITE); graphics.setStroke(THICK_STROKE);
                graphics.drawRect(x1, y1, x2 - x1, y3 - y1);
            } else {
                graphics.setColor(Color.GRAY); graphics.setStroke(THIN_STROKE);
                graphics.drawRect(x1, y1, x2 - x1, y3 - y1);
            }
        }
        graphics.setClip(originalClip); // restore original clip
    }

    public void drawTxsDatapoints(Graphics2D graphics,
                                  Rectangle pa,
                                  Bounds bounds,
                                  List<Datapoint> txDatapoints,
                                  Datapoint hoveredPoint,
                                  Config config) {
        if (txDatapoints == null || txDatapoints.isEmpty()) return;

        var originalClip = graphics.getClip();
        graphics.setClip(pa.x, pa.y, pa.width, pa.height);

        int circleSize = 24, radius = circleSize / 2;

        graphics.setFont(graphics.getFont().deriveFont(Font.BOLD, 14f));
        var metrics = graphics.getFontMetrics();

        for (Datapoint d : txDatapoints) {
            if (d.time < bounds.xMin || d.time > bounds.xMax) { continue; }

            int x = bounds.toX(pa, d.time), y = bounds.toY(pa, d.price);

            graphics.setColor(d.isLow ? config.lowColor : config.highColor);
            graphics.fillOval(x - radius, y - radius, circleSize, circleSize);

            graphics.setColor(hoveredPoint == d ? Color.BLACK : Color.WHITE); graphics.setStroke(new BasicStroke(2.0f));
            graphics.drawOval(x - radius, y - radius, circleSize, circleSize);

            String text = d.isLow ? "B" : "S";
            int textWidth = metrics.stringWidth(text), textHeight = metrics.getAscent();

            graphics.setColor(Color.WHITE);
            graphics.drawString(text,
                    x - textWidth / 2,
                    y + textHeight / 3); // Adjust vertical centering
        }

        graphics.setClip(originalClip);
    }
}
