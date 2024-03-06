package com.flippingcopilot.controller;

import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.GrandExchangeOfferChanged;

public class SlotOfferEventFilter {
    private GrandExchangeOfferChanged slotPreviousEvent;
    private boolean emptyLoginEventReceived = false;

    boolean shouldProcess(GrandExchangeOfferChanged event) {
        boolean shouldProcess = false;
        boolean isEmpty = event.getOffer().getState().equals(GrandExchangeOfferState.EMPTY);
        boolean isEmptyLoginEvent = !emptyLoginEventReceived && isEmpty;

        if (!isEmptyLoginEvent && (slotPreviousEvent == null || !eventsEqual(slotPreviousEvent, event))) {
            shouldProcess = true;
            slotPreviousEvent = event;
        }
        emptyLoginEventReceived = true;
        return shouldProcess;
    }

    private static boolean eventsEqual(GrandExchangeOfferChanged event1, GrandExchangeOfferChanged event2) {
        GrandExchangeOffer offer1 = event1.getOffer();
        GrandExchangeOffer offer2 = event2.getOffer();
        return offer1.getItemId() == offer2.getItemId()
                && offer1.getQuantitySold() == offer2.getQuantitySold()
                && offer1.getTotalQuantity() == offer2.getTotalQuantity()
                && offer1.getPrice() == offer2.getPrice()
                && offer1.getSpent() == offer2.getSpent()
                && offer1.getState() == offer2.getState();
    }

    void onLogout() {
        emptyLoginEventReceived = false;
    }
    void setToLoggedIn() {
        emptyLoginEventReceived = true;
    }
}
