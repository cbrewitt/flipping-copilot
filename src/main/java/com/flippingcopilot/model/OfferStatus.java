package com.flippingcopilot.model;

import com.google.gson.annotations.SerializedName;
import net.runelite.api.GrandExchangeOfferState;

public enum OfferStatus {
    @SerializedName("buy")
    BUY,
    @SerializedName("sell")
    SELL,
    @SerializedName("empty")
    EMPTY;

    public int protoInt() {
        switch (this) {
            case BUY:
                return 1;
            case SELL:
                return 2;
            case EMPTY:
                return 3;
            default:
                return 0;
        }
    }

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
