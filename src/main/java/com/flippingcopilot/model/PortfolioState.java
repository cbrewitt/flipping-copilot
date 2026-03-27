package com.flippingcopilot.model;

import lombok.Value;

import java.util.Collections;
import java.util.Map;

@Value
public class PortfolioState {
    boolean loaded;
    Map<Integer, PortfolioItemCardData> itemCardDataByItemId;
    PortfolioSummaryData summaryData;

    public static PortfolioState empty() {
        return new PortfolioState(false, Collections.emptyMap(), new PortfolioSummaryData(0L, 0L, 0L, 0L));
    }
}
