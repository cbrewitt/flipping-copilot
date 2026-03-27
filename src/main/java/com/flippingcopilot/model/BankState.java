package com.flippingcopilot.model;

import lombok.Value;

import java.util.Collections;
import java.util.Map;

@Value
public class BankState {
    boolean loaded;
    Map<Integer, Integer> items;

    public static BankState empty() {
        return new BankState(false, Collections.emptyMap());
    }
}
