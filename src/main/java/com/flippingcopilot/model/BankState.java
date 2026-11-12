package com.flippingcopilot.model;

import lombok.Value;

import java.util.*;

@Value
public class BankState {
    boolean loaded;
    Map<Integer, Integer> items;
    Long loadedAccountHash;

    public static BankState empty() {
        return new BankState(false, Collections.emptyMap(), null);
    }
}
