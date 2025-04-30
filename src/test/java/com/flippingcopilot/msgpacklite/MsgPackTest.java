package com.flippingcopilot.msgpacklite;

import com.flippingcopilot.model.Suggestion;
import com.flippingcopilot.ui.graph.model.Data;
import org.junit.Test;


import static org.junit.Assert.*;

public class MsgPackTest {

    @Test
    public void testSerializeDeserializeSuggestion() {
        // Create test data
        Data graphData = new Data();
        graphData.name = "Test Item";
        graphData.dailyVolume = 12345.67;
        graphData.low1hTimes = new int[]{1000, 2000, 3000};
        graphData.low1hPrices = new int[]{100, 200, 300};
        graphData.high1hTimes = new int[]{1100, 2100, 3100};
        graphData.high1hPrices = new int[]{110, 210, 310};
        graphData.predictionTimes = new int[]{4000, 5000, 6000};
        graphData.predictionLowMeans = new int[]{400, 500, 600};

        Suggestion suggestion = new Suggestion(
                "buy",
                1,
                12345,
                5000,
                10,
                "Dragon Bones",
                101,
                "Buy suggestion message",
                graphData
        );

        // Test serialization and deserialization
        byte[] serialized = MsgpackSerializer.serialize(suggestion);
        assertNotNull(serialized);
        assertTrue(serialized.length > 0);

        // Deserialize
        Suggestion deserialized = MsgpackDeserializer.deserialize(serialized, Suggestion.class);

        // Verify all fields were correctly serialized and deserialized
        assertEquals("buy", deserialized.getType());
        assertEquals(1, deserialized.getBoxId());
        assertEquals(12345, deserialized.getItemId());
        assertEquals(5000, deserialized.getPrice());
        assertEquals(10, deserialized.getQuantity());
        assertEquals("Dragon Bones", deserialized.getName());
        assertEquals(101, deserialized.getId());
        assertEquals("Buy suggestion message", deserialized.getMessage());

        // Verify nested Data object
        assertNotNull(deserialized.getGraphData());
        assertEquals("Test Item", deserialized.getGraphData().name);
        assertEquals(12345.67, deserialized.getGraphData().dailyVolume, 0.001);

        // Verify arrays in Data object
        assertArrayEquals(graphData.low1hTimes, deserialized.getGraphData().low1hTimes);
        assertArrayEquals(graphData.low1hPrices, deserialized.getGraphData().low1hPrices);
        assertArrayEquals(graphData.high1hTimes, deserialized.getGraphData().high1hTimes);
        assertArrayEquals(graphData.high1hPrices, deserialized.getGraphData().high1hPrices);
        assertArrayEquals(graphData.predictionTimes, deserialized.getGraphData().predictionTimes);
        assertArrayEquals(graphData.predictionLowMeans, deserialized.getGraphData().predictionLowMeans);
    }

    @Test
    public void testNullFieldsInSuggestion() {
        // Create suggestion with null fields
        Suggestion suggestion = new Suggestion(
                "sell",
                2,
                54321,
                3000,
                5,
                "Abyssal Whip",
                202,
                null,  // Null message
                null   // Null graph data
        );

        // Test serialization and deserialization
        byte[] serialized = MsgpackSerializer.serialize(suggestion);
        assertNotNull(serialized);

        Suggestion deserialized = MsgpackDeserializer.deserialize(serialized, Suggestion.class);

        // Verify fields
        assertEquals("sell", deserialized.getType());
        assertEquals(2, deserialized.getBoxId());
        assertEquals(54321, deserialized.getItemId());
        assertEquals(3000, deserialized.getPrice());
        assertEquals(5, deserialized.getQuantity());
        assertEquals("Abyssal Whip", deserialized.getName());
        assertEquals(202, deserialized.getId());
        assertNull(deserialized.getMessage());
        assertNull(deserialized.getGraphData());
    }

    @Test
    public void testSuggestionEquals() {
        // Create two suggestions with the same key fields
        Data graphData1 = new Data();
        graphData1.name = "Test Item";

        Data graphData2 = new Data();
        graphData2.name = "Test Item";

        Suggestion suggestion1 = new Suggestion(
                "abort",
                4,
                12345,
                0,
                0,
                "Nature Rune",
                404,
                "Abort message",
                graphData1
        );

        Suggestion suggestion2 = new Suggestion(
                "abort",
                4,
                12345,
                1000, // Different price
                5,    // Different quantity
                "Nature Rune",
                505,  // Different id
                "Different message",
                graphData2
        );

        // Serialize and deserialize both
        byte[] serialized1 = MsgpackSerializer.serialize(suggestion1);
        byte[] serialized2 = MsgpackSerializer.serialize(suggestion2);

        Suggestion deserialized1 = MsgpackDeserializer.deserialize(serialized1, Suggestion.class);
        Suggestion deserialized2 = MsgpackDeserializer.deserialize(serialized2, Suggestion.class);

        // Check that equals method works as expected
        assertTrue(deserialized1.equals(deserialized2));

        // Check that non-key fields were correctly preserved despite not being part of equality
        assertEquals(0, deserialized1.getPrice());
        assertEquals(1000, deserialized2.getPrice());
        assertEquals(0, deserialized1.getQuantity());
        assertEquals(5, deserialized2.getQuantity());
        assertEquals(404, deserialized1.getId());
        assertEquals(505, deserialized2.getId());
        assertEquals("Abort message", deserialized1.getMessage());
        assertEquals("Different message", deserialized2.getMessage());
    }

    @Test
    public void testToMessage() {
        // Test the toMessage formatting for different suggestion types
        Data graphData = new Data();

        // Buy suggestion
        Suggestion buySuggestion = new Suggestion(
                "buy",
                5,
                55555,
                1000000,
                1000,
                "Dragon Dagger",
                606,
                null,
                graphData
        );

        byte[] serialized = MsgpackSerializer.serialize(buySuggestion);
        Suggestion deserialized = MsgpackDeserializer.deserialize(serialized, Suggestion.class);

        String message = deserialized.toMessage();
        assertTrue(message.contains("Buy"));
        assertTrue(message.contains("1,000"));
        assertTrue(message.contains("Dragon Dagger"));
        assertTrue(message.contains("1,000,000"));

        // Sell suggestion
        Suggestion sellSuggestion = new Suggestion(
                "sell",
                6,
                66666,
                5000000,
                500,
                "Abyssal Whip",
                707,
                null,
                graphData
        );

        serialized = MsgpackSerializer.serialize(sellSuggestion);
        deserialized = MsgpackDeserializer.deserialize(serialized, Suggestion.class);

        message = deserialized.toMessage();
        assertTrue(message.contains("Sell"));
        assertTrue(message.contains("500"));
        assertTrue(message.contains("Abyssal Whip"));
        assertTrue(message.contains("5,000,000"));
    }
}