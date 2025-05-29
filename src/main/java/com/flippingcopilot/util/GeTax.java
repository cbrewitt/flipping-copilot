package com.flippingcopilot.util;

import java.util.Arrays;
import java.util.HashSet;

public class GeTax {
    private final static int MAX_PRICE_FOR_GE_TAX = 500000000;
    private final static int GE_TAX_CAP = 5000000;
    private final static double GE_TAX = 0.02;
    private final static HashSet<Integer> GE_TAX_EXEMPT_ITEMS = new HashSet<>(
            Arrays.asList(13190, 1755, 5325, 1785, 2347, 1733, 233, 5341, 8794, 5329, 5343, 1735, 952, 5331));

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
