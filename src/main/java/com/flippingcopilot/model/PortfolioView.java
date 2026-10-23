package com.flippingcopilot.model;

import lombok.Value;

import java.util.List;

@Value
public class PortfolioView {
    PortfolioSummaryData summary;
    List<PortfolioRow> rows;
    // the logged in account's own portfolio, the only editable view
    boolean liveAccount;
    List<Source> sources;
    // accounts without a snapshot yet
    List<String> missingAccounts;

    @Value
    public static class Source {
        String displayName;
        Long savedAt; // epoch seconds, null for the live account
    }
}
