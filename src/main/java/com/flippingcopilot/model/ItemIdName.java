package com.flippingcopilot.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@AllArgsConstructor
@EqualsAndHashCode
public class ItemIdName {
    public final Integer itemId;
    public final String name;

    public String toString() {
        return name;
    }
}
