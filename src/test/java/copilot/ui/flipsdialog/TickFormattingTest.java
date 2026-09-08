package copilot.ui.flipsdialog;
import org.junit.Test;
import java.util.*;
import java.util.function.Function;
import static org.junit.Assert.*;

public class TickFormattingTest {
    @Test public void consolidatedFormatterPreservesBoundariesAndCallTimeLocale() {
        Locale original = Locale.getDefault();
        try {
            for (long range : new long[]{Long.MIN_VALUE, -1, 0, 999, 1000, 1001, 999999,
                    1000000, 1000001, 999999999, 1000000000, Long.MAX_VALUE}) {
                Function<Long, String> expected = reference(range), actual = AxisCalculator.formatFunc(range);
                for (Locale locale : new Locale[]{Locale.US, Locale.FRANCE, Locale.GERMANY, new Locale("ar")}) {
                    Locale.setDefault(locale);
                    for (long value : new long[]{Long.MIN_VALUE, -1000000001, -1000000, -1500, -1000, -1,
                            0, 1, 999, 1000, 1499, 1500, 1000001, 1000000001, Long.MAX_VALUE}) {
                        assertEquals("range=" + range + " value=" + value + " locale=" + locale,
                                expected.apply(value), actual.apply(value));
                    }
                }
            }
        } finally { Locale.setDefault(original); }
    }

    public static Function<Long, String> reference(long range) {
        return (v) -> {
            String sign = v < 0 ? "-" : "";
            long absProfit = Math.abs(v);
            if (range >= 1_000_000_000) {
                return sign + String.format("%.1fB", absProfit / 1_000_000_000.0).replace(".0B", "B");
            } else if (range >= 1_000_000) {
                return sign + String.format("%.1fM", absProfit / 1_000_000.0).replace(".0M", "M");
            } else if (range >= 1_000) {
                return sign + String.format("%.1fK", absProfit / 1_000.0).replace(".0K", "K");
            } else {
                return sign + absProfit;
            }
        };
    }

}
