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
public class ItemPrice {
    @SerializedName("sell_price")
    private int sellPrice;
    @SerializedName("buy_price")
    private  int buyPrice;
    private  String message;
    @SerializedName("graph_data")
    private Data graphData;

    public static ItemPrice fromMsgPack(ByteBuffer b) {
        ItemPrice ip = new ItemPrice();
        Integer mapSize = MsgPackUtil.decodeMapSize(b);
        if(mapSize == null) {
            return null;
        }
        for (int i = 0; i < mapSize; i++) {
            String key = (String) MsgPackUtil.decodePrimitive(b);
            switch (key) {
                case "sp":
                    ip.sellPrice = (int) (long)MsgPackUtil.decodePrimitive(b);
                    break;
                case "bp":
                    ip.buyPrice = (int) (long) MsgPackUtil.decodePrimitive(b);
                    break;
                case "m":
                    ip.message = (String) MsgPackUtil.decodePrimitive(b);
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
