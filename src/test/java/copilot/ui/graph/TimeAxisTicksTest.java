package copilot.ui.graph;

import copilot.ui.graph.model.*;
import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;

public class TimeAxisTicksTest {
    @Test public void sharedIntradayLoopPreservesTickOrderAndBoundaries() {
        Random random = new Random(75);
        for (int offset : new int[]{-43200, -12600, 0, 19800, 20700, 50400}) {
            for (int days = 0; days <= 90; days++) {
                for (int extra : new int[]{0, 1, 43200, 86399}) {
                    Bounds bounds = new Bounds();
                    bounds.xMin = 1700000000 + random.nextInt(86400);
                    bounds.xMax = bounds.xMin + days * 86400 + extra;
                    TimeAxis expected = ReferenceTimeAxis.calculateTimeAxis(bounds, offset);
                    TimeAxis actual = AxisCalculator.calculateTimeAxis(bounds, offset);
                    assertArrayEquals(expected.dateOnlyTickTimes, actual.dateOnlyTickTimes);
                    assertArrayEquals(expected.timeOnlyTickTimes, actual.timeOnlyTickTimes);
                    assertArrayEquals(expected.gridOnlyTickTimes, actual.gridOnlyTickTimes);
                }
            }
        }
    }
}
