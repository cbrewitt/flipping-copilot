package com.flippingcopilot.ui.graph;

import com.flippingcopilot.ui.graph.model.Bounds;
import com.flippingcopilot.ui.graph.model.Data;
import com.flippingcopilot.ui.graph.model.Datapoint;
import lombok.Getter;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;


@Getter
public class DataManager {
    public static final int FIVE_MIN_SECONDS = 60*5;
    public static final int HOUR_SECONDS = 60*60;

    public final List<Datapoint> highDatapoints = new ArrayList<>();
    public final List<Datapoint> lowDatapoints = new ArrayList<>();
    public final List<Datapoint> predictionDatapoints = new ArrayList<>();
    private final Data data;


    public DataManager(Data data) {
        this.data = data;
        setupGraphDatapoints();
    }

    public Datapoint findClosestPoint(Point mousePos, int hoverRadius, PlotArea pa) {
        if (mousePos == null) return null;

        Datapoint closest = null;
        double minDistance = hoverRadius;

        for (Datapoint d : datapoints) {
            // Update screen position
            int x = pa.timeToX(d.time);
            int y = pa.priceToY(d.price);
            d.setScreenPosition(new Point(x, y));

            double distance = mousePos.distance(d.getScreenPosition());
            if (distance < minDistance) {
                minDistance = distance;
                closest = d;
            }
        }

        return closest;
    }

    public Bounds calculateMaxBounds() {
        return calculateBounds(datapoints);
    }

    public Bounds calculateBounds(List<Datapoint> datapoints) {
        Bounds b = new Bounds();

        b.xMin = Integer.MAX_VALUE;
        b.xMax = Integer.MIN_VALUE;
        b.yMax =  Integer.MIN_VALUE;
        b.yMin = Integer.MAX_VALUE;

        for (Datapoint d : datapoints) {
            b.xMin = Math.min(b.xMin, d.time);
            b.xMax = Math.max(b.xMax, d.time);

            // Update price bounds
            b.yMin = Math.min(b.yMin, d.price);
            b.yMax= Math.max(b.yMax, d.price);

            // Check IQR bounds for prediction points
            if (d.type == Datapoint.Type.PREDICTION) {
                if (d.iqrLower != null) {
                    b.yMin = Math.min(b.yMin, d.iqrLower);
                }
                if (d.iqrUpper != null) {
                    b.yMax = Math.max(b.yMax, d.iqrUpper);
                }
            }
        }
        int pricePadding = (int) (0.05 * b.yDelta());
        if (pricePadding < 1) pricePadding = 1;

        b.yMin = Math.max(0, b.yMin - pricePadding);
        b.yMax+= pricePadding;

        return b;
    }

    /**
     * Create all data points that will be hoverable
     */
    private void setupGraphDatapoints() {
        highDatapoints.clear();
        lowDatapoints.clear();
        predictionDatapoints.clear();


        // here we combine the hour / 5min / latest wiki price data points into a continuous dataset where the
        // hour points transition into the 5min points that transition into the latest points. So we get increasing
        // granularity,

        for (int i = 0; i < data.lowLatestTimes.length; i++) {
            lowDatapoints.add(new Datapoint(data.lowLatestTimes[i], data.lowLatestPrices[i], true, Datapoint.Type.INSTA_SELL_BUY));
        }
        int fiveMinCut;
        if (!lowDatapoints.isEmpty()) {
            fiveMinCut = FIVE_MIN_SECONDS * (lowDatapoints.get(0).time / FIVE_MIN_SECONDS)  + FIVE_MIN_SECONDS;
            lowDatapoints.removeIf((i) -> i.time < fiveMinCut);
        } else {
            fiveMinCut = Integer.MAX_VALUE;
        }
        for (int i = data.low5mTimes.length-1; i >= 0; i--) {
            if (data.lowLatestTimes[i] < fiveMinCut) {
                lowDatapoints.add(0, new Datapoint(data.low5mTimes[i], data.low5mPrices[i], true, Datapoint.Type.INSTA_SELL_BUY));
            }
        }
        int oneHourCut;
        if (!lowDatapoints.isEmpty()) {
            oneHourCut = HOUR_SECONDS * (lowDatapoints.get(0).time / HOUR_SECONDS)  + HOUR_SECONDS;
            lowDatapoints.removeIf((i) -> i.time < oneHourCut);
        } else {
            oneHourCut = Integer.MAX_VALUE;
        }
        for (int i = data.low1hTimes.length-1; i >= 0; i--) {
            if (data.lowLatestTimes[i] < oneHourCut) {
                lowDatapoints.add(0, new Datapoint(data.low1hTimes[i], data.low1hPrices[i], true, Datapoint.Type.INSTA_SELL_BUY));
            }
        }





        for (int i = 0; i < data.predictionTimes.length; i++) {
            predictionDatapoints.add(new Datapoint(
                    data.predictionTimes[i],
                    data.predictionLowMeans[i],
                    data.predictionLowIQRLower[i],
                    data.predictionLowIQRUpper[i],
                    true
            ));
            predictionDatapoints.add(new Datapoint(
                    data.predictionTimes[i],
                    data.predictionHighMeans[i],
                    data.predictionHighIQRLower[i],
                    data.predictionHighIQRUpper[i],
                    false
            ));
        }
    }
}