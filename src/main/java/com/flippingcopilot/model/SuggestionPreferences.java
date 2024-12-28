package com.flippingcopilot.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SuggestionPreferences {

    private boolean sellOnlyMode = false;
    private List<Integer> blockedItemIds = new ArrayList<>();
}