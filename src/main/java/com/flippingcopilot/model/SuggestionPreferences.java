package com.flippingcopilot.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SuggestionPreferences {

    private boolean f2pOnlyMode = false;
    private boolean sellOnlyMode = false;
    private List<Integer> blockedItemIds = new ArrayList<>();
    private int timeframe = 5;
}
