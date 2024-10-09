package com.flippingcopilot.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class Stats {

    public long profit;
    public long gross;
    public long taxPaid;
    public int flipsMade;

    public float calculateRoi() {
        if (gross == 0){
            return 0;
        }
        return (float) (((double) profit) / ((double) gross));
    }

    public Stats copy() {
        return new Stats(profit, gross, taxPaid, flipsMade);
    }

    public void add(Stats s) {
        if(s != null) {
            profit += s.profit;
            gross += s.gross;
            taxPaid += s.taxPaid;
            flipsMade += s.flipsMade;
        }
    }

    public void addFlip(FlipV2 f) {
        profit += f.getProfit();
        gross += f.getSpent();
        taxPaid += f.getTaxPaid();
        flipsMade += 1;
    }

    public void subtractFlip(FlipV2 f) {
        profit -= f.getProfit();
        gross -= f.getSpent();
        taxPaid -= f.getTaxPaid();
        flipsMade -= 1;
    }
}
