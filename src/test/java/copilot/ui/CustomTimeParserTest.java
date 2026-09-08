package copilot.ui;

import org.junit.Test;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

public class CustomTimeParserTest {
    @Test public void preservesAllSyntaxesAndNumericBoundaries() throws Exception {
        Method parse = ControlPanel.class.getDeclaredMethod("parseCustomTimeMinutes", String.class);
        parse.setAccessible(true);
        String[] values = {"", " ", "0", "1", "59", "60", "1440", "1441", "-1", "+1", ".5", "1.5",
                "2147483647", "2147483648", "4294967296", "9999999999999999999999999999999999999999999999",
                "NaN", "Infinity", "１", "1e2", "1:30", "1:30:", ":30", "1::30", "2147483647:59"};
        String[] units = {"", "m", "M", "min", "mins", "minute", "minutes", "h", "H", "hr", "hrs",
                "hour", "hours", "foo", ":00", ":59", ":60", ":-1", ":", " 30m", "h 30m", "h1m"};
        assertNull(parse.invoke(null, new Object[]{null}));
        for (String value : values) {
            for (String unit : units) {
                for (String suffix : new String[]{"", " ", "!", " 1h", "\t"}) {
                    String input = " " + value + unit + suffix;
                    assertEquals(input, ReferenceCustomTime.parse(input), parse.invoke(null, input));
                }
            }
        }
    }
}
