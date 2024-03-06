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
}