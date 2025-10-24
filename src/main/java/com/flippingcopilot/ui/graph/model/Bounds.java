package com.flippingcopilot.ui.graph.model;

import lombok.AllArgsConstructor;

import java.awt.*;
import java.time.Instant;

@AllArgsConstructor
public class Bounds {
    public int xMin;
    public int xMax;
    public long yMin;
    public long yMax;
    public long y2Min;
    public long y2Max;

    public Bounds() {
        xMax = (int) Instant.now().getEpochSecond();
        xMin = xMax - 24*60*60*4;
        yMax = Integer.MAX_VALUE;
        yMin = 0;
    }

    public long y2Delta() {
        return y2Max - y2Min;
    }

    public long yDelta() {
        return yMax - yMin;
    }

    public int xDelta() {
        return xMax - xMin;
    }

    public int xMid() {
        return xMin + (xMax - xMin)/2;
    }

    public Bounds copy() {
        return new Bounds(xMin, xMax, yMin, yMax, y2Min, y2Max);
    }

    public Point toPoint(Rectangle pa, int xValue, long yValue) {
        return new Point(toX(pa, xValue), toY(pa, yValue));
    }

    public int toX(Rectangle pa, long xValue) {
        if (xDelta() == 0) return pa.width / 2 + pa.x;
        return pa.x + (int) (((xValue - xMin) * pa.width) / xDelta());
    }

    public int toY(Rectangle pa, long yValue) {
        if (yDelta() == 0) return pa.height / 2 + pa.y;
        return (pa.y + pa.height) - (int)((yValue - yMin) * pa.height / yDelta());
    }

    public int toW(Rectangle pa, long xDelta) {
        return (int) (xDelta * pa.width / xDelta());
    }

    public int toY2(Rectangle pa, long yValue) {
        if (y2Delta() == 0) return pa.height / 2 + pa.y;
        return (pa.y + pa.height) - (int)((yValue - y2Min) * pa.height / y2Delta());
    }
}
