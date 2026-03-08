package com.flippingcopilot.util;

import com.flippingcopilot.model.FlipV2;

import java.util.List;

public class FlipUtils {

    public static long AverageUnclosedBuyPrice(List<FlipV2> flips) {
        long t = 0;
        long q = 0;
        for (FlipV2 f : flips) {
            t += f.getAvgBuyPrice() * (f.getOpenedQuantity() - f.getClosedQuantity());
            q += (f.getOpenedQuantity() - f.getClosedQuantity());
        }
        return t / q;
    }

    public static boolean inPortfolio(List<FlipV2> itemFlips) {
        if(itemFlips == null) {
            return false;
        }
        for (FlipV2 f : itemFlips) {
            if(f.getPortfolioId() != 0) {
                return false;
            }
        }
        return !itemFlips.isEmpty();
    }
}
