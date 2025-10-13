package com.flippingcopilot.model;

import com.flippingcopilot.ui.graph.model.Data;
import com.flippingcopilot.util.MsgPackUtil;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.nio.ByteBuffer;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class VisualizeFlipResponse {

    public int[] buyTimes;
    public int[] buyVolumes;
    public int[] buyPrices;
    public int[] sellTimes;
    public int[] sellVolumes;
    public int[] sellPrices;
    public Data graphData;

    public static VisualizeFlipResponse fromMsgPack(ByteBuffer b) {
        VisualizeFlipResponse ip = new VisualizeFlipResponse();
        Integer mapSize = MsgPackUtil.decodeMapSize(b);
        if(mapSize == null) {
            return null;
        }
        for (int i = 0; i < mapSize; i++) {
            String key = (String) MsgPackUtil.decodePrimitive(b);
            switch (key) {
                case "bt":
                    ip.buyTimes = MsgPackUtil.decodeInt32Array(b);
                    break;
                case "bv":
                    ip.buyVolumes = MsgPackUtil.decodeInt32Array(b);
                    break;
                case "bp":
                    ip.buyPrices = MsgPackUtil.decodeInt32Array(b);
                    break;
                case "st":
                    ip.sellTimes = MsgPackUtil.decodeInt32Array(b);
                    break;
                case "sv":
                    ip.sellVolumes = MsgPackUtil.decodeInt32Array(b);
                    break;
                case "sp":
                    ip.sellPrices = MsgPackUtil.decodeInt32Array(b);
                    break;
                case "gd":
                    ip.graphData = Data.fromMsgPack(b);
                    break;
                default:
                    // discard value for unrecognised key
                    MsgPackUtil.decodePrimitive(b);
            }
        }
        return ip;
    }
}
