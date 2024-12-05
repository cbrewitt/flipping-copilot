package com.flippingcopilot.controller;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.GrandExchangeOfferChanged;

@Slf4j
public class SlotOfferEventFilter {
    @Getter
    private GrandExchangeOfferChanged slotPreviousEvent;
    private boolean emptyLoginEventReceived = false;

    boolean shouldProcess(GrandExchangeOfferChanged event) {
        boolean isEmpty = event.getOffer().getState().equals(GrandExchangeOfferState.EMPTY);

        if (!emptyLoginEventReceived && isEmpty) {
            log.debug("skipping event because its the empty login event: {}", event);
            emptyLoginEventReceived = true;
            return false;
        }
        if (slotPreviousEvent != null && eventsEqual(slotPreviousEvent, event)) {
            log.debug("skipping event because it is equal to previous event: {}", event);
            return false;
        }

        emptyLoginEventReceived = true;
        slotPreviousEvent = event;
        log.debug("processing event {}", event);

        return true;
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
