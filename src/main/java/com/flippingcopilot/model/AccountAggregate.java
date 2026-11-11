package com.flippingcopilot.model;

import lombok.*;


@Data
@Builder
public class AccountAggregate {
    private int accountId;
    private String accountName;
    private int numberOfFlips;
    private long biggestLoss;
    private long biggestWin;
    private long totalProfit;
}