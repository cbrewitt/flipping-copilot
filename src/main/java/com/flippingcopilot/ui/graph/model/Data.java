package com.flippingcopilot.ui.graph.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;


public class Data {

    @Getter
    public String loadingErrorMessage;

    @Getter
    public boolean fromWaitSuggestion;

    // 6 months 1h data
    @JsonProperty("l1ht")
    @SerializedName("low_1h_times")
    public int[] low1hTimes;

    @JsonProperty("l1hp")
    @SerializedName("low_1h_prices")
    public int[] low1hPrices;

    @JsonProperty("h1ht")
    @SerializedName("high_1h_times")
    public int[] high1hTimes;

    @JsonProperty("h1hp")
    @SerializedName("high_1h_prices")
    public int[] high1hPrices;

    // 1 month 5m data
    @JsonProperty("l5mt")
    @SerializedName("low_5m_times")
    public int[] low5mTimes;

    @JsonProperty("l5mp")
    @SerializedName("low_5m_prices")
    public int[] low5mPrices;

    @JsonProperty("h5mt")
    @SerializedName("high_5m_times")
    public int[] high5mTimes;

    @JsonProperty("h5mp")
    @SerializedName("high_5m_prices")
    public int[] high5mPrices;

    // several days latest data
    @JsonProperty("llt")
    @SerializedName("low_latest_times")
    public int[] lowLatestTimes;

    @JsonProperty("llp")
    @SerializedName("low_latest_prices")
    public int[] lowLatestPrices;

    @JsonProperty("hlt")
    @SerializedName("high_latest_times")
    public int[] highLatestTimes;

    @JsonProperty("hlp")
    @SerializedName("high_latest_prices")
    public int[] highLatestPrices;

    @JsonProperty("pt")
    @SerializedName("prediction_times")
    public int[] predictionTimes;

    @JsonProperty("plm")
    @SerializedName("prediction_low_means")
    public int[] predictionLowMeans;

    @JsonProperty("pliu")
    @SerializedName("prediction_low_iqr_upper")
    public int[] predictionLowIQRUpper;

    @JsonProperty("plil")
    @SerializedName("prediction_low_iqr_lower")
    public int[] predictionLowIQRLower;

    @JsonProperty("phm")
    @SerializedName("prediction_high_means")
    public int[] predictionHighMeans;

    @JsonProperty("phiu")
    @SerializedName("prediction_high_iqr_upper")
    public int[] predictionHighIQRUpper;

    @JsonProperty("phil")
    @SerializedName("prediction_high_iqr_lower")
    public int[] predictionHighIQRLower;

    // stats
    @JsonProperty("id")
    @SerializedName("item_id")
    public int itemId;

    @JsonProperty("n")
    @SerializedName("name")
    public String name;


    @JsonProperty("dv")
    @SerializedName("daily_volume")
    public double dailyVolume;

    @JsonProperty("sp")
    @SerializedName("sell_price")
    public int sellPrice;

    @JsonProperty("bp")
    @SerializedName("buy_price")
    public int buyPrice;
}