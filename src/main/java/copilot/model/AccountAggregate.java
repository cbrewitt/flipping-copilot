package copilot.model;

import lombok.*;

@Data
@Builder
public class AccountAggregate {
    public int accountId;
    public String accountName;
    public int numberOfFlips;
    public long biggestLoss, biggestWin;
    public long totalProfit;
}