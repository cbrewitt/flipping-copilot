package com.flippingcopilot.model;

import com.flippingcopilot.ui.graph.model.Data;
import com.flippingcopilot.msgpacklite.MsgpackName;
import com.google.gson.annotations.SerializedName;
import lombok.*;

import java.text.NumberFormat;

@Getter
@AllArgsConstructor
@ToString
@NoArgsConstructor
public class Suggestion {

    @MsgpackName("t")
    private String type;

    @MsgpackName("b")
    @SerializedName("box_id")
    private int boxId;

    @MsgpackName("i")
    @SerializedName("item_id")
    private int itemId;

    @MsgpackName("p")
    private int price;

    @MsgpackName("q")
    private int quantity;

    @MsgpackName("n")
    private String name;

    @MsgpackName("id")
    @SerializedName("command_id")
    private int id;

    @MsgpackName("m")
    private String message;

    @MsgpackName("gd")
    @SerializedName("graph_data")
    @Setter
    private Data graphData;


    public boolean equals(Suggestion other) {
        return this.type.equals(other.type)
                && this.boxId == other.boxId
                && this.itemId == other.itemId
                && this.name.equals(other.name);
    }

    public String toMessage() {
        NumberFormat formatter = NumberFormat.getNumberInstance();
        String string = "Flipping Copilot: ";
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
}


