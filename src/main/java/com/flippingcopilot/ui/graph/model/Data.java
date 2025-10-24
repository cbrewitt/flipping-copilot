package com.flippingcopilot.ui.graph.model;

import com.flippingcopilot.util.MsgPackUtil;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import java.nio.ByteBuffer;
import java.util.Random;

public class Data {

    @Getter
    public String loadingErrorMessage;

    @Getter
    public boolean fromWaitSuggestion;

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

    // the volumes are for UTC hour bins and the current time is assumed to be (predictionTimes[0] - 60) epoch seconds
    public int[] volume1hLows;
    public int[] volume1hHighs;
    public int[] volume1hTimes;

    public int[] volume5mLows;
    public int[] volume5mHighs;
    public int[] volume5mTimes;
    
    // stats
    @SerializedName("item_id")
    public int itemId;

    @SerializedName("name")
    public String name;

    @SerializedName("daily_volume")
    public double dailyVolume;

    @SerializedName("sell_price")
    public long sellPrice;
    @SerializedName("buy_price")
    public long buyPrice;


    public static Data fromMsgPack(ByteBuffer b) {
        Data d = new Data();
        Integer mapSize = MsgPackUtil.decodeMapSize(b);
        if(mapSize == null) {
            return null;
        }
        for (int i = 0; i < mapSize; i++) {
            String key = (String) MsgPackUtil.decodePrimitive(b);
            switch (key) {
                case "l1ht":
                    d.low1hTimes = MsgPackUtil.decodeInt32Array(b);
                    break;
                case "l1hp":
                    d.low1hPrices = MsgPackUtil.decodeInt32Array(b);
                    break;
                case "h1ht":
                    d.high1hTimes = MsgPackUtil.decodeInt32Array(b);
                    break;
                case "h1hp":
                    d.high1hPrices = MsgPackUtil.decodeInt32Array(b);
                    break;
                case "l5mt":
                    d.low5mTimes = MsgPackUtil.decodeInt32Array(b);
                    break;
                case "l5mp":
                    d.low5mPrices = MsgPackUtil.decodeInt32Array(b);
                    break;
                case "h5mt":
                    d.high5mTimes = MsgPackUtil.decodeInt32Array(b);
                    break;
                case "h5mp":
                    d.high5mPrices = MsgPackUtil.decodeInt32Array(b);
                    break;
                case "llt":
                    d.lowLatestTimes = MsgPackUtil.decodeInt32Array(b);
                    break;
                case "llp":
                    d.lowLatestPrices = MsgPackUtil.decodeInt32Array(b);
                    break;
                case "hlt":
                    d.highLatestTimes = MsgPackUtil.decodeInt32Array(b);
                    break;
                case "hlp":
                    d.highLatestPrices = MsgPackUtil.decodeInt32Array(b);
                    break;
                case "pt":
                    d.predictionTimes = MsgPackUtil.decodeInt32Array(b);
                    break;
                case "plm":
                    d.predictionLowMeans = MsgPackUtil.decodeInt32Array(b);
                    break;
                case "pliu":
                    d.predictionLowIQRUpper = MsgPackUtil.decodeInt32Array(b);
                    break;
                case "plil":
                    d.predictionLowIQRLower = MsgPackUtil.decodeInt32Array(b);
                    break;
                case "phm":
                    d.predictionHighMeans = MsgPackUtil.decodeInt32Array(b);
                    break;
                case "phiu":
                    d.predictionHighIQRUpper = MsgPackUtil.decodeInt32Array(b);
                    break;
                case "phil":
                    d.predictionHighIQRLower = MsgPackUtil.decodeInt32Array(b);
                    break;
                case "id":
                    d.itemId =  (int) (long)MsgPackUtil.decodePrimitive(b);
                    break;
                case "n":
                    d.name = (String) MsgPackUtil.decodePrimitive(b);
                    break;
                case "dv":
                    d.dailyVolume = (double) MsgPackUtil.decodePrimitive(b);
                    break;
                case "sp":
                    d.sellPrice = (long) (long) MsgPackUtil.decodePrimitive(b);
                    break;
                case "bp":
                    d.buyPrice = (long) MsgPackUtil.decodePrimitive(b);
                    break;
                case "v1ht":
                    d.volume1hTimes = MsgPackUtil.decodeInt32Array(b);
                    break;
                case "v1hl":
                    d.volume1hLows = MsgPackUtil.decodeInt32Array(b);
                    break;
                case "v1hh":
                    d.volume1hHighs = MsgPackUtil.decodeInt32Array(b);
                    break;
                case "v5mt":
                    d.volume5mTimes = MsgPackUtil.decodeInt32Array(b);
                    break;
                case "v5ml":
                    d.volume5mLows = MsgPackUtil.decodeInt32Array(b);
                    break;
                case "v5mh":
                    d.volume5mHighs = MsgPackUtil.decodeInt32Array(b);
                    break;
                default:
                    // discard value for unrecognised key
                    MsgPackUtil.decodePrimitive(b);
            }
        }
        return d;
    }
    
    public void clearPredictionData() {
        predictionHighIQRLower = null;
        predictionHighIQRUpper = null;
        predictionHighMeans = null;
        predictionLowIQRLower = null;
        predictionLowIQRUpper = null;
        predictionLowMeans = null;
        predictionTimes = null;
    }
}