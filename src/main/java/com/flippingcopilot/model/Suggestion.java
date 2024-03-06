package com.flippingcopilot.model;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.text.NumberFormat;

@Getter
@AllArgsConstructor
@ToString
public class Suggestion {
    private final String type;

    @SerializedName("box_id")
    private final int boxId;

    @SerializedName("item_id")
    private final int itemId;

    private final int price;

    private final int quantity;

    private final String name;

    @SerializedName("command_id")
    private final int id;

    private final String message;

    public static Suggestion fromJson(JsonObject json) {
        Gson gson = new Gson();
        return gson.fromJson(json, Suggestion.class);
    }

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


