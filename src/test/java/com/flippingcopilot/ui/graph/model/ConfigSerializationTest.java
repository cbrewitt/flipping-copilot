package com.flippingcopilot.ui.graph.model;

import com.google.gson.Gson;
import org.junit.Test;

import java.awt.Color;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfigSerializationTest {

    private final Gson gson = new Gson();

    @Test
    public void indicatorTogglesDefaultToOff() {
        Config c = new Config();
        assertFalse(c.isShowEma());
        assertFalse(c.isShowBollinger());
    }

    @Test
    public void indicatorTogglesSurviveJsonRoundTrip() {
        Config c = new Config();
        c.setShowEma(true);

        Config loaded = gson.fromJson(gson.toJson(c), Config.class);

        assertTrue(loaded.isShowEma());
        assertFalse(loaded.isShowBollinger());
    }

    @Test
    public void legacyConfigJsonWithoutToggleFieldsLoadsWithTogglesOff() {
        // simulates a price_graph_config.json written before this feature existed
        Config loaded = gson.fromJson("{\"connectPoints\":true}", Config.class);
        assertFalse(loaded.isShowEma());
        assertFalse(loaded.isShowBollinger());
    }

    @Test
    public void indicatorColorsRoundTripAndDefault() {
        Config c = new Config();
        assertEquals(new Color(34, 197, 94, 120), c.getEmaUpColor());
        assertEquals(new Color(239, 68, 68, 120), c.getEmaDownColor());
        assertEquals(new Color(148, 163, 184, 45), c.getBollingerCloudColor());
        Config loaded = gson.fromJson("{\"connectPoints\":true}", Config.class);
        assertEquals(new Color(34, 197, 94, 120), loaded.getEmaUpColor());
    }
}
