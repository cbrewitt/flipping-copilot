package com.flippingcopilot.model;

import lombok.Getter;

@Getter
public enum IntervalTimeUnit {

    ALL(-1),
    SESSION(-1),
    HOUR(3600),
    DAY(86400),
    WEEK(604800),
    MONTH(2592000),
    YEAR(31536000);

    private final int seconds;;

    IntervalTimeUnit(int seconds) {
        this.seconds = seconds;
    }

    public static IntervalTimeUnit fromString(String str) {
        switch (str) {
            case "h":
                return IntervalTimeUnit.HOUR;
            case "d":
                return IntervalTimeUnit.DAY;
            case "w":
                return IntervalTimeUnit.WEEK;
            case "m":
                return IntervalTimeUnit.MONTH;
            case "y":
                return IntervalTimeUnit.YEAR;
            default:
                return IntervalTimeUnit.ALL;
        }
    }
}
