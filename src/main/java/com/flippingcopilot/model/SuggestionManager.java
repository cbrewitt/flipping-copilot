package com.flippingcopilot.model;

import lombok.Getter;
import lombok.Setter;

import javax.inject.Singleton;
import java.time.Instant;

@Singleton
@Getter
@Setter
public class SuggestionManager {

    private volatile boolean suggestionNeeded;
    private volatile boolean suggestionRequestInProgress;
    private volatile boolean graphDataReadingInProgress;
    private Instant lastFailureAt;
    private HttpResponseException suggestionError;
    private Suggestion suggestion;
    private Instant suggestionReceivedAt;
    private int lastOfferSubmittedTick = -1;

    // these two variables get set based on the current suggestion when the confirm offer button is clicked.
    // this allows us to track on the subsequent offer events whether the offer originates from a copilot suggestion
    // this flag can then eventually be propagated onto each transaction and can be used by the server to
    // determine which items in the inventory were bought based upon copilot suggestions and which are not
    private int suggestionItemIdOnOfferSubmitted = -1;
    private OfferStatus suggestionOfferStatusOnOfferSubmitted = null;


    public volatile int suggestionsDelayedUntil = 0;

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
        suggestionItemIdOnOfferSubmitted = -1;
        suggestionOfferStatusOnOfferSubmitted = null;
    }

    public boolean suggestionOutOfDate() {
        Instant tenSecondsAgo = Instant.now().minusSeconds(10L);
        if (suggestionReceivedAt == null || tenSecondsAgo.isAfter(suggestionReceivedAt)) {
            return lastFailureAt == null || tenSecondsAgo.isAfter(lastFailureAt);
        }
        return false;
    }
}
