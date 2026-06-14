package com.flippingcopilot.model;

import lombok.Getter;
import lombok.Setter;

import javax.inject.Singleton;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@Getter
@Setter
public class SuggestionManager {

    private volatile boolean suggestionNeeded;
    private volatile boolean suggestionRequestInProgress;
    private volatile boolean graphDataReadingInProgress;
    private volatile boolean suggestionRefreshPending;
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

    // Most recent expected duration (seconds) Copilot predicted for each item, captured from each
    // suggestion as it arrives. expectedDuration only lives on the live suggestion, so caching it by
    // item lets the GE offer tooltip show an expected time for any item that has been suggested.
    private final Map<String, Double> itemNameToExpectedDuration = new ConcurrentHashMap<>();


    public volatile int suggestionsDelayedUntil = 0;

    public void setSuggestion(Suggestion suggestion) {
        this.suggestion = suggestion;
        suggestionReceivedAt = Instant.now();
        if (suggestion != null && suggestion.getExpectedDuration() != null && suggestion.getName() != null) {
            itemNameToExpectedDuration.put(suggestion.getName(), suggestion.getExpectedDuration());
        }
    }

    public Double getExpectedDurationForItem(String itemName) {
        return itemNameToExpectedDuration.get(itemName);
    }

    public void setSuggestionError(HttpResponseException error) {
        this.suggestionError = error;
        lastFailureAt= Instant.now();
    }

    public void reset() {
        suggestionNeeded = false;
        suggestionRefreshPending = false;
        suggestion = null;
        suggestionReceivedAt = null;
        lastFailureAt = null;
        lastOfferSubmittedTick = -1;
        suggestionError = null;
        suggestionItemIdOnOfferSubmitted = -1;
        suggestionOfferStatusOnOfferSubmitted = null;
        itemNameToExpectedDuration.clear();
    }

    public boolean suggestionOutOfDate() {
        Instant tenSecondsAgo = Instant.now().minusSeconds(10L);
        if (suggestionReceivedAt == null || tenSecondsAgo.isAfter(suggestionReceivedAt)) {
            return lastFailureAt == null || tenSecondsAgo.isAfter(lastFailureAt);
        }
        return false;
    }

    public boolean suggestionVeryOutOfDate() {
        return suggestionReceivedAt != null && Instant.now().minusSeconds(60L).isAfter(suggestionReceivedAt);
    }
}
