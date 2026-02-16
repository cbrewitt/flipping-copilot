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

    // Color ranges for amber highlight (R,G,B values)
    private static final int[] AMBER_START = {192, 118, 0};
    private static final int[] AMBER_END = {220, 150, 18};

    // Constants for controlling the drift
    private static final long CYCLE_DURATION = 600000; // 10 minutes for one complete cycle
    private static final int BASE_ALPHA = 79;
    private static final int MIN_ALPHA = 31;
    private static final int DUMP_ALPHA_MAX = 127;
    private static final double DUMP_ALPHA_FREQUENCY_HZ = 2.0;

    public Color getRedColor() {
        return getRedColor(false);
    }

    public Color getRedColor(boolean isDumpAlert) {
        double phase = calculatePhase();
        int alpha = calculateAlpha(isDumpAlert);
        return interpolateColor(RED_START, RED_END, phase, alpha);
    }

    public Color getBlueColor() {
        return getBlueColor(false);
    }

    public Color getBlueColor(boolean isDumpAlert) {
        double phase = calculatePhase();
        int alpha = calculateAlpha(isDumpAlert);
        return interpolateColor(BLUE_START, BLUE_END, phase, alpha);
    }

    public Color getAmberColor() {
        return getAmberColor(false);
    }

    public Color getAmberColor(boolean isDumpAlert) {
        double phase = calculatePhase();
        int alpha = calculateAlpha(isDumpAlert);
        return interpolateColor(AMBER_START, AMBER_END, phase, alpha);
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

    private int calculateAlpha(boolean isDumpAlert) {
        if (!isDumpAlert) {
            return BASE_ALPHA;
        }
        double timeSeconds = System.currentTimeMillis() / 1000.0;
        double phase = (timeSeconds * DUMP_ALPHA_FREQUENCY_HZ) % 1.0;
        double oscillation = 1.0 - Math.abs(2.0 * phase - 1.0);
        return (int) Math.round(MIN_ALPHA + (DUMP_ALPHA_MAX - MIN_ALPHA) * oscillation);
    }
}
