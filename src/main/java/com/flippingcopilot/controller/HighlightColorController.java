package com.flippingcopilot.controller;

import javax.inject.Singleton;
import java.awt.*;

@Singleton
public class HighlightColorController {

    /*
    For the highlighted colors we oscillate the actual colors through different shades
     */

    // Color ranges for red highlight (R,G,B values)
    private static final int[] RED_START = {205, 0, 50};
    private static final int[] RED_END = {255, 50, 0};

    // Color ranges for blue highlight (R,G,B values)
    private static final int[] BLUE_START = {5, 153, 255};
    private static final int[] BLUE_END = {55, 205, 205};

    // Constants for controlling the drift
    private static final long CYCLE_DURATION = 600000; // 10 minutes for one complete cycle
    private static final int ALPHA = 79;

    public Color getRedColor() {
        double phase = calculatePhase();
        return interpolateColor(RED_START, RED_END, phase, ALPHA);
    }

    public Color getBlueColor() {
        double phase = calculatePhase();
        return interpolateColor(BLUE_START, BLUE_END, phase, ALPHA);
    }

    private double calculatePhase() {
        // Current time modulo cycle duration gives us position in the cycle
        long currentTime = System.currentTimeMillis();
        double cyclePosition = (currentTime % CYCLE_DURATION) / (double) CYCLE_DURATION;

        // Use absolute sine wave to create smooth back-and-forth oscillation
        return Math.abs(Math.sin(cyclePosition * Math.PI));
    }

    private Color interpolateColor(int[] start, int[] end, double phase, int alpha) {
        int red = interpolateComponent(start[0], end[0], phase);
        int green = interpolateComponent(start[1], end[1], phase);
        int blue = interpolateComponent(start[2], end[2], phase);

        return new Color(red, green, blue, alpha);
    }

    private int interpolateComponent(int start, int end, double phase) {
        return (int) Math.round(start + (end - start) * phase);
    }
}