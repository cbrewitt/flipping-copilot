package copilot.ui.components;

import org.junit.Test;
import java.lang.reflect.Method;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class IntervalDropdownTest {
    @Test public void dateFormattingPreservesBoundariesAndLocale() throws Exception {
        Locale original = Locale.getDefault();
        Method convert = IntervalDropdown.class.getDeclaredMethod("convertDateToIntervalString", Date.class);
        convert.setAccessible(true);
        try {
            for (Locale locale : new Locale[]{Locale.US, Locale.GERMANY}) {
                Locale.setDefault(locale);
                IntervalDropdown dropdown = new IntervalDropdown((unit, value) -> {}, IntervalDropdown.ALL_TIME, true);
                for (int days : new int[]{-2, 0, 1, 6, 7, 8, 14, 29, 30, 31, 60, 364, 365, 366, 730}) {
                    LocalDate selected = LocalDate.now().minusDays(days);
                    Date date = Date.from(selected.atStartOfDay(ZoneId.systemDefault()).toInstant());
                    String expected;
                    if (days < 0) expected = -days + "d (Future)";
                    else if (days == 0) expected = "-0h";
                    else if (days < 7) expected = "-" + days + "d";
                    else {
                        double value = days / (days < 30 ? 7.0 : days < 365 ? 30.0 : 365.0);
                        String unit = days < 30 ? "w" : days < 365 ? "m" : "y";
                        expected = value == Math.floor(value) ? "-" + (int) value + unit : String.format("-%.1f", value) + unit;
                    }
                    assertEquals(locale + " days=" + days, expected, convert.invoke(dropdown, date));
                }
            }
        } finally {
            Locale.setDefault(original);
        }
    }
}
