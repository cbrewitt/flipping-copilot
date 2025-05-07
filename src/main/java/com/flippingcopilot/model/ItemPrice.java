package com.flippingcopilot.model;

import com.flippingcopilot.msgpacklite.MsgpackName;
import com.flippingcopilot.ui.graph.model.Data;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
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
    @MsgpackName("sl")
    private int sellPrice;

    @SerializedName("buy_price")
    @MsgpackName("bp")
    private  int buyPrice;
    @MsgpackName("m")
    private  String message;

    @SerializedName("graph_data")
    @MsgpackName("gd")
    private Data graphData;

}
