package com.flippingcopilot.util;
import com.flippingcopilot.model.Market;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ProfitCalculatorTest {

    @Test
    public void postTaxPriceForExemptItemReturnsOriginalPrice() {
        int itemId = 13190;
        int price = 1000;
        assertEquals(price, ProfitCalculator.getPostTaxPrice(itemId, price, Market.OSRS));
    }

    @Test
    public void postTaxPriceNormalItem() {
        int itemId = 9999;
        int price = 1000;
        assertEquals(980, ProfitCalculator.getPostTaxPrice(itemId, price, Market.OSRS));
    }

    @Test
    public void postTaxPriceAboveCap() {
        int itemId = 9999;
        int price = 600000000;
        assertEquals(595000000, ProfitCalculator.getPostTaxPrice(itemId, price, Market.OSRS));
    }

    @Test
    public void postTaxPriceCheapItem() {
        int itemId = 13190;
        int price = 67;
        assertEquals(price, ProfitCalculator.getPostTaxPrice(itemId, price, Market.OSRS));
    }
    @org.junit.Test
    public void hardcoreSalesHaveNoTaxAtAnyPrice() {
        for (long price : new long[]{49, 1000, 250000000, 3000000000L}) {
            assertEquals(0, ProfitCalculator.getTaxAmount(560, price, Market.HC));
            assertEquals(price, ProfitCalculator.getPostTaxPrice(560, price, Market.HC));
        }
        assertEquals(200, ProfitCalculator.calculateProfitPerItem(560, 1000, 800, Market.HC));
        assertEquals(180, ProfitCalculator.calculateProfitPerItem(560, 1000, 800, Market.OSRS));
    }
}
