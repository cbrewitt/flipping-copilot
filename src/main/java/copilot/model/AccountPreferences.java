package copilot.model;

import lombok.*;

@Data
public class AccountPreferences {
    public int timeframe = 5;
    public boolean buyAndHold = true, f2pOnlyMode = false;
    public RiskLevel riskLevel = RiskLevel.MEDIUM;
    public Integer reservedSlots = null;
    public boolean receiveDumpSuggestions = false;
    public Long minPredictedProfit = null, dumpMinPredictedProfit = null;
    public String selectedProfile = null;
}
