package com.flippingcopilot.model;

import lombok.Data;

@Data
public class AccountSuggestionPreferences {
    public int timeframe = 5;
    public boolean f2pOnlyMode = false;
    public RiskLevel riskLevel = RiskLevel.MEDIUM;
    public Integer reservedSlots = null;
    public boolean receiveDumpSuggestions = false;
    public Integer minPredictedProfit = null;
    public String selectedProfile = null;
}
