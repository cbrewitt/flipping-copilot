package com.flippingcopilot.ui.graph.model;

import com.flippingcopilot.msgpacklite.MsgpackName;
import com.google.gson.annotations.SerializedName;


public class Data {

    public String loadingErrorMessage;

    // 6 months 1h data
    @SerializedName("low_1h_times")
    @MsgpackName("l1ht")
    public int[] low1hTimes;
    @MsgpackName("l1hp")
    @SerializedName("low_1h_prices")
    public int[] low1hPrices;
    @MsgpackName("h1ht")
    @SerializedName("high_1h_times")
    public int[] high1hTimes;
    @MsgpackName("h1hp")
    @SerializedName("high_1h_prices")
    public int[] high1hPrices;

    // 1 month 5m data
    @MsgpackName("l5mt")
    @SerializedName("low_5m_times")
    public int[] low5mTimes;
    @MsgpackName("l5mp")
    @SerializedName("low_5m_prices")
    public int[] low5mPrices;
    @MsgpackName("h5mt")
    @SerializedName("high_5m_times")
    public int[] high5mTimes;
    @MsgpackName("h5mp")
    @SerializedName("high_5m_prices")
    public int[] high5mPrices;

    // several days latest data
    @MsgpackName("llt")
    @SerializedName("low_latest_times")
    public int[] lowLatestTimes;
    @MsgpackName("llp")
    @SerializedName("low_latest_prices")
    public int[] lowLatestPrices;
    @MsgpackName("hlt")
    @SerializedName("high_latest_times")
    public int[] highLatestTimes;
    @MsgpackName("hlp")
    @SerializedName("high_latest_prices")
    public int[] highLatestPrices;

    @MsgpackName("pt")
    @SerializedName("prediction_times")
    public int[] predictionTimes;
    @MsgpackName("plm")
    @SerializedName("prediction_low_means")
    public int[] predictionLowMeans;
    @MsgpackName("pliu")
    @SerializedName("prediction_low_iqr_upper")
    public int[] predictionLowIQRUpper;
    @MsgpackName("plil")
    @SerializedName("prediction_low_iqr_lower")
    public int[] predictionLowIQRLower;
    @MsgpackName("phm")
    @SerializedName("prediction_high_means")
    public int[] predictionHighMeans;
    @MsgpackName("phiu")
    @SerializedName("prediction_high_iqr_upper")
    public int[] predictionHighIQRUpper;
    @MsgpackName("phil")
    @SerializedName("prediction_high_iqr_lower")
    public int[] predictionHighIQRLower;

    // stats
    @MsgpackName("id")
    @SerializedName("item_id")
    public int itemId;
    @MsgpackName("n")
    @SerializedName("name")
    public String name;
    @MsgpackName("dv")
    @SerializedName("daily_volume")
    public double dailyVolume;
    @MsgpackName("sp")
    @SerializedName("sell_price")
    public int sellPrice;
    @MsgpackName("bp")
    @SerializedName("buy_price")
    public int buyPrice;
}