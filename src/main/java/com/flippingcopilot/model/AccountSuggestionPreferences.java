package com.flippingcopilot.model;

import lombok.Data;

@Data
public class AccountSuggestionPreferences {
    public int timeframe = 5;
    public boolean f2pOnlyMode = false;
    public RiskLevel riskLevel = RiskLevel.MEDIUM;
    public int reservedSlots = 0;
    public boolean receiveDumpSuggestions = true;
}
