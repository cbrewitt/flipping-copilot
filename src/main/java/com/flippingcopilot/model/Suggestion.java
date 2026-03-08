package com.flippingcopilot.model;

import com.flippingcopilot.ui.graph.model.Data;
import com.flippingcopilot.util.MsgPackUtil;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import com.google.gson.annotations.SerializedName;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.text.NumberFormat;
import java.time.Instant;
import java.io.IOException;

@Setter
@Getter
@AllArgsConstructor
@ToString
@NoArgsConstructor
@Slf4j
public class Suggestion {
    private SuggestionType type;
    @SerializedName("box_id")
    private int boxId;
    @SerializedName("item_id")
    private int itemId;
    private int price;
    private int quantity;
    private String name;
    @SerializedName("command_id")
    private int id;
    private String message = "";
    private Double expectedProfit;
    private Double expectedDuration;

    @SerializedName("graph_data")
    private Data graphData;

    public volatile Instant dumpAlertReceived = Instant.now();
    public volatile boolean isDumpAlert;
    public volatile int actionedTick = -1;


    public boolean equals(Suggestion other) {
        return this.type == other.type
                && this.itemId == other.itemId
                && this.name.equals(other.name);
    }

    public boolean isWaitSuggestion() {
        return type == SuggestionType.WAIT;
    }

    public boolean isAbortSuggestion() {
        return type == SuggestionType.ABORT;
    }

    public boolean isBuySuggestion() {
        return type == SuggestionType.BUY || type == SuggestionType.MODIFY_BUY;
    }

    public boolean isSellSuggestion() {
        return type == SuggestionType.SELL || type == SuggestionType.MODIFY_SELL;
    }

    public boolean isModifySuggestion() {
        return type == SuggestionType.MODIFY_BUY || type == SuggestionType.MODIFY_SELL;
    }

    public String offerType() {
        if (isBuySuggestion()) {
            return "buy";
        }
        if (isSellSuggestion()) {
            return "sell";
        }
        return null;
    }

    public boolean isRecentUnActionedDumpAlert() {
        return isDumpAlert && actionedTick == -1 && dumpAlertReceived.isAfter(Instant.now().minusSeconds(10));
    }

    public boolean isDumpSuggestion() {
        return isDumpAlert && !isAbortSuggestion();
    }

    public String toMessage() {
        NumberFormat formatter = NumberFormat.getNumberInstance();
        String string = isDumpAlert ? "DUMP ALERT!! " : "Flipping Copilot: ";
        if (type == null) {
            return string + "Unknown suggestion type";
        }
        switch (type) {
            case BUY:
                string += String.format("Buy %s %s for %s gp",
                        formatter.format(quantity), name, formatter.format(price));
                break;
            case MODIFY_BUY:
                string += String.format("Modify buy offer for %s %s to %s gp",
                        formatter.format(quantity), name, formatter.format(price));
                break;
            case SELL:
                string += String.format("Sell %s %s for %s gp",
                        formatter.format(quantity), name, formatter.format(price));
                break;
            case MODIFY_SELL:
                string += String.format("Modify sell offer for %s %s to %s gp",
                        formatter.format(quantity), name, formatter.format(price));
                break;
            case ABORT:
                string += "Abort " + name;
                break;
            case WAIT:
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
                    s.type = SuggestionType.fromApiValue((String) MsgPackUtil.decodePrimitive(b));
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
        if(s.message != null && s.message.contains("Dump alert")) {
            s.isDumpAlert = true;
        }

        return s;
    }

    public static Suggestion decodeProto(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        Suggestion suggestion = new Suggestion();
        try {
            CodedInputStream input = CodedInputStream.newInstance(bytes);
            while (!input.isAtEnd()) {
                int tag = input.readTag();
                if (tag == 0) {
                    break;
                }

                int fieldNumber = WireFormat.getTagFieldNumber(tag);
                switch (fieldNumber) {
                    case 2:
                        suggestion.boxId = input.readInt32();
                        break;
                    case 3:
                        suggestion.type = SuggestionType.fromProtoInt(input.readInt32());
                        break;
                    case 4:
                        suggestion.itemId = input.readInt32();
                        break;
                    case 5:
                        suggestion.quantity = clampToInt(input.readInt64());
                        break;
                    case 6:
                        suggestion.price = clampToInt(input.readInt64());
                        break;
                    case 7:
                        suggestion.id = input.readInt32();
                        break;
                    case 11:
                        suggestion.message = input.readString();
                        break;
                    case 12:
                        suggestion.expectedProfit = input.readDouble();
                        break;
                    case 14:
                        suggestion.isDumpAlert = input.readBool();
                        break;
                    case 15:
                        suggestion.name = input.readString();
                        break;
                    case 16:
                        suggestion.expectedDuration = input.readDouble();
                        break;
                    default:
                        input.skipField(tag);
                }
            }
        } catch (IOException e) {
            log.warn("failed decoding suggestion proto", e);
            return null;
        }

        if (!suggestion.isDumpAlert && suggestion.message != null && suggestion.message.contains("Dump alert")) {
            suggestion.isDumpAlert = true;
        }
        if (suggestion.type == SuggestionType.ABORT) {
            suggestion.isDumpAlert = false;
        }
        return suggestion;
    }

    private static int clampToInt(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }
}
