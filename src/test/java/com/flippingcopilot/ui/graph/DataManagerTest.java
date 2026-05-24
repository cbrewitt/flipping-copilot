package com.flippingcopilot.ui.graph;

import com.flippingcopilot.model.VisualizeFlipResponse;
import com.flippingcopilot.ui.graph.model.Bounds;
import com.flippingcopilot.ui.graph.model.Constants;
import com.flippingcopilot.ui.graph.model.Data;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DataManagerTest {

    private static final int T = 1_700_000_000;

    @Test
    public void calculateHomeBoundsExpandsShortFlipWindowToLeft() {
        DataManager dataManager = new DataManager(graphData(), shortFlipResponse());

        Bounds bounds = dataManager.calculateHomeBounds();

        assertEquals(2 * Constants.DAY_SECONDS, bounds.xMax - bounds.xMin);
        assertEquals(T + Constants.TEN_MIN_SECONDS + Constants.FIVE_MIN_SECONDS, bounds.xMax);
    }

    private static VisualizeFlipResponse shortFlipResponse() {
        VisualizeFlipResponse response = new VisualizeFlipResponse();
        response.buyTimes = new int[]{T};
        response.buyPrices = new int[]{100};
        response.buyVolumes = new int[]{10};
        response.sellTimes = new int[]{T + Constants.TEN_MIN_SECONDS};
        response.sellPrices = new int[]{110};
        response.sellVolumes = new int[]{10};
        response.graphData = graphData();
        return response;
    }

    private static Data graphData() {
        Data data = new Data();
        data.itemId = 2;
        data.name = "Test item";
        data.buyPrice = 100;
        data.sellPrice = 110;
        data.dailyVolume = 1_000;

        data.lowLatestTimes = new int[0];
        data.lowLatestPrices = new int[0];
        data.highLatestTimes = new int[0];
        data.highLatestPrices = new int[0];

        data.low5mTimes = new int[0];
        data.low5mPrices = new int[0];
        data.high5mTimes = new int[0];
        data.high5mPrices = new int[0];

        data.low1hTimes = new int[]{T - 2 * Constants.DAY_SECONDS, T};
        data.low1hPrices = new int[]{95, 100};
        data.high1hTimes = new int[]{T - 2 * Constants.DAY_SECONDS, T};
        data.high1hPrices = new int[]{105, 110};

        data.volume1hTimes = new int[]{T - 2 * Constants.DAY_SECONDS, T};
        data.volume1hLows = new int[]{10, 10};
        data.volume1hHighs = new int[]{10, 10};
        data.volume5mTimes = new int[0];
        data.volume5mLows = new int[0];
        data.volume5mHighs = new int[0];

        return data;
    }
}
