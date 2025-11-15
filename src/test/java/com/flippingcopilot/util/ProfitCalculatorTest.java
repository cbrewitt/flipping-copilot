package com.flippingcopilot.util;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ProfitCalculatorTest {

    @Test
    public void postTaxPriceForExemptItemReturnsOriginalPrice() {
        int itemId = 13190;
        int price = 1000;
        assertEquals(price, ProfitCalculator.getPostTaxPrice(itemId, price));
    }

    @Test
    public void postTaxPriceNormalItem() {
        int itemId = 9999;
        int price = 1000;
        assertEquals(980, ProfitCalculator.getPostTaxPrice(itemId, price));
    }

    @Test
    public void postTaxPriceAboveCap() {
        int itemId = 9999;
        int price = 600000000;
        assertEquals(595000000, ProfitCalculator.getPostTaxPrice(itemId, price));
    }

    @Test
    public void postTaxPriceCheapItem() {
        int itemId = 13190;
        int price = 67;
        assertEquals(price, ProfitCalculator.getPostTaxPrice(itemId, price));
    }
}
