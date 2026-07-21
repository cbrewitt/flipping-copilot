package com.flippingcopilot.ui.graph;

import com.flippingcopilot.model.VisualizeFlipResponse;
import com.flippingcopilot.ui.graph.model.*;
import com.flippingcopilot.util.ProfitCalculator;
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
    public final List<Datapoint> volumes = new ArrayList<>();

    public final List<Datapoint> flipEntryDatapoints = new ArrayList<>();
    public final List<Datapoint> flipCloseDatapoints = new ArrayList<>();
    public int minEntryTime;
    public int maxCloseTime;
    public Bounds maxBounds = new Bounds();

    public final Data data;
    private final VisualizeFlipResponse fpr;
    public double priceChange24H = 0;
    public double priceChangeWeek = 0;
    public int lastLowTime = 0;
    public long lastLowPrice = 0;
    public int lastHighTime = 0;
    public long lastHighPrice = 0;
    public long margin;
    public long tax;
    public long profit;

    public DataManager(Data data, VisualizeFlipResponse fpr) {
        this.data = data;
        this.fpr = fpr;
        processDatapoints();
        calculateStats();
    }

    public Datapoint findClosestPoint(Point mousePos, int hoverRadius, Rectangle pa, Bounds bounds) {
        if (mousePos == null) return null;

        Datapoint closest = null;
        double minDistance = hoverRadius*2;

        // prioritize hovering on the flip transaction datapoints
        for(List<Datapoint> datapoints : Arrays.asList(flipEntryDatapoints, flipCloseDatapoints)) {
            for (Datapoint d : datapoints) {
                Point hoverPosition = d.getHoverPosition(pa, bounds);
                double distance = mousePos.distance(hoverPosition);
                if (distance < minDistance) {
                    minDistance = distance;
                    closest = d;
                }
            }
        }
        if (closest != null) {
            return closest;
        }

        minDistance = hoverRadius;

        for(List<Datapoint> datapoints : Arrays.asList(highDatapoints, lowDatapoints, predictionLowDatapoints, predictionHighDatapoints)) {
            for (Datapoint d : datapoints) {
                Point hoverPosition = d.getHoverPosition(pa, bounds);
                double distance = mousePos.distance(hoverPosition);
                if (distance < minDistance) {
                    minDistance = distance;
                    closest = d;
                }
            }
        }
        return closest;
    }


    public Bounds calculateHomeBounds() {
        int xMax = flipCloseDatapoints.isEmpty() ? maxBounds.xMax : maxCloseTime;
        int xMin = flipEntryDatapoints.isEmpty() ? maxBounds.xMax - 4*Constants.DAY_SECONDS : minEntryTime;
        int range = xMax - xMin;
        if(!flipEntryDatapoints.isEmpty()) {
            xMin -= Math.max(range / 10, Constants.DAY_SECONDS);
        }
        if(!flipCloseDatapoints.isEmpty()) {
            xMax += Math.max(range / 10, Constants.DAY_SECONDS);
            xMax = Math.min(xMax, maxBounds.xMax);
        }
        int finalXMax = xMax;
        int finalXMin = xMin;
        Bounds b = calculateBounds((p) -> p.time > finalXMin && p.time < finalXMax);
        b.xMax = xMax;
        b.xMin = xMin;
        return b;
    }

    public Bounds calculateWeekBounds() {
        Bounds b = calculateBounds((p) -> p.time > maxBounds.xMax - 7 * Constants.DAY_SECONDS);
        b.xMin= ((b.xMin) / Constants.HOUR_SECONDS) * Constants.HOUR_SECONDS;
        b.xMax = ((b.xMax) / Constants.HOUR_SECONDS) * Constants.HOUR_SECONDS + Constants.HOUR_SECONDS;
        return b;
    }

    public Bounds calculateMonthBounds() {
        Bounds b = calculateBounds((p) -> p.time > maxBounds.xMax - 30 * Constants.DAY_SECONDS);
        b.xMin= ((b.xMin) / Constants.HOUR_SECONDS) * Constants.HOUR_SECONDS;
        b.xMax = ((b.xMax) / Constants.HOUR_SECONDS) * Constants.HOUR_SECONDS + Constants.HOUR_SECONDS;
        return b;
    }


    public Bounds calculateBounds(Predicate<Datapoint> p) {
        Bounds b = new Bounds();

        b.xMin = Integer.MAX_VALUE;
        b.xMax = Integer.MIN_VALUE;
        b.yMax = Long.MIN_VALUE;
        b.yMin = Long.MAX_VALUE;
        b.y2Min = 0;
        b.y2Max = 10;

        long yMean = 0;
        long n = 0;

        for(List<Datapoint> datapoints : Arrays.asList(highDatapoints, lowDatapoints, predictionLowDatapoints, predictionHighDatapoints, flipEntryDatapoints, flipCloseDatapoints)) {
            for (Datapoint d : datapoints) {
                if (p.test(d)) {
                    n += 1;
                    yMean += (d.price - yMean) / n;

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
        for (Datapoint d : volumes) {
            if (p.test(d)) {
                b.y2Max = Math.max(b.y2Max, d.highVolume + d.lowVolume);
            }
            d.time += Constants.HOUR_SECONDS;
            if (p.test(d)) {
                b.y2Max = Math.max(b.y2Max, d.highVolume + d.lowVolume);
            }
            d.time -= Constants.HOUR_SECONDS;
        }
        b.y2Max += b.y2Max / 10;
        long pricePadding = Math.max(1L, (yMean / 100) * 3 + ((yMean % 100) * 3) / 100);

        b.yMin = Math.max(0L, b.yMin - Math.min(b.yMin, pricePadding));
        b.yMax = b.yMax > Long.MAX_VALUE - pricePadding ? Long.MAX_VALUE : b.yMax + pricePadding;

        return b;
    }

    private void addPriceDatapoints(List<Datapoint> target,
                                    int[] latestTimes,
                                    long[] latestPrices,
                                    int[] fiveMinTimes,
                                    long[] fiveMinPrices,
                                    int[] hourTimes,
                                    long[] hourPrices,
                                    boolean isLow) {
        for (int i = 0; i < latestTimes.length; i++) {
            target.add(new Datapoint(latestTimes[i], latestPrices[i], isLow, Datapoint.Type.INSTA_SELL_BUY));
        }
        int fiveMinCut = removeBeforeNextBucket(target, Constants.FIVE_MIN_SECONDS);
        addHistoricalPoints(target, fiveMinTimes, fiveMinPrices, fiveMinCut, isLow, Datapoint.Type.FIVE_MIN_AVERAGE);

        int hourCut = removeBeforeNextBucket(target, Constants.HOUR_SECONDS);
        addHistoricalPoints(target, hourTimes, hourPrices, hourCut, isLow, Datapoint.Type.HOUR_AVERAGE);
    }

    private int removeBeforeNextBucket(List<Datapoint> target, int bucketSeconds) {
        if (target.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        int cut = bucketSeconds * (target.get(0).time / bucketSeconds) + bucketSeconds;
        target.removeIf(i -> i.time < cut);
        return cut;
    }

    private void addHistoricalPoints(List<Datapoint> target,
                                     int[] times,
                                     long[] prices,
                                     int cut,
                                     boolean isLow,
                                     Datapoint.Type type) {
        for (int i = times.length - 1; i >= 0; i--) {
            if (times[i] < cut) {
                target.add(0, new Datapoint(times[i], prices[i], isLow, type));
            }
        }
    }

    private void processDatapoints() {
        highDatapoints.clear();
        lowDatapoints.clear();
        predictionHighDatapoints.clear();
        predictionLowDatapoints.clear();
        volumes.clear();
        flipEntryDatapoints.clear();
        flipCloseDatapoints.clear();
        
        // here we combine the hour / 5min / latest wiki price data points into a continuous dataset where hour points 
        // transition into the 5min points that transition into the latest points. So we get increasingly finer granularity.
        // We truncate the points correctly at the boundaries to ensure no overlap.

        if (data.lowLatestTimes == null) {
            return;
        }

        addPriceDatapoints(lowDatapoints, data.lowLatestTimes, data.lowLatestPrices,
                data.low5mTimes, data.low5mPrices, data.low1hTimes, data.low1hPrices, true);
        addPriceDatapoints(highDatapoints, data.highLatestTimes, data.highLatestPrices,
                data.high5mTimes, data.high5mPrices, data.high1hTimes, data.high1hPrices, false);


        if(data.predictionTimes != null) {
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

        for (int i = data.volume1hLows.length-1; i >= 0; i--) {
            volumes.add(0, Datapoint.newVolumeDatapoint(data.volume1hTimes[i], data.volume1hLows[i], data.volume1hHighs[i]));
        }
        int current1hTime = data.volume1hTimes[data.volume1hTimes.length-1] + Constants.HOUR_SECONDS;
        int currentHourLowVolume = 0;
        int currentHoursHighVolume =0;
        for (int i = data.volume5mLows.length-1; i >= 0; i--) {
            if (data.volume5mTimes[i] >= current1hTime) {
                currentHourLowVolume += data.volume5mLows[i];
                currentHoursHighVolume += data.volume5mHighs[i];
            }
        }
        volumes.add(Datapoint.newVolumeDatapoint(current1hTime, currentHourLowVolume, currentHoursHighVolume));

        minEntryTime = Integer.MAX_VALUE;
        maxCloseTime = 0;
        if(fpr != null){
            if(fpr.buyTimes != null) {
                for (int i = 0; i < fpr.buyTimes.length; i++) {
                    flipEntryDatapoints.add(Datapoint.newBuyTx(
                            fpr.buyTimes[i],
                            fpr.buyPrices[i],
                            fpr.buyVolumes[i]
                    ));
                    minEntryTime = Math.min(fpr.buyTimes[i], minEntryTime);
                }
            }
            if(fpr.sellTimes != null) {
                for (int i = 0; i < fpr.sellTimes.length; i++) {
                    flipCloseDatapoints.add(Datapoint.newSellTx(
                            fpr.sellTimes[i],
                            fpr.sellPrices[i],
                            fpr.sellVolumes[i]
                    ));
                    maxCloseTime = Math.max(fpr.sellTimes[i], maxCloseTime);
                }
            }
        }

        maxBounds = calculateBounds((p) -> true);
        maxBounds.xMin= ((maxBounds.xMin) / Constants.HOUR_SECONDS) * Constants.HOUR_SECONDS;
        maxBounds.xMax = ((maxBounds.xMax) / Constants.HOUR_SECONDS) * Constants.HOUR_SECONDS + Constants.HOUR_SECONDS;
    }

    private void calculateStats() {
        int cut24h = (int) Instant.now().minus(Duration.ofDays(1)).getEpochSecond();
        int cutWeek = (int) Instant.now().minus(Duration.ofDays(7)).getEpochSecond();
        if (!lowDatapoints.isEmpty() && !highDatapoints.isEmpty()){
            long currentLowPrice = lowDatapoints.get(lowDatapoints.size() - 1).price;
            long currentHighPrice = highDatapoints.get(highDatapoints.size() - 1).price;
            this.priceChange24H = priceChangeSince(cut24h, currentLowPrice, currentHighPrice);
            this.priceChangeWeek = priceChangeSince(cutWeek, currentLowPrice, currentHighPrice);
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
        tax = ProfitCalculator.getTaxAmount(data.itemId, data.sellPrice);
        profit = margin - tax;
    }

    private double priceChangeSince(int cut, long currentLowPrice, long currentHighPrice) {
        long lowPrice = firstPriceAfter(lowDatapoints, cut, currentLowPrice);
        long highPrice = firstPriceAfter(highDatapoints, cut, currentHighPrice);
        double currentPrice = currentLowPrice * 0.5 + currentHighPrice * 0.5;
        double oldPrice = lowPrice * 0.5 + highPrice * 0.5;
        return oldPrice > 0 ? (currentPrice - oldPrice) / oldPrice : 0;
    }

    private long firstPriceAfter(List<Datapoint> datapoints, int cut, long fallback) {
        return datapoints.stream()
                .filter(i -> i.time > cut)
                .findFirst()
                .map(i -> i.price)
                .orElse(fallback);
    }

    public List<Datapoint> sellPriceDataPoint() {
        return Collections.singletonList(new Datapoint(
                (int) Instant.now().getEpochSecond(),
                data.sellPrice,
                false,
                Datapoint.Type.PREDICTION
        ));
    }

    public List<Datapoint> buyPriceDataPoint() {
        return Collections.singletonList(new Datapoint(
                (int) Instant.now().getEpochSecond(),
                data.buyPrice,
                true,
                Datapoint.Type.PREDICTION
        ));
    }

    public Datapoint closedVolumeBar(Point mousePosition, Rectangle pa, Bounds bounds) {
        Datapoint winner =  null;
        int winnerDist = Integer.MAX_VALUE;
        for (Datapoint v : volumes) {
            int dist = Math.abs(bounds.toX(pa, v.time + Constants.HOUR_SECONDS / 2) - mousePosition.x);
            if (dist < winnerDist) {
                winnerDist = dist;
                winner = v;
            }
        }
        return winner;
    }
}
