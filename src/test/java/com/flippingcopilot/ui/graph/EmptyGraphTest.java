package com.flippingcopilot.ui.graph;

import com.flippingcopilot.ui.graph.model.*;
import org.junit.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.time.Instant;
import static org.junit.Assert.*;

public class EmptyGraphTest {
    private Data emptyData() throws Exception {
        Data data = new Data();
        for (Field field : Data.class.getFields()) {
            if (field.getType() == int[].class) field.set(data, new int[0]);
            if (field.getType() == long[].class) field.set(data, new long[0]);
        }
        return data;
    }

    private void assertRenderable(Bounds bounds) {
        assertTrue(bounds.xMax > bounds.xMin);
        assertTrue(bounds.yMax > bounds.yMin);
        assertTrue(bounds.yDelta() > 0);
        assertTrue(bounds.y2Delta() > 0);
        Graphics2D graphics = new BufferedImage(500, 300, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            new RenderV2().drawYAxisLabels(graphics, new Config(), new Rectangle(50, 10, 400, 200),
                    bounds::toY, AxisCalculator.calculateNumericAxis(bounds.yMin, bounds.yMax, bounds.yDelta(), 18, 28), false);
        } finally { graphics.dispose(); }
    }

    @Test public void emptyHistoryHasSafeHomeAndPresetBounds() throws Exception {
        for (Data data : new Data[]{emptyData(), Data.decodeProto(new byte[0])}) {
            DataManager manager = new DataManager(data, null, com.flippingcopilot.model.Market.OSRS);
            assertFalse(manager.hasPriceData());
            assertRenderable(manager.maxBounds);
            assertRenderable(manager.calculateHomeBounds());
            assertRenderable(manager.calculateSpanBounds(Constants.DAY_SECONDS, 0));
        }
    }

    @Test public void emptyTimeWindowDoesNotKeepSentinelBounds() throws Exception {
        Data data = emptyData();
        data.low1hTimes = new int[]{(int) Instant.now().getEpochSecond()};
        data.low1hPrices = new long[]{100};
        DataManager manager = new DataManager(data, null, com.flippingcopilot.model.Market.OSRS);
        assertTrue(manager.hasPriceData());
        assertEquals(97, manager.maxBounds.yMin);
        assertEquals(103, manager.maxBounds.yMax);
        assertRenderable(manager.calculateBounds(point -> false));
    }
}
