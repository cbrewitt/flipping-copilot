package com.flippingcopilot.model;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@ToString
public class ItemPrice {
    @SerializedName("sell_price")
    private final int sellPrice;

    @SerializedName("buy_price")
    private final int buyPrice;
    private final String message;

    public static ItemPrice fromJson(JsonObject json, Gson gson) {
        return gson.fromJson(json, ItemPrice.class);
    }
}
