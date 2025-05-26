package com.flippingcopilot.ui.graph;

import com.flippingcopilot.ui.graph.model.Bounds;
import com.flippingcopilot.ui.graph.model.Constants;
import com.flippingcopilot.ui.graph.model.Data;
import com.flippingcopilot.ui.graph.model.Datapoint;
import com.flippingcopilot.util.GeTax;
import lombok.Getter;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;


@Getter
public class DataManager {

    public final List<Datapoint> highDatapoints = new ArrayList<>();
    public final List<Datapoint> lowDatapoints = new ArrayList<>();
    public final List<Datapoint> predictionLowDatapoints = new ArrayList<>();
    public final List<Datapoint> predictionHighDatapoints = new ArrayList<>();

    public final Data data;
    public double priceChange24H = 0;
    public double priceChangeWeek = 0;
    public int lastLowTime = 0;
    public int lastLowPrice = 0;
    public int lastHighTime = 0;
    public int lastHighPrice = 0;
    public long margin;
    public long tax;
    public long profit;


    public DataManager(Data data) {
        this.data = data;
        processDatapoints();
        calculateStats();
    }

    public Datapoint findClosestPoint(Point mousePos, int hoverRadius, PlotArea pa) {
        if (mousePos == null) return null;

        Datapoint closest = null;
        double minDistance = hoverRadius;

        for(List<Datapoint> datapoints : Arrays.asList(highDatapoints, lowDatapoints, predictionLowDatapoints, predictionHighDatapoints)) {
            for (Datapoint d : datapoints) {
                Point hoverPosition = d.getHoverPosition(pa);
                double distance = mousePos.distance(hoverPosition);
                if (distance < minDistance) {
                    minDistance = distance;
                    closest = d;
                }
            }
        }
        return closest;
    }


