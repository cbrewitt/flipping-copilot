package copilot.ui.graph.model;
import static copilot.util.ProtoUtils.*;

import com.google.protobuf.*;
import copilot.util.*;
import lombok.*;

import java.io.*;

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
    public int[] volume1hLows, volume1hHighs;
    public int[] volume1hTimes;

    public int[] volume5mLows, volume5mHighs;
    public int[] volume5mTimes;
    
    // stats
    public int itemId;

    public String name;

    public double dailyVolume;

    public long sellPrice, buyPrice;

    // every series is delta encoded, so a series that was not sent simply leaves its field null
    public static Data decodeProto(byte[] bytes) throws IOException {
        var d = new Data();
        if (bytes == null || bytes.length == 0) { return d; }
        var input = CodedInputStream.newInstance(bytes);
        for (int tag; (tag = input.readTag()) != 0;) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:  d.volume1hTimes = readDeltaInt32Array(input); break;
                case 2:  d.volume1hLows = readDeltaInt32Array(input); break;
                case 3:  d.volume1hHighs = readDeltaInt32Array(input); break;

                case 4:  d.volume5mTimes = readDeltaInt32Array(input); break;
                case 5:  d.volume5mLows = readDeltaInt32Array(input); break;
                case 6:  d.volume5mHighs = readDeltaInt32Array(input); break;

                case 7:  d.low1hTimes = readDeltaInt32Array(input); break;
                case 8:  d.low1hPrices = readDeltaInt64Array(input); break;
                case 9:  d.high1hTimes = readDeltaInt32Array(input); break;
                case 10: d.high1hPrices = readDeltaInt64Array(input); break;

                case 11: d.low5mTimes = readDeltaInt32Array(input); break;
                case 12: d.low5mPrices = readDeltaInt64Array(input); break;
                case 13: d.high5mTimes = readDeltaInt32Array(input); break;
                case 14: d.high5mPrices = readDeltaInt64Array(input); break;

                case 15: d.lowLatestTimes = readDeltaInt32Array(input); break;
                case 16: d.lowLatestPrices = readDeltaInt64Array(input); break;
                case 17: d.highLatestTimes = readDeltaInt32Array(input); break;
                case 18: d.highLatestPrices = readDeltaInt64Array(input); break;

                case 19: d.predictionTimes = readDeltaInt32Array(input); break;
                case 20: d.predictionLowMeans = readDeltaInt64Array(input); break;
                case 21: d.predictionLowIQRUpper = readDeltaInt64Array(input); break;
                case 22: d.predictionLowIQRLower = readDeltaInt64Array(input); break;
                case 23: d.predictionHighMeans = readDeltaInt64Array(input); break;
                case 24: d.predictionHighIQRUpper = readDeltaInt64Array(input); break;
                case 25: d.predictionHighIQRLower = readDeltaInt64Array(input); break;

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
