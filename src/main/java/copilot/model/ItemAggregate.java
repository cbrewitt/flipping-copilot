package copilot.model;

import lombok.*;

@Data
@Builder
public class ItemAggregate {
    private final int itemId;
    public final String itemName;
    public final int numberOfFlips, totalQuantityFlipped;
    public final long biggestLoss, biggestWin;
    public final long totalProfit, avgProfit;
    public final long avgProfitEa;
}