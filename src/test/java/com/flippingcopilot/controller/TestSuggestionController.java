package com.flippingcopilot.controller;

import com.flippingcopilot.model.Suggestion;
import org.junit.Test;

public class TestSuggestionController {

    @Test
    public void testShouldNotifyOnWait() {
        Suggestion oldSuggestion = new Suggestion("wait", 0, 0, 0, 0, "", 0, "");
        Suggestion newSuggestion = new Suggestion("wait", 0, 0, 0, 0, "", 1, "");
        assert !SuggestionController.shouldNotify(newSuggestion, oldSuggestion);
    }

    @Test
    public void testShouldNotifyOnNewBuy() {
        Suggestion oldSuggestion = new Suggestion("wait", 0, 0, 0, 0, "", 0, "");
        Suggestion newSuggestion = new Suggestion("buy", 0, 560, 200, 25000, "Death rune", 1, "");
        assert SuggestionController.shouldNotify(newSuggestion, oldSuggestion);
    }

    @Test
    public void testShouldNotifyOnRepeatedBuy() {
        Suggestion oldSuggestion = new Suggestion("buy", 0, 560, 200, 25000, "Death rune", 0, "");;
        Suggestion newSuggestion = new Suggestion("buy", 0, 560, 200, 25000, "Death rune", 1, "");
        assert !SuggestionController.shouldNotify(newSuggestion, oldSuggestion);
    }

    @Test
    public void testShouldNotifyOnAbort() {
        Suggestion oldSuggestion = new Suggestion("wait", 0, 0, 0, 0, "", 0, "");
        Suggestion newSuggestion = new Suggestion("abort", 0, 560, 200, 25000, "Death rune", 1, "");
        assert SuggestionController.shouldNotify(newSuggestion, oldSuggestion);
    }
}
