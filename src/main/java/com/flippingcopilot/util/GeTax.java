package com.flippingcopilot.util;

import java.util.Arrays;
import java.util.HashSet;

public class GeTax {
    private final static int MAX_PRICE_FOR_GE_TAX = 250000000;
    private final static int GE_TAX_CAP = 5000000;
    private final static double GE_TAX = 0.02;
    private final static HashSet<Integer> GE_TAX_EXEMPT_ITEMS = new HashSet<>(
            Arrays.asList(8011, 365, 2309, 882, 806, 1891, 8010, 1755, 28824, 2140, 2142, 8009, 5325, 1785, 2347, 347,
                    884, 807, 28790, 379, 8008, 355, 2327, 558, 1733, 13190, 233, 351, 5341, 2552, 329, 8794, 5329,
                    5343, 1735, 315, 952, 886, 808, 8013, 361, 8007, 5331));

    public static int getPostTaxPrice(int itemId, int price) {
        if (GE_TAX_EXEMPT_ITEMS.contains(itemId)) {
            return price;
        }
        if (price >= MAX_PRICE_FOR_GE_TAX) {
            return price - GE_TAX_CAP;
        }
        int tax = (int)Math.floor(price * GE_TAX);
        return price - tax;
    }
}
