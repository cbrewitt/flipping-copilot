package copilot.model;

import java.util.*;
import lombok.*;

@Value
public class PortfolioState {
    public boolean loaded;
    public Map<Integer, PortfolioItem> itemCardDataByItemId;
    public PortfolioSummary summaryData;

    public static PortfolioState empty() {
        return new PortfolioState(false, Collections.emptyMap(), new PortfolioSummary(0L, 0L, 0L, 0L, 0L));
    }
}
