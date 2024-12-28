package com.flippingcopilot.model;

import lombok.Getter;
import lombok.Setter;

import javax.inject.Singleton;
import java.time.Instant;

@Singleton
@Getter
@Setter
public class SuggestionManager {

    private boolean suggestionNeeded;
    private boolean suggestionRequestInProgress;
    private Instant lastFailureAt;
    private HttpResponseException suggestionError;
    private Suggestion suggestion;
    private Instant suggestionReceivedAt;
    private int lastOfferSubmittedTick = -1;

    public void setSuggestion(Suggestion suggestion) {
        this.suggestion = suggestion;
        suggestionReceivedAt = Instant.now();
    }

    public void setSuggestionError(HttpResponseException error) {
        this.suggestionError = error;
        lastFailureAt= Instant.now();
    }

    public void reset() {
        suggestionNeeded = false;
        suggestion = null;
        suggestionReceivedAt = null;
        lastFailureAt = null;
        lastOfferSubmittedTick = -1;
        suggestionError = null;
    }

    public boolean suggestionOutOfDate() {
        Instant tenSecondsAgo = Instant.now().minusSeconds(10L);
        if (suggestionReceivedAt == null || tenSecondsAgo.isAfter(suggestionReceivedAt)) {
            return lastFailureAt == null || tenSecondsAgo.isAfter(lastFailureAt);
        }
        return false;
    }
}
