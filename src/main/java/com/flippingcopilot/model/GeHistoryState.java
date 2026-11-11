package com.flippingcopilot.model;

import lombok.Value;

import java.util.*;

@Value
public class GeHistoryState {
    boolean loaded;
    List<GeHistoryRow> rows;
    int capturedAt;
    Long accountHash;

    public static GeHistoryState empty() {
        return new GeHistoryState(false, Collections.emptyList(), 0, null);
    }
}
