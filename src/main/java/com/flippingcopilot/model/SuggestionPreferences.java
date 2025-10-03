package com.flippingcopilot.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SuggestionPreferences {

    public List<Integer> blockedItemIds = new ArrayList<>();
    public int timeframe = 5;
    public boolean f2pOnlyMode = false;
}
