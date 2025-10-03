package com.flippingcopilot.ui.flipsdialog;

import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

@RequiredArgsConstructor
public class Datapoint {
    public final LocalDate t;
    public final long cumulativeProfit;
    public final long dailyProfit;
    public boolean isDailyProfitHovered;
    public boolean isCumulativeProfitHovered;

    public long timestamp()  {
        return t.toEpochSecond(LocalTime.MIN, ZoneOffset.UTC);
    }
}
