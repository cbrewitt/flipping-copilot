package com.flippingcopilot.model;


import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;



@AllArgsConstructor
public class SessionData {

    @SerializedName("start_time")
    public int startTime;

    @SerializedName("duration_millis")
    public long durationMillis;

    @SerializedName("average_cash")
    public long averageCash;
}
