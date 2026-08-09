package com.flippingcopilot.util;

/** Human readable sizes/durations for log lines (user facing durations live in UIUtilities). */
public class FormatUtil {

    public static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    public static String formatDuration(long nanos) {
        if (nanos < 0) {
            return "n/a";
        }
        double millis = nanos / 1_000_000.0;
        if (millis < 1000.0) {
            return String.format("%.0f ms", millis);
        }
        return String.format("%.2f s", millis / 1000.0);
    }

    public static String formatDurationSince(long startNanos) {
        return formatDuration(System.nanoTime() - startNanos);
    }
}
