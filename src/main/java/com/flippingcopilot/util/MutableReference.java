package com.flippingcopilot.util;

import lombok.*;

@AllArgsConstructor
@Getter
@Setter
public class MutableReference<T> {
    private T value;
}
