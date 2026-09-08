package copilot.ui.graph;

import copilot.manager.GraphSettings;
import copilot.ui.graph.model.*;
import org.junit.Test;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import static org.junit.Assert.*;

public class GraphContextTest {
    @Test public void plotContextsPreserveCompleteGraphPixels() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (boolean predictions : new boolean[]{false, true}) {
                for (boolean connect : new boolean[]{false, true}) {
                    for (int width : new int[]{500, 1000}) {
                        Config config = new Config(); config.connectPoints = connect;
                        GraphSettings manager = new GraphSettings(null, null) {
                            @Override public synchronized Config getConfig() { return config; }
                        };
                        Data data = new Data(); data.itemId = 1;
                        DataManager points = new DataManager(data, null);
                        int start = 1700000000;
                        for (int i = 0; i < 5; i++) {
                            int time = start + i * 3600;
                            points.lowDatapoints.add(new Datapoint(time, 20 + i * 5, true, Datapoint.Type.HOUR_AVERAGE));
                            points.highDatapoints.add(new Datapoint(time, 50 + i * 3, false, Datapoint.Type.INSTA_SELL_BUY));
                            points.volumes.add(Datapoint.newVolumeDatapoint(time, 10 + i * 2, 30));
                        }
                        points.flipEntryDatapoints.add(Datapoint.newBuyTx(start + 3600, 25, 2));
                        points.flipCloseDatapoints.add(Datapoint.newSellTx(start + 10800, 60, 2));
                        if (predictions) {
                            data.predictionTimes = new int[]{start + 14400, start + 18000};
                            data.predictionLowIQRLower = new long[]{30, 35};
                            data.predictionLowIQRUpper = new long[]{50, 60};
                            data.predictionHighIQRLower = new long[]{50, 55};
                            data.predictionHighIQRUpper = new long[]{70, 80};
                            for (int i = 0; i < 2; i++) {
                                points.predictionLowDatapoints.add(new Datapoint(data.predictionTimes[i], 40 + i * 5,
                                        data.predictionLowIQRLower[i], data.predictionLowIQRUpper[i], true));
                                points.predictionHighDatapoints.add(new Datapoint(data.predictionTimes[i], 60 + i * 5,
                                        data.predictionHighIQRLower[i], data.predictionHighIQRUpper[i], false));
                            }
                        }
                        Bounds bounds = new Bounds(start, start + 21600, 0, 100, 0, 100);
                        ReferenceGraphPanel before = new ReferenceGraphPanel(manager);
                        GraphPanel after = new GraphPanel(manager);
                        before.dataManager = points; after.dataManager = points;
                        before.bounds = bounds; after.bounds = bounds;
                        assertArrayEquals(render(before, width), render(after, width));
                    }
                }
            }
        });
    }

    private static int[] render(JPanel panel, int width) {
        panel.setSize(width, 500);
        BufferedImage image = new BufferedImage(width, 500, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try { panel.paint(graphics); }
        finally { graphics.dispose(); }
        return image.getRGB(0, 0, width, 500, null, 0, width);
    }
}
