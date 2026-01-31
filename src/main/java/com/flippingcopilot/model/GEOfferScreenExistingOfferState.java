package com.flippingcopilot.model;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class GEOfferScreenExistingOfferState {

    public final String offerType;
    public final int currentItemId;
    public final int offerPrice;
    public final boolean searchOpen;
    public final int viewedSlotItemId;
    public final int viewedSlotItemPrice;
}
