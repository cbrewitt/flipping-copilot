package copilot.model;

import org.junit.Test;
import java.text.NumberFormat;
import java.util.*;
import static org.junit.Assert.*;

public class SuggestionMessageTest {
    @Test public void messagesPreserveActionWordingHoldFlagsAndLocalizedNumbers() {
        Locale previous = Locale.getDefault();
        try {
            for (Locale locale : new Locale[]{Locale.US, Locale.GERMANY}) {
                Locale.setDefault(locale);
                for (boolean hold : new boolean[]{false, true}) {
                    for (boolean dump : new boolean[]{false, true}) {
                        Suggestion suggestion = new Suggestion();
                        suggestion.quantity = 12_345;
                        suggestion.price = 9_876_543_210L;
                        suggestion.name = "Rune arrows";
                        suggestion.setHold(hold);
                        suggestion.isDumpAlert = dump;
                        NumberFormat numbers = NumberFormat.getNumberInstance();
                        String items = numbers.format(suggestion.quantity) + " Rune arrows";
                        String price = numbers.format(suggestion.price) + " gp";
                        Map<SuggestionType, String> expected = new EnumMap<>(SuggestionType.class);
                        expected.put(SuggestionType.BUY, (hold ? "Buy and hold " : "Buy ") + items + " for " + price);
                        expected.put(SuggestionType.SELL, "Sell " + items + " for " + price);
                        expected.put(SuggestionType.MODIFY_BUY, "Modify buy offer for " + items + " to " + price);
                        expected.put(SuggestionType.MODIFY_SELL, "Modify sell offer for " + items + " to " + price);
                        expected.put(SuggestionType.ABORT, "Abort Rune arrows");
                        expected.put(SuggestionType.WAIT, "Wait");
                        String prefix = dump ? "DUMP ALERT!! " : "Flipping Copilot: ";
                        expected.forEach((type, message) -> {
                            suggestion.type = type;
                            assertEquals(prefix + message, suggestion.toMessage());
                        });
                        suggestion.type = null;
                        assertEquals(prefix + "Unknown suggestion type", suggestion.toMessage());
                    }
                }
            }
        } finally {
            Locale.setDefault(previous);
        }
    }
}
