package com.flippingcopilot.model;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@AllArgsConstructor
@Data
public class DataDeltaRequest {

    @SerializedName("account_id_time")
    private final Map<Integer, Integer> accountIdTime;

}
