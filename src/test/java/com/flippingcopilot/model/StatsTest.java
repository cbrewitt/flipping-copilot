package com.flippingcopilot.model;

import org.junit.Assert;
import org.junit.Test;

public class StatsTest {

    @Test
    public void calculateWinRate_countsProfitableFlipsOnly() {
        Stats stats = new Stats();
        stats.addFlip(flipWithProfit(100));
        stats.addFlip(flipWithProfit(0));
        stats.addFlip(flipWithProfit(-100));

        Assert.assertEquals(3, stats.flipsMade);
        Assert.assertEquals(1, stats.winningFlips);
        Assert.assertEquals(1.0f / 3.0f, stats.calculateWinRate(), 0.0001f);
    }

    @Test
    public void calculateWinRate_returnsZeroWithNoFlips() {
        Assert.assertEquals(0.0f, new Stats().calculateWinRate(), 0.0001f);
    }

    private FlipV2 flipWithProfit(long profit) {
        FlipV2 flip = new FlipV2();
        flip.setSpent(1000);
        flip.setProfit(profit);
        return flip;
    }
}
