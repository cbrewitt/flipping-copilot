package com.flippingcopilot.model;

import com.google.common.base.MoreObjects;

public enum FlipStatus {
    BUYING,
    SELLING,
    FINISHED;

    public static FlipStatus fromValue(String value) {
        switch (MoreObjects.firstNonNull(value, "").toUpperCase()) {
            case "O":
                return FlipStatus.BUYING;
            case "C":
                return FlipStatus.SELLING;
            case "F":
                return FlipStatus.FINISHED;
            default:
                return FlipStatus.BUYING;
        }
    }
}
