package com.flippingcopilot.model;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class GEOfferScreenSetupOfferState {

    public final String offerType;
    public final int currentItemId;
    public final int offerPrice;
    public final int offerQuantity;
    public final boolean searchOpen;

    public boolean offerDetailsCorrect(Suggestion suggestion) {
        return offerType.equals(suggestion.getType())
                && currentItemId == suggestion.getItemId()
                && offerPrice == suggestion.getPrice()
                && offerQuantity == suggestion.getQuantity();
    }

    public boolean isEmptyBuyState() {
        return offerType.equals("buy") && currentItemId == -1;
    }
}
