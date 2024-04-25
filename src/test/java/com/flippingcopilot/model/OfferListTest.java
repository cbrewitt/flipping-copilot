package com.flippingcopilot.model;

import org.junit.Test;


public class OfferListTest {
    @Test
    public void testIsEmptySlotNeededWithExistingOfferInSlot() {
        OfferList offerList = new OfferList();
        offerList.set(0, new Offer(OfferStatus.BUY, 565, 200, 10, 0, 0, 0, 0, 0, false));
        Suggestion suggestion = new Suggestion("buy", 0, 560, 200, 10, "Death rune", 0, "");
        assert !offerList.isEmptySlotNeeded(suggestion);
    }

    @Test
    public void testIsEmptySlotNeededWithNoEmptySlots() {
        OfferList offerList = new OfferList();
        offerList.replaceAll(ignored -> new Offer(OfferStatus.BUY, 565, 200, 10, 0, 0, 0, 0, 0, false));
        Suggestion suggestion = new Suggestion("buy", 0, 560, 200, 10, "Death rune", 0, "");
        assert offerList.isEmptySlotNeeded(suggestion);
    }

}
