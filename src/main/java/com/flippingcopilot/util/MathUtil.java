package com.flippingcopilot.util;

public class MathUtil {

    public static int clamp(int x, int min, int max) {
        if (x > max) {
            return max;
        } else {
            return Math.max(x, min);
        }
    }
}
