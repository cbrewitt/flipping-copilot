package com.flippingcopilot.util;

import com.flippingcopilot.model.FlipV2;

import java.util.List;

public class FlipUtils {

    public static long AverageUnclosedBuyPrice(List<FlipV2> flips) {
        long t = 0;
        long q = 0;
        for (FlipV2 f : flips) {
            long unclosedQuantity = getUnclosedQuantity(f);
            t += f.getAvgBuyPrice() * unclosedQuantity;
            q += unclosedQuantity;
        }
        if (q <= 0) {
            return 0;
        }
        return t / q;
    }

    public static int getUnclosedQuantity(FlipV2 flip) {
        if (flip == null) {
            return 0;
        }
        return Math.max(0, flip.getOpenedQuantity() - flip.getClosedQuantity());
    }

    public static int totalUnclosedQuantity(List<FlipV2> flips) {
        int total = 0;
        if (flips == null) {
            return total;
        }
        for (FlipV2 flip : flips) {
            total += getUnclosedQuantity(flip);
        }
        return total;
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
