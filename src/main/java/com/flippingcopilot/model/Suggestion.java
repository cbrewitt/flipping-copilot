package com.flippingcopilot.model;

import com.flippingcopilot.ui.graph.model.Data;
import com.flippingcopilot.util.MsgPackUtil;
import com.google.gson.annotations.SerializedName;
import lombok.*;

import java.nio.ByteBuffer;
import java.text.NumberFormat;
import java.time.Instant;

@Setter
@Getter
@AllArgsConstructor
@ToString
@NoArgsConstructor
public class Suggestion {
    private String type;
    @SerializedName("box_id")
    private int boxId;
    @SerializedName("item_id")
    private int itemId;
    private int price;
    private int quantity;
    private String name;
    @SerializedName("command_id")
    private int id;
    private String message;
    private Double expectedProfit;
    private Double expectedDuration;

    @SerializedName("graph_data")
    private Data graphData;

    public volatile Instant dumpAlertReceived = Instant.now();
    public volatile boolean isDumpAlert;
    public volatile boolean actioned;


    public boolean equals(Suggestion other) {
        return this.type.equals(other.type)
                && this.itemId == other.itemId
                && this.name.equals(other.name);
    }

    public boolean isRecentUnsanctionedDumpAlert() {
        return isDumpAlert && !actioned && dumpAlertReceived.isAfter(Instant.now().minusSeconds(10));
    }

    public boolean isDumpSuggestion() {
        return isDumpAlert;
    }

    public String toMessage() {
        NumberFormat formatter = NumberFormat.getNumberInstance();
        String string = isDumpAlert ? "DUMP ALERT!! " : "Flipping Copilot: ";
        switch (type) {
            case "buy":
                string += String.format("Buy %s %s for %s gp",
                        formatter.format(quantity), name, formatter.format(price));
                break;
            case "sell":
                string += String.format("Sell %s %s for %s gp",
                        formatter.format(quantity), name, formatter.format(price));
                break;
            case "abort":
                string += "Abort " + name;
                break;
            case "wait":
                string += "Wait";
                break;
            default:
                string += "Unknown suggestion type";
                break;
        }
        return string;
    }

    public static Suggestion fromMsgPack(ByteBuffer b) {
        Suggestion s = new Suggestion();
        Integer mapSize = MsgPackUtil.decodeMapSize(b);
        if(mapSize == null) {
            return null;
        }

        for (int i = 0; i < mapSize; i++) {
            String key = (String) MsgPackUtil.decodePrimitive(b);
            switch (key) {
                case "t":
                    s.type = (String) MsgPackUtil.decodePrimitive(b);
                    break;
                case "b":
                    s.boxId = (int) (long) MsgPackUtil.decodePrimitive(b);
                    break;
                case "i":
                    s.itemId = (int) (long) MsgPackUtil.decodePrimitive(b);
                    break;
                case "p":
                    s.price = (int) (long) MsgPackUtil.decodePrimitive(b);
                    break;
                case "q":
                    s.quantity = (int) (long) MsgPackUtil.decodePrimitive(b);
                    break;
                case "n":
                    s.name = (String) MsgPackUtil.decodePrimitive(b);
                    break;
                case "id":
                    s.id = (int) (long) MsgPackUtil.decodePrimitive(b);
                    break;
                case "m":
                    s.message = (String) MsgPackUtil.decodePrimitive(b);
                    break;
                case "ed":
                    s.expectedDuration = (Double) MsgPackUtil.decodePrimitive(b);
                    break;
                case "ep":
                    s.expectedProfit = (Double) MsgPackUtil.decodePrimitive(b);
                    break;
                case "gd":
                    s.graphData = Data.fromMsgPack(b);
                    break;
                default:
                    // discard value for unrecognised key
                    MsgPackUtil.decodePrimitive(b);
            }
        }

        return s;
    }
}
