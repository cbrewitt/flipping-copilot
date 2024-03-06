package com.flippingcopilot.model;

import com.google.gson.JsonArray;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.events.GrandExchangeOfferChanged;
import static net.runelite.api.ItemID.COINS_995;

import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;


public class OfferList extends ArrayList<Offer> {
    public static final int NUM_SLOTS = 8;

    public OfferList() {
        super(NUM_SLOTS);
        for (int i = 0; i < NUM_SLOTS; i++) {
            add(Offer.getEmptyOffer(i));
        }
    }

    public Transaction update(GrandExchangeOfferChanged event) {
        Offer oldOffer = get(event.getSlot());
        Offer newOffer = oldOffer.getUpdatedOffer(event);
        set(event.getSlot(), newOffer);
        return newOffer.getTransaction(oldOffer);
    }

    public static OfferList fromRunelite(GrandExchangeOffer[] runeliteOffers) {
        OfferList offers = new OfferList();
        for (int i = 0; i < runeliteOffers.length; i++) {
            offers.set(i, Offer.fromRunelite(runeliteOffers[i], i));
        }
        return offers;
    }

    public boolean areAllEmpty() {
        return stream().allMatch(offer -> offer.getStatus().equals(OfferStatus.EMPTY));
    }

    public boolean missingUncollectedItems() {
        return stream().anyMatch(Offer::missingUncollectedItems);
    }

    public boolean isEmptySlotNeeded(Suggestion suggestion) {
        return (suggestion.getType().equals("buy") || suggestion.getType().equals("sell"))
                && !emptySlotExists();
    }

    private boolean emptySlotExists() {
        return stream().anyMatch(offer -> offer.getStatus() == OfferStatus.EMPTY);
    }

    void removeCollectables() {
        forEach(Offer::removeCollectables);
    }

    Map<Integer, Long> getUncollectedItemAmounts() {
        Map<Integer, Long> itemsAmount = getUncollectedTradeablesAmounts();
        long totalGpToCollect = getTotalGpToCollect();
        itemsAmount.merge(COINS_995, totalGpToCollect, Long::sum);
        itemsAmount.entrySet().removeIf(entry -> entry.getValue() == 0);
        return itemsAmount;
    }

    public long getTotalGpToCollect() {
        return stream().mapToLong(Offer::getGpToCollect).sum();
    }

    private Map<Integer, Long> getUncollectedTradeablesAmounts() {
        return stream().collect(Collectors.groupingBy(Offer::getItemId,
                        Collectors.summingLong(Offer::getItemsToCollect)));
    }

    JsonArray toJson() {
        return stream().map(Offer::toJson).collect(JsonArray::new, JsonArray::add, JsonArray::addAll);
    }
}
