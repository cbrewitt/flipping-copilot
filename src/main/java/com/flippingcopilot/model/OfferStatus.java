package com.flippingcopilot.model;

import com.google.gson.annotations.SerializedName;
import net.runelite.api.GrandExchangeOfferState;

public enum OfferStatus {
    @SerializedName("sell")
    SELL,
    @SerializedName("buy")
    BUY,
    @SerializedName("empty")
    EMPTY;

    static OfferStatus fromRunelite(GrandExchangeOfferState state) {
        OfferStatus status;
        switch (state) {
            case SELLING:
            case CANCELLED_SELL:
            case SOLD:
                status = SELL;
                break;
            case BUYING:
            case CANCELLED_BUY:
            case BOUGHT:
                status = BUY;
                break;
            default:
                status = EMPTY;
        }
        return status;
    }
}
