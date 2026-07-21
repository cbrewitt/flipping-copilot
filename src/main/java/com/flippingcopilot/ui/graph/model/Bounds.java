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
        yMax = Long.MAX_VALUE;
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

    public int toX(Rectangle pa, long xValue) {
        if (xDelta() == 0) return pa.width / 2 + pa.x;
        return pa.x + scaleToPixels(xValue, xMin, xMax, pa.width);
    }

    public int toY(Rectangle pa, long yValue) {
        if (yDelta() == 0) return pa.height / 2 + pa.y;
        return pa.y + pa.height - scaleToPixels(yValue, yMin, yMax, pa.height);
    }

    public int toW(Rectangle pa, long xDelta) {
        if (xDelta() == 0) return 0;
        return scaleToPixels(xDelta, 0, xDelta(), pa.width);
    }

    public int toY2(Rectangle pa, long yValue) {
        if (y2Delta() == 0) return pa.height / 2 + pa.y;
        return pa.y + pa.height - scaleToPixels(yValue, y2Min, y2Max, pa.height);
    }

    private static int scaleToPixels(long value, long min, long max, int pixelRange) {
        double range = (double) max - min;
        if (range == 0) return pixelRange / 2;
        double scaled = ((value - (double) min) / range) * pixelRange;
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, scaled));
    }
}
