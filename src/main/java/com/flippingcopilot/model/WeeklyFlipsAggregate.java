package com.flippingcopilot.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class WeeklyFlipsAggregate {

    int periodStart;
    long profit;
    long gross;
    long taxPaid;
    int flipsMade;

    final DailFlipsAggregate[] dailyFlipsAggregate = new DailFlipsAggregate[]{
            new DailFlipsAggregate(),
            new DailFlipsAggregate(),
            new DailFlipsAggregate(),
            new DailFlipsAggregate(),
            new DailFlipsAggregate(),
            new DailFlipsAggregate(),
            new DailFlipsAggregate(),
    };

    static class DailFlipsAggregate {
        long gross;
        long taxPaid;
        int flipsMade;
        List<UUID> flipIDs = new ArrayList<>(100);
    }

    public void removeFlip(UUID flip, int day) {

    }
}
