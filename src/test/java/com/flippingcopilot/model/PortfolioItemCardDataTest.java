package com.flippingcopilot.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PortfolioItemCardDataTest {

    @Test
    public void portfolioBankQuantityIsZeroWhenInventoryCoversPortfolio() {
        PortfolioItemCardData item = item(0, 45, 1, 45);

        assertEquals(0, item.getPortfolioBankQuantity());
        assertFalse(item.hasPortfolioQuantityInBank());
    }

    @Test
    public void portfolioBankQuantityIncludesRemainderNotHeldOutsideBank() {
        PortfolioItemCardData item = item(10, 30, 10, 45);

        assertEquals(5, item.getPortfolioBankQuantity());
        assertTrue(item.hasPortfolioQuantityInBank());
    }

    @Test
    public void portfolioBankQuantityDoesNotExceedBankQuantity() {
        PortfolioItemCardData item = item(0, 10, 2, 20);

        assertEquals(2, item.getPortfolioBankQuantity());
    }

    private PortfolioItemCardData item(int geQuantity,
                                       int inventoryQuantity,
                                       int bankQuantity,
                                       int portfolioQuantity) {
        return new PortfolioItemCardData(
                560,
                "Death rune",
                geQuantity,
                inventoryQuantity,
                bankQuantity,
                1,
                180,
                178,
                2L,
                2,
                portfolioQuantity
        );
    }
}
