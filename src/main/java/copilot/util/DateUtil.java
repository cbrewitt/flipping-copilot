package copilot.util;

import java.time.format.*;
import java.time.*;

public class DateUtil {

    public static String formatEpoch(long epochSeconds) {
        Instant instant = Instant.ofEpochSecond(epochSeconds);
        var formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withZone(ZoneId.systemDefault());
        return formatter.format(instant);
    }

    public static String formatEpochOrNa(int epochSeconds) {
        return epochSeconds == 0 ? "N/A" : formatEpoch(epochSeconds);
    }
}