    public Bounds calculateBounds(Predicate<Datapoint> p) {
        Bounds b = new Bounds();

        b.xMin = Integer.MAX_VALUE;
        b.xMax = Integer.MIN_VALUE;
        b.yMax =  Integer.MIN_VALUE;
        b.yMin = Integer.MAX_VALUE;

        long yMean = 0;
        long n = 0;

        for(List<Datapoint> datapoints : Arrays.asList(highDatapoints, lowDatapoints, predictionLowDatapoints, predictionHighDatapoints)) {
            for (Datapoint d : datapoints) {
                if (p.test(d)) {
                    yMean = (n * yMean + (long) d.price) / (n+1);
                    n+=1;

                    b.xMin = Math.min(b.xMin, d.time);
                    b.xMax = Math.max(b.xMax, d.time);

                    // Update price bounds
                    b.yMin = Math.min(b.yMin, d.price);
                    b.yMax = Math.max(b.yMax, d.price);

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
            }
        }
        int pricePadding = (int) (0.03 * yMean);
        if (pricePadding < 1) pricePadding = 1;

        b.yMin = Math.max(0, b.yMin - pricePadding);
        b.yMax+= pricePadding;

        return b;
    }

    private void processDatapoints() {
        highDatapoints.clear();
        lowDatapoints.clear();
        predictionHighDatapoints.clear();
        predictionLowDatapoints.clear();
        
        // here we combine the hour / 5min / latest wiki price data points into a continuous dataset where hour points 
        // transition into the 5min points that transition into the latest points. So we get increasingly finer granularity.
        // We truncate the points correctly at the boundaries to ensure no overlap.

        for (int i = 0; i < data.lowLatestTimes.length; i++) {
            lowDatapoints.add(new Datapoint(data.lowLatestTimes[i], data.lowLatestPrices[i], true, Datapoint.Type.INSTA_SELL_BUY));
        }
        int fiveMinLowsCut;
        if (!lowDatapoints.isEmpty()) {
            fiveMinLowsCut = Constants.FIVE_MIN_SECONDS * (lowDatapoints.get(0).time / Constants.FIVE_MIN_SECONDS)  + Constants.FIVE_MIN_SECONDS;
            lowDatapoints.removeIf((i) -> i.time < fiveMinLowsCut);
        } else {
            fiveMinLowsCut = Integer.MAX_VALUE;
        }
        for (int i = data.low5mTimes.length-1; i >= 0; i--) {
            if (data.low5mTimes[i] < fiveMinLowsCut) {
                lowDatapoints.add(0, new Datapoint(data.low5mTimes[i], data.low5mPrices[i], true, Datapoint.Type.FIVE_MIN_AVERAGE));
            }
        }
        int oneHourLowsCut;
        if (!lowDatapoints.isEmpty()) {
            oneHourLowsCut = Constants.HOUR_SECONDS * (lowDatapoints.get(0).time / Constants.HOUR_SECONDS)  + Constants.HOUR_SECONDS;
            lowDatapoints.removeIf((i) -> i.time < oneHourLowsCut);
        } else {
            oneHourLowsCut = Integer.MAX_VALUE;
        }
        for (int i = data.low1hTimes.length-1; i >= 0; i--) {
            if (data.low1hTimes[i] < oneHourLowsCut) {
                lowDatapoints.add(0, new Datapoint(data.low1hTimes[i], data.low1hPrices[i], true, Datapoint.Type.HOUR_AVERAGE));
            }
        }

        for (int i = 0; i < data.highLatestTimes.length; i++) {
            highDatapoints.add(new Datapoint(data.highLatestTimes[i], data.highLatestPrices[i], false, Datapoint.Type.INSTA_SELL_BUY));
        }
        int fiveMinHighsCut;
        if (!highDatapoints.isEmpty()) {
            fiveMinHighsCut = Constants.FIVE_MIN_SECONDS * (highDatapoints.get(0).time / Constants.FIVE_MIN_SECONDS)  + Constants.FIVE_MIN_SECONDS;
            highDatapoints.removeIf((i) -> i.time < fiveMinHighsCut);
        } else {
            fiveMinHighsCut = Integer.MAX_VALUE;
        }
        for (int i = data.high5mTimes.length-1; i >= 0; i--) {
            if (data.high5mTimes[i] < fiveMinHighsCut) {
                highDatapoints.add(0, new Datapoint(data.high5mTimes[i], data.high5mPrices[i], false, Datapoint.Type.FIVE_MIN_AVERAGE));
            }
        }
        int oneHourHighsCut;
        if (!highDatapoints.isEmpty()) {
            oneHourHighsCut = Constants.HOUR_SECONDS * (highDatapoints.get(0).time / Constants.HOUR_SECONDS)  + Constants.HOUR_SECONDS;
            highDatapoints.removeIf((i) -> i.time < oneHourHighsCut);
        } else {
            oneHourHighsCut = Integer.MAX_VALUE;
        }
        for (int i = data.high1hTimes.length-1; i >= 0; i--) {
            if (data.high1hTimes[i] < oneHourHighsCut) {
                highDatapoints.add(0, new Datapoint(data.high1hTimes[i], data.high1hPrices[i], false, Datapoint.Type.HOUR_AVERAGE));
            }
        }
        
        // add the prediction data points
        for (int i = 0; i < data.predictionTimes.length; i++) {
            predictionLowDatapoints.add(new Datapoint(
                    data.predictionTimes[i],
                    data.predictionLowMeans[i],
                    data.predictionLowIQRLower[i],
                    data.predictionLowIQRUpper[i],
                    true
            ));
            predictionHighDatapoints.add(new Datapoint(
                    data.predictionTimes[i],
                    data.predictionHighMeans[i],
                    data.predictionHighIQRLower[i],
                    data.predictionHighIQRUpper[i],
                    false
            ));
        }
    }

    private void calculateStats() {
        int cut24h = (int) Instant.now().minus(Duration.ofDays(1)).getEpochSecond();
        int cutWeek = (int) Instant.now().minus(Duration.ofDays(7)).getEpochSecond();
        if (!lowDatapoints.isEmpty() && !highDatapoints.isEmpty()){
            double priceCurrent = (lowDatapoints.get(lowDatapoints.size()-1).price *0.5 + highDatapoints.get(highDatapoints.size()-1).price *0.5);
            double lowPrice24hAgo = lowDatapoints.stream().filter(i-> i.time > cut24h).findFirst().map(i -> (double) i.price).orElse(priceCurrent);
            double highPrice24hAgo = highDatapoints.stream().filter(i-> i.time > cut24h).findFirst().map(i -> (double) i.price).orElse(priceCurrent);
            double price24hAgo = lowPrice24hAgo*0.5 + highPrice24hAgo*0.5;
            if (price24hAgo > 0 ) {
                this.priceChange24H = (priceCurrent - price24hAgo) / price24hAgo;
            }
            double lowPriceWeekAgo = lowDatapoints.stream().filter(i-> i.time > cutWeek).findFirst().map(i -> (double) i.price).orElse(priceCurrent);
            double highPriceWeekAgo = highDatapoints.stream().filter(i-> i.time > cutWeek).findFirst().map(i -> (double) i.price).orElse(priceCurrent);
            double priceWeekAgo = lowPriceWeekAgo*0.5 + highPriceWeekAgo*0.5;
            if (priceWeekAgo > 0 ) {
                this.priceChangeWeek = (priceCurrent - priceWeekAgo) / priceWeekAgo;
            }
        }

        if(!highDatapoints.isEmpty()) {
            lastHighTime = highDatapoints.get(highDatapoints.size()-1).time;
            lastHighPrice = highDatapoints.get(highDatapoints.size()-1).price;
        }

        if(!lowDatapoints.isEmpty()) {
            lastLowTime = lowDatapoints.get(lowDatapoints.size()-1).time;
            lastLowPrice = lowDatapoints.get(lowDatapoints.size()-1).price;
        }

        margin = data.sellPrice - data.buyPrice;
        tax = data.sellPrice - GeTax.getPostTaxPrice(data.itemId, (int) data.sellPrice);
        profit = margin - tax;
    }

    public List<Datapoint> sellPriceDataPoint() {
        return Collections.singletonList(new Datapoint(
                (int) Instant.now().getEpochSecond(),
                (int) data.sellPrice,
                false,
                Datapoint.Type.PREDICTION
        ));
    }

    public List<Datapoint> buyPriceDataPoint() {
        return Collections.singletonList(new Datapoint(
                (int) Instant.now().getEpochSecond(),
                (int) data.buyPrice,
                true,
                Datapoint.Type.PREDICTION
        ));
    }
}