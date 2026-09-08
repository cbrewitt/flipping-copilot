package copilot.ui.graph;

import org.junit.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import static org.junit.Assert.*;

public class ZoomButtonsTest {
    @Test public void buttonsKeepTheirPositionsPixelsAndHoverHistory() {
        ZoomHandler current = new ZoomHandler();
        ReferenceZoomHandler previous = new ReferenceZoomHandler();
        for (int width : new int[]{720, 360, 100, 1000}) {
            Rectangle area = new Rectangle(40, 40, width, 180);
            for (Point mouse : new Point[]{null, new Point(width + 25, 55), new Point(width, 55),
                    new Point(width - 40, 55), new Point(width - 100, 55), new Point(width - 200, 55)}) {
                BufferedImage expected = new BufferedImage(1100, 300, BufferedImage.TYPE_INT_ARGB);
                BufferedImage actual = new BufferedImage(1100, 300, BufferedImage.TYPE_INT_ARGB);
                Graphics2D oldGraphics = expected.createGraphics(), newGraphics = actual.createGraphics();
                previous.drawButtons(oldGraphics, area, mouse);
                current.drawButtons(newGraphics, area, mouse);
                oldGraphics.dispose(); newGraphics.dispose();
                assertArrayEquals("width=" + width + " mouse=" + mouse,
                        expected.getRGB(0, 0, 1100, 300, null, 0, 1100),
                        actual.getRGB(0, 0, 1100, 300, null, 0, 1100));
                for (int i = 0; i < current.presets.size(); i++) {
                    assertEquals(previous.presets.get(i).buttonRect, current.presets.get(i).buttonRect);
                }
            }
        }
    }
}
