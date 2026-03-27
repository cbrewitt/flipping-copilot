package com.flippingcopilot.ui;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class UIUtilitiesTest {

    @Test
    public void truncateString_returnsOriginalString_whenLengthIsGreaterThanStringLength() {
        String result = UIUtilities.truncateString("Hello", 10);
        assertEquals("Hello", result);
    }

    @Test
    public void truncateString_returnsTruncatedString_whenLengthIsLessThanStringLength() {
        String result = UIUtilities.truncateString("Hello, World!", 5);
        assertEquals("Hello...", result);
    }

    @Test
    public void truncateString_returnsOriginalString_whenLengthIsEqualToStringLength() {
        String result = UIUtilities.truncateString("Hello", 5);
        assertEquals("Hello", result);
    }

    @Test
    public void truncateString_returnsEmptyString_whenInputStringIsEmpty() {
        String result = UIUtilities.truncateString("", 5);
        assertEquals("", result);
    }

    @Test
    public void truncateString_returnsTruncatedString_whenLengthIsZero() {
        String result = UIUtilities.truncateString("Hello, World!", 0);
        assertEquals("...", result);
    }

    @Test
    public void quantityToRSDecimalStack_largeNegativeValue_includesMinusSign() {
        String result = UIUtilities.quantityToRSDecimalStack(-15000, false);
        assertEquals("-15K", result);
    }

    @Test
    public void quantityToRSDecimalStack_smallNegativeValue_includesMinusSign() {
        String result = UIUtilities.quantityToRSDecimalStack(-406, false);
        assertEquals("-406", result);
    }

    @Test
    public void formatSuggestionDuration_roundsToNearestFiveMinutes_underAnHour() {
        String result = UIUtilities.formatSuggestionDuration(33 * 60);
        assertEquals("35min", result);
    }

    @Test
    public void formatSuggestionDuration_showsHoursAndMinutes_underOneDay() {
        String result = UIUtilities.formatSuggestionDuration((2 * 60 + 7) * 60);
        assertEquals("2h 5m", result);
    }

    @Test
    public void formatSuggestionDuration_showsDaysAndHours_whenAtLeastOneDay() {
        String result = UIUtilities.formatSuggestionDuration((26 * 60 + 29) * 60);
        assertEquals("1d 2h", result);
    }

    @Test
    public void formatSuggestionDuration_roundsToNearestHour_whenAtLeastOneDay() {
        String result = UIUtilities.formatSuggestionDuration((26 * 60 + 31) * 60);
        assertEquals("1d 3h", result);
    }

    @Test
    public void formatSuggestionDuration_omitsHours_whenRoundedToWholeDay() {
        String result = UIUtilities.formatSuggestionDuration((23 * 60 + 58) * 60);
        assertEquals("1d", result);
    }
}
