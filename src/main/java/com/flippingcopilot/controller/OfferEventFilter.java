package com.flippingcopilot.controller;
import com.flippingcopilot.model.OfferList;
import net.runelite.api.events.GrandExchangeOfferChanged;


public class OfferEventFilter {
    private final SlotOfferEventFilter[] slotOfferEventFilters;

    public OfferEventFilter() {
        slotOfferEventFilters = new SlotOfferEventFilter[OfferList.NUM_SLOTS];
        for(int i = 0; i < OfferList.NUM_SLOTS; i++) {
            slotOfferEventFilters[i] = new SlotOfferEventFilter();
        }
    }

    public boolean shouldProcess(GrandExchangeOfferChanged event) {

        return slotOfferEventFilters[event.getSlot()].shouldProcess(event);
    }

    void setExpectEmptyOffers() {
        for(SlotOfferEventFilter slotOfferEventFilter : slotOfferEventFilters) {
            slotOfferEventFilter.onLogout();
        }
    }

    void setToLoggedIn() {
        for(SlotOfferEventFilter slotOfferEventFilter : slotOfferEventFilters) {
            slotOfferEventFilter.setToLoggedIn();
        }
    }
}
