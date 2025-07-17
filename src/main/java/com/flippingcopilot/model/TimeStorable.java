package com.flippingcopilot.model;

import java.util.UUID;

public interface TimeStorable {
    UUID getId();
    int getTime();
    String getFileName();
    String toJson();
}
