package com.flippingcopilot.ui.graph;


@FunctionalInterface
public interface CoordinateConverter {
    int toValue(int coordinate);
}