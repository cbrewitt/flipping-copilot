package com.flippingcopilot.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flippingcopilot.ui.graph.model.Data;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class ItemPrice {
    @SerializedName("sell_price")
    @JsonProperty("sp")
    private int sellPrice;

    @SerializedName("buy_price")
    @JsonProperty("bp")
    private  int buyPrice;
    @JsonProperty("m")
    private  String message;

    @SerializedName("graph_data")
    @JsonProperty("gd")
    private Data graphData;

}
