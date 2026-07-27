package com.flippingcopilot.model;

import java.util.*;

public enum SuggestionType {
    BUY(1),
    SELL(2),
    ABORT(3),
    MODIFY_BUY(4),
    MODIFY_SELL(5),
    WAIT(6);

    private final int protoInt;

    SuggestionType(int protoInt) {
        this.protoInt = protoInt;
    }

    public int protoInt() {
        return protoInt;
    }

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return apiValue();
    }

    public static SuggestionType fromProtoInt(int protoInt) {
        for (SuggestionType value : values()) {
            if (value.protoInt == protoInt) {
                return value;
            }
        }
        return null;
    }

    public static Set<SuggestionType> abortAndModifyTypes() {
        return new HashSet<>(Set.of(ABORT, MODIFY_BUY, MODIFY_SELL));
    }

}
