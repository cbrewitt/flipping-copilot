package com.flippingcopilot.model;

import org.junit.Test;


public class OfferListTest {
    @Test
    public void testIsEmptySlotNeededWithExistingOfferInSlot() {
        StatusOfferList offerList = new StatusOfferList();
        offerList.set(0, new Offer(OfferStatus.BUY, 565, 200, 10, 0, 0, 0, 0, 0, false, false));
        Suggestion suggestion = new Suggestion("buy", 0, 560, 200, 10, "Death rune", 0, "", null, null, null, null, false, false );
        assert !offerList.isEmptySlotNeeded(suggestion, true);
    }

    @Test
    public void testIsEmptySlotNeededWithNoEmptySlots() {
        StatusOfferList offerList = new StatusOfferList();
        offerList.replaceAll(ignored -> new Offer(OfferStatus.BUY, 565, 200, 10, 0, 0, 0, 0, 0, false, false));
        Suggestion suggestion = new Suggestion("buy", 0, 560, 200, 10, "Death rune", 0, "", null, null, null, null, false, false );
        assert offerList.isEmptySlotNeeded(suggestion, true);
    }

}
