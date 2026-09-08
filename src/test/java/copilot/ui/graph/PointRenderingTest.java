package copilot.ui.graph;

import copilot.ui.graph.model.*;
import org.junit.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import static org.junit.Assert.*;

public class PointRenderingTest {
    @Test public void sharedRendererPreservesPixelsAndGraphicsState() {
        Bounds bounds = new Bounds(100, 7300, 0, 100, 0, 100);
        Rectangle area = new Rectangle(10, 10, 230, 100);
        List<Datapoint> points = new ArrayList<>();
        for (Datapoint.Type type : Datapoint.Type.values()) {
            for (int time : new int[]{99, 100, 1100, 4000, 7300, 7301}) {
                for (int price : new int[]{-10, 0, 50, 100, 110}) {
                    points.add(new Datapoint(time, price, true, type));
                }
            }
        }
        for (boolean starts : new boolean[]{false, true}) {
            for (int size : new int[]{0, 1, 5, 8, 17}) {
                for (boolean empty : new boolean[]{false, true}) {
                    BufferedImage expected = new BufferedImage(260, 130, BufferedImage.TYPE_INT_ARGB);
                    BufferedImage actual = new BufferedImage(260, 130, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D before = expected.createGraphics(), after = actual.createGraphics();
                    try {
                        for (Graphics2D g : new Graphics2D[]{before, after}) {
                            g.setClip(20, 20, 150, 80);
                            g.setColor(Color.BLUE);
                            g.setStroke(new BasicStroke(3));
                        }
                        List<Datapoint> data = empty ? Collections.emptyList() : points;
                        GraphRenderer renderer = new GraphRenderer(after, null, area, bounds);
                        if (starts) {
                            drawStartPoints(before, area, bounds, data, Color.RED, size);
                            renderer.drawStartPoints(data, Color.RED, size);
                        } else {
                            drawPoints(before, area, bounds, data, Color.RED, size);
                            renderer.drawPoints(data, Color.RED, size);
                        }
                        assertArrayEquals(expected.getRGB(0, 0, 260, 130, null, 0, 260),
                                actual.getRGB(0, 0, 260, 130, null, 0, 260));
                        assertEquals(before.getClipBounds(), after.getClipBounds());
                        assertEquals(before.getColor(), after.getColor());
                        assertEquals(before.getStroke(), after.getStroke());
                    } finally {
                        before.dispose();
                        after.dispose();
                    }
                }
            }
        }
    }

    // Independent reference implementation from before the shared point-rendering refactor.
    public void drawStartPoints(Graphics2D plotAreaG2,
                                Rectangle pa,
                                Bounds bounds, List<Datapoint> startPoints,
                                Color color,
                                int size
    ) {
        if (startPoints.isEmpty()) return;

        plotAreaG2.setColor(color);
        var originalClip = plotAreaG2.getClip();
        plotAreaG2.setClip(pa.x, pa.y, pa.width, pa.height);

        for (Datapoint d : startPoints) {
            if (d.time < bounds.xMin || d.time > bounds.xMax) { continue; }
            int x = bounds.toX(pa,d.time), y = bounds.toY(pa,d.price);

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
        var originalClip = g2d.getClip();
        g2d.setClip(pa.x, pa.y, pa.width, pa.height);

        // Draw each point as a filled oval
        for (Datapoint d : datapoints) {
            if (d.time < bounds.xMin || d.time > bounds.xMax) { continue; }
            int x = bounds.toX(pa,d.time), y = bounds.toY(pa,d.price), ovalX = x - size / 2;
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

}
