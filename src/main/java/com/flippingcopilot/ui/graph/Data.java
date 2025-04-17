package com.flippingcopilot.ui.graph;

import com.google.gson.annotations.SerializedName;

public class Data {
    @SerializedName("low_times")
    public int[] lowTimes;
    @SerializedName("low_prices")
    public int[] lowPrices;
    @SerializedName("high_times")
    public int[] highTimes;
    @SerializedName("high_prices")
    public int[] highPrices;
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
    public long dailyVolume;
    @SerializedName("last_insta_buy_time")
    public int lastInstaBuyTime;
    @SerializedName("last_insta_buy_price")
    public int lastInstaBuyPrice;
    @SerializedName("last_insta_sell_time")
    public int lastInstaSellTime;
    @SerializedName("last_insta_sell_price")
    public int lastInstaSellPrice;
    @SerializedName("daily_price_change")
    public float dailyPriceChange;
    @SerializedName("weekly_price_change")
    public float weeklyPriceChange;
}