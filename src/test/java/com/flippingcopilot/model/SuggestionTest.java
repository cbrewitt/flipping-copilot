package com.flippingcopilot.model;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SuggestionTest {

    @Test
    public void onlyBuyDumpSuggestionsFlashHighlights() {
        assertTrue(suggestion(SuggestionType.BUY, true).isBuyDumpSuggestion());

        assertFalse(suggestion(SuggestionType.BUY, false).isBuyDumpSuggestion());
        assertFalse(suggestion(SuggestionType.MODIFY_BUY, true).isBuyDumpSuggestion());
        assertFalse(suggestion(SuggestionType.SELL, true).isBuyDumpSuggestion());
        assertFalse(suggestion(SuggestionType.MODIFY_SELL, true).isBuyDumpSuggestion());
        assertFalse(suggestion(SuggestionType.ABORT, true).isBuyDumpSuggestion());
    }

    private static Suggestion suggestion(SuggestionType type, boolean isDumpAlert) {
        Suggestion suggestion = new Suggestion();
        suggestion.setType(type);
        suggestion.setDumpAlert(isDumpAlert);
        return suggestion;
    }
}
