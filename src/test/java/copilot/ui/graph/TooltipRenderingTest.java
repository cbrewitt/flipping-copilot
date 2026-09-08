package copilot.ui.graph;

import copilot.ui.graph.model.*;
import org.junit.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import static org.junit.Assert.*;

public class TooltipRenderingTest {
    @Test public void sharedTooltipRenderingMatchesOriginalPixels() {
        Rectangle area = new Rectangle(20, 15, 420, 200);
        Bounds bounds = new Bounds(100, 3700, 0, 100, 0, 100);
        Config config = new Config();
        for (Datapoint.Type type : Datapoint.Type.values()) {
            for (boolean low : new boolean[]{false, true}) {
                for (int time : new int[]{100, 2000, 3700}) {
                    for (int price : new int[]{0, 50, 100}) {
                        Datapoint point = new Datapoint(time, price, low, type);
                        point.qty = -12345;
                        point.lowVolume = 20;
                        point.highVolume = 30;
                        BufferedImage expected = new BufferedImage(480, 260, BufferedImage.TYPE_INT_ARGB);
                        BufferedImage actual = new BufferedImage(480, 260, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D before = expected.createGraphics(), after = actual.createGraphics();
                        try {
                            if (type == Datapoint.Type.VOLUME_1H) {
                                ReferenceTooltip.drawVolume(before, config, area, bounds, point);
                                DatapointTooltip.drawVolume(after, config, area, bounds, point);
                            } else {
                                ReferenceTooltip.draw(before, config, area, bounds, point);
                                DatapointTooltip.draw(after, config, area, bounds, point);
                            }
                            assertArrayEquals(type + " time=" + time + " price=" + price,
                                    expected.getRGB(0, 0, 480, 260, null, 0, 480), actual.getRGB(0, 0, 480, 260, null, 0, 480));
                            assertEquals(before.getColor(), after.getColor());
                            assertEquals(before.getFont(), after.getFont());
                        } finally {
                            before.dispose();
                            after.dispose();
                        }
                    }
                }
            }
        }
    }
}
