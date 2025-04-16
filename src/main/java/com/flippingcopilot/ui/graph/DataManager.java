package com.flippingcopilot.ui.graph;

import lombok.Getter;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;


@Getter
public class DataManager {
    private final List<Datapoint> dataPoints = new ArrayList<>();
    private final Data data;

    // Data bounds
    private int minTime;
    private int maxTime;
    private int minPrice;
    private int maxPrice;

    public DataManager(Data data) {
        this.data = data;
        calculateBounds();
        setupGraphDatapoints();
    }

    /**
     * Find the closest data point to the specified position.
     *
     * @param mousePos The position to check
     * @param hoverRadius Maximum distance for hovering
     * @param minTime Minimum time value in view
     * @param maxTime Maximum time value in view
     * @param minPrice Minimum price value in view
     * @param maxPrice Maximum price value in view
     * @param width Component width
     * @param height Component height
     * @return The closest data point, or null if none is close enough
     */
    public Datapoint findClosestPoint(Point mousePos, int hoverRadius,
                                      int minTime, int maxTime, int minPrice, int maxPrice,
                                      int width, int height) {
        if (mousePos == null) return null;

        Datapoint closest = null;
        double minDistance = hoverRadius;

        // Create temporary renderer for coordinate conversion
        Renderer renderer = new Renderer();

        for (Datapoint point : dataPoints) {
            // Update screen position
            int x = renderer.timeToX(point.getTime(), minTime, maxTime, width);
            int y = renderer.priceToY(point.getPrice(), minPrice, maxPrice, height);
            point.setScreenPosition(new Point(x, y));

            double distance = mousePos.distance(point.getScreenPosition());
            if (distance < minDistance) {
                minDistance = distance;
                closest = point;
            }
        }

        return closest;
    }

    /**
     * Calculate the min and max values for scaling the graph.
     */
    private void calculateBounds() {
        if (data == null) return;

        // Initialize with first values
        minTime = Integer.MAX_VALUE;
        maxTime = Integer.MIN_VALUE;
        minPrice = Integer.MAX_VALUE;
        maxPrice = Integer.MIN_VALUE;

        // Check historical data
        for (int time : data.lowTimes) {
            minTime = Math.min(minTime, time);
            maxTime = Math.max(maxTime, time);
        }

        for (int time : data.highTimes) {
            minTime = Math.min(minTime, time);
            maxTime = Math.max(maxTime, time);
        }

        for (int price : data.lowPrices) {
            minPrice = Math.min(minPrice, price);
            maxPrice = Math.max(maxPrice, price);
        }

        for (int price : data.highPrices) {
            minPrice = Math.min(minPrice, price);
            maxPrice = Math.max(maxPrice, price);
        }

        // Check prediction data
        if (data.predictionTimes != null && data.predictionTimes.length > 0) {
            for (int time : data.predictionTimes) {
                maxTime = Math.max(maxTime, time);
            }

            for (int price : data.predictionLowIQRLower) {
                minPrice = Math.min(minPrice, price);
            }

            for (int price : data.predictionLowIQRUpper) {
                maxPrice = Math.max(maxPrice, price);
            }

            for (int price : data.predictionHighIQRLower) {
                minPrice = Math.min(minPrice, price);
            }

            for (int price : data.predictionHighIQRUpper) {
                maxPrice = Math.max(maxPrice, price);
            }
        }

        // Add some padding to the price range
        int pricePadding = (int) (0.05 * (maxPrice - minPrice));
        if (pricePadding < 1) pricePadding = 1;

        minPrice -= pricePadding;
        maxPrice += pricePadding;
    }

    /**
     * Create all data points that will be hoverable
     */
    private void setupGraphDatapoints() {
        dataPoints.clear();

        if (data == null) return;

        // Add low prices (buy)
        for (int i = 0; i < data.lowTimes.length; i++) {
            dataPoints.add(new Datapoint(data.lowTimes[i], data.lowPrices[i], true, false));
        }

        // Add high prices (sell)
        for (int i = 0; i < data.highTimes.length; i++) {
            dataPoints.add(new Datapoint(data.highTimes[i], data.highPrices[i], false, false));
        }

        // Add prediction points (only means, not IQR bounds)
        if (data.predictionTimes != null) {
            for (int i = 0; i < data.predictionTimes.length; i++) {
                if (data.predictionLowMeans != null && i < data.predictionLowMeans.length) {
                    dataPoints.add(new Datapoint(data.predictionTimes[i], data.predictionLowMeans[i], true, true));
                }
                if (data.predictionHighMeans != null && i < data.predictionHighMeans.length) {
                    dataPoints.add(new Datapoint(data.predictionTimes[i], data.predictionHighMeans[i], false, true));
                }
            }
        }
    }
}