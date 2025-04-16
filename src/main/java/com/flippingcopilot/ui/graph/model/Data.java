package com.flippingcopilot.ui.graph.model;

import com.google.gson.annotations.SerializedName;

public class Data {
    // 6 months 1h data
    @SerializedName("low_1h_times")
    public int[] low1hTimes;
    @SerializedName("low_1h_prices")
    public int[] low1hPrices;
    @SerializedName("high_1h_times")
    public int[] high1hTimes;
    @SerializedName("high_1h_prices")
    public int[] high1hPrices;

    // 1 month 5m data
    @SerializedName("low_5m_times")
    public int[] low5mTimes;
    @SerializedName("low_5m_prices")
    public int[] low5mPrices;
    @SerializedName("high_5m_times")
    public int[] high5mTimes;
    @SerializedName("high_5m_prices")
    public int[] high5mPrices;

    // several days latest data
    @SerializedName("low_latest_times")
    public int[] lowLatestTimes;
    @SerializedName("low_latest_prices")
    public int[] lowLatestPrices;
    @SerializedName("high_latest_times")
    public int[] highLatestTimes;
    @SerializedName("high_latest_prices")
    public int[] highLatestPrices;

    @SerializedName("prediction_times")
    public int[] predictionTimes;
    @SerializedName("prediction_low_means")
    public int[] predictionLowMeans;
    @SerializedName("prediction_low_iqr_upper")
    public int[] predictionLowIQRUpper;
    @SerializedName("prediction_low_iqr_lower")
    public int[] predictionLowIQRLower;
    @SerializedName("prediction_high_means")
    public int[] predictionHighMeans;
    @SerializedName("prediction_high_iqr_upper")
    public int[] predictionHighIQRUpper;
    @SerializedName("prediction_high_iqr_lower")
    public int[] predictionHighIQRLower;

    // stats
    @SerializedName("name")
    public String name;
    @SerializedName("daily_volume")
    public double dailyVolume;
}