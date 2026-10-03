package com.flippingcopilot.model;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.*;

public class LatestPriceTest {

    private final Gson gson = new Gson();

    private static final String REAL_RESPONSE =
            "{\"data\":{\"4151\":{\"high\":988369,\"highTime\":1785143307,\"low\":993267,\"lowTime\":1785143148}}}";

    @Test
    public void parsesAllFourFields() {
        WikiLatestPrice p = WikiLatestPrice.fromJson(gson, REAL_RESPONSE, 4151);
        assertNotNull(p);
        assertEquals(Long.valueOf(988369L), p.getHigh());
        assertEquals(Long.valueOf(1785143307L), p.getHighTime());
        assertEquals(Long.valueOf(993267L), p.getLow());
        assertEquals(Long.valueOf(1785143148L), p.getLowTime());
    }

    @Test
    public void requestedItemMissingFromDataReturnsNull() {
        assertNull(WikiLatestPrice.fromJson(gson, REAL_RESPONSE, 26219));
    }

    @Test
    public void neverSeenItemYieldsNullFields() {
        WikiLatestPrice p = WikiLatestPrice.fromJson(gson, "{\"data\":{\"99\":{\"high\":null,\"highTime\":null,\"low\":5,\"lowTime\":7}}}", 99);
        assertNotNull(p);
        assertNull(p.getHigh());
        assertNull(p.getHighTime());
        assertEquals(Long.valueOf(5L), p.getLow());
    }

    @Test
    public void malformedOrEmptyBodiesReturnNull() {
        assertNull(WikiLatestPrice.fromJson(gson, "not json at all", 4151));
        assertNull(WikiLatestPrice.fromJson(gson, "{}", 4151));
        assertNull(WikiLatestPrice.fromJson(gson, "", 4151));
        assertNull(WikiLatestPrice.fromJson(gson, "{\"data\":[]}", 4151));
    }
}
