package com.flippingcopilot.ui.graph;
import com.flippingcopilot.ui.graph.model.Bounds;
import lombok.NoArgsConstructor;

import java.awt.*;

@NoArgsConstructor
public class PlotArea {

    // this is the padding between the plot area and the edge of the panel
    public final int leftPadding = 80;
    public final int topPadding = 100;
    public final int rightPadding = 20;
    public final int bottomPadding = 50;

    public int w;
    public int h;

    public Bounds bounds;

    public int timeDeltaToXDelta(int d) {
        return  (int)((long)w * (long)d / (long) bounds.xDelta());
    }
    public int priceDeltaToYDelta(int d) {
        return  (int)((long)h * (long)d / (long) bounds.yDelta());
    }

    public int timeToX(int t) {
        return  (int)(((long)w * (long)(t - bounds.xMin)) / bounds.xDelta());
    }

    public int priceToY(int p) {
        return (int)(((long)h * (long)(bounds.yMax - p)) / bounds.yDelta());
    }

    public Point relativePoint(Point p) {
        return new Point( p.x - leftPadding, p.y - topPadding);
    }

    public boolean pointInPlotArea(Point p) {
        return p.x <= w && p.y <= h;
    }
}
