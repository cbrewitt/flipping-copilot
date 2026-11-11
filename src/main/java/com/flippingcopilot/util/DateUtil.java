package com.flippingcopilot.util;

import java.time.*;
import java.time.format.*;

public class DateUtil {

    public static String formatEpoch(long epochSeconds) {
        Instant instant = Instant.ofEpochSecond(epochSeconds);
        DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withZone(ZoneId.systemDefault());
        return formatter.format(instant);
    }

    public static String formatEpochOrNa(int epochSeconds) {
        return epochSeconds == 0 ? "N/A" : formatEpoch(epochSeconds);
    }
}
