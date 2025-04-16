package com.flippingcopilot.ui.graph.model;

import lombok.AllArgsConstructor;

import java.time.Instant;

@AllArgsConstructor
public class Bounds {
    public int xMin;
    public int xMax;
    public int yMin;
    public int yMax;

    public Bounds() {
        xMax = (int) Instant.now().getEpochSecond();
        xMin = xMax - 24*60*60*4;
        yMax = Integer.MAX_VALUE;
        yMin = 0;
    }

    public int yDelta() {
        return yMax - yMin;
    }

    public int xDelta() {
        return xMax - xMin;
    }

    public Bounds copy() {
        return new Bounds(xMin, xMax, yMin, yMax);
    }
}
