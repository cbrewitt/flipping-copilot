package com.flippingcopilot.ui.graph.model;

import com.flippingcopilot.util.ProtoUtils;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import lombok.Getter;

import java.io.IOException;

public class Data {

    @Getter
    public String loadingErrorMessage;

    @Getter
    public boolean fromWaitSuggestion;

    // 6 months 1h data
    public int[] low1hTimes;

    public long[] low1hPrices;

    public int[] high1hTimes;

    public long[] high1hPrices;

    // 1 month 5m data
    public int[] low5mTimes;

    public long[] low5mPrices;

    public int[] high5mTimes;

    public long[] high5mPrices;

    // several days latest data
    public int[] lowLatestTimes;

    public long[] lowLatestPrices;

    public int[] highLatestTimes;

    public long[] highLatestPrices;

    public int[] predictionTimes;

    public long[] predictionLowMeans;

    public long[] predictionLowIQRUpper;

    public long[] predictionLowIQRLower;

    public long[] predictionHighMeans;

    public long[] predictionHighIQRUpper;

    public long[] predictionHighIQRLower;

    // the volumes are for UTC hour bins and the current time is assumed to be (predictionTimes[0] - 60) epoch seconds
    public int[] volume1hLows;
    public int[] volume1hHighs;
    public int[] volume1hTimes;

    public int[] volume5mLows;
    public int[] volume5mHighs;
    public int[] volume5mTimes;
    
    // stats
    public int itemId;

    public String name;

    public double dailyVolume;

    public long sellPrice;
    public long buyPrice;


    // every series is delta encoded, so a series that was not sent simply leaves its field null
    public static Data decodeProto(byte[] bytes) throws IOException {
        Data d = new Data();
        if (bytes == null || bytes.length == 0) {
            return d;
        }
        CodedInputStream input = CodedInputStream.newInstance(bytes);
        while (!input.isAtEnd()) {
            int tag = input.readTag();
            if (tag == 0) {
                break;
            }
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:  d.volume1hTimes = ProtoUtils.readDeltaInt32Array(input); break;
                case 2:  d.volume1hLows = ProtoUtils.readDeltaInt32Array(input); break;
                case 3:  d.volume1hHighs = ProtoUtils.readDeltaInt32Array(input); break;

                case 4:  d.volume5mTimes = ProtoUtils.readDeltaInt32Array(input); break;
                case 5:  d.volume5mLows = ProtoUtils.readDeltaInt32Array(input); break;
                case 6:  d.volume5mHighs = ProtoUtils.readDeltaInt32Array(input); break;

                case 7:  d.low1hTimes = ProtoUtils.readDeltaInt32Array(input); break;
                case 8:  d.low1hPrices = ProtoUtils.readDeltaInt64Array(input); break;
                case 9:  d.high1hTimes = ProtoUtils.readDeltaInt32Array(input); break;
                case 10: d.high1hPrices = ProtoUtils.readDeltaInt64Array(input); break;

                case 11: d.low5mTimes = ProtoUtils.readDeltaInt32Array(input); break;
                case 12: d.low5mPrices = ProtoUtils.readDeltaInt64Array(input); break;
                case 13: d.high5mTimes = ProtoUtils.readDeltaInt32Array(input); break;
                case 14: d.high5mPrices = ProtoUtils.readDeltaInt64Array(input); break;

                case 15: d.lowLatestTimes = ProtoUtils.readDeltaInt32Array(input); break;
                case 16: d.lowLatestPrices = ProtoUtils.readDeltaInt64Array(input); break;
                case 17: d.highLatestTimes = ProtoUtils.readDeltaInt32Array(input); break;
                case 18: d.highLatestPrices = ProtoUtils.readDeltaInt64Array(input); break;

                case 19: d.predictionTimes = ProtoUtils.readDeltaInt32Array(input); break;
                case 20: d.predictionLowMeans = ProtoUtils.readDeltaInt64Array(input); break;
                case 21: d.predictionLowIQRUpper = ProtoUtils.readDeltaInt64Array(input); break;
                case 22: d.predictionLowIQRLower = ProtoUtils.readDeltaInt64Array(input); break;
                case 23: d.predictionHighMeans = ProtoUtils.readDeltaInt64Array(input); break;
                case 24: d.predictionHighIQRUpper = ProtoUtils.readDeltaInt64Array(input); break;
                case 25: d.predictionHighIQRLower = ProtoUtils.readDeltaInt64Array(input); break;

                case 26: d.itemId = input.readInt32(); break;
                case 27: d.name = input.readString(); break;
                case 28: d.dailyVolume = input.readDouble(); break;
                case 29: d.sellPrice = input.readInt64(); break;
                case 30: d.buyPrice = input.readInt64(); break;

                default: input.skipField(tag);
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
