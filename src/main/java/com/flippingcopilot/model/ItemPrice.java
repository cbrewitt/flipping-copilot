package com.flippingcopilot.model;

import com.flippingcopilot.ui.graph.model.Data;
import com.flippingcopilot.util.ProtoUtils;
import com.google.gson.annotations.SerializedName;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import lombok.*;

import java.io.IOException;
import java.time.Instant;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class ItemPrice {
    @SerializedName("sell_price")
    private long sellPrice;
    @SerializedName("buy_price")
    private long buyPrice;
    private  String message;
    @SerializedName("graph_data")
    private Data graphData;
    @SerializedName("time_given")
    private Instant timeGiven;
    @SerializedName("item_id")
    private int itemId;

    public ItemPrice(long sellPrice, long buyPrice, String message, Data graphData) {
        this(sellPrice, buyPrice, message, graphData, null, 0);
    }

    public static ItemPrice decodeProto(byte[] bytes) throws IOException {
        ItemPrice ip = new ItemPrice();
        if (bytes == null || bytes.length == 0) {
            return ip;
        }
        CodedInputStream input = CodedInputStream.newInstance(bytes);
        while (!input.isAtEnd()) {
            int tag = input.readTag();
            if (tag == 0) {
                break;
            }
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1:
                    ip.buyPrice = input.readInt64();
                    break;
                case 2:
                    ip.sellPrice = input.readInt64();
                    break;
                case 3:
                    ip.message = input.readString();
                    break;
                case 4:
                    ip.graphData = Data.decodeProto(input.readByteArray());
                    break;
                case 5:
                    ip.timeGiven = ProtoUtils.decodeTimestamp(input);
                    break;
                case 6:
                    ip.itemId = input.readInt32();
                    break;
                default:
                    input.skipField(tag);
            }
        }
        return ip;
    }
}
