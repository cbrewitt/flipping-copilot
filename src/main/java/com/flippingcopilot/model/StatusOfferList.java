package com.flippingcopilot.model;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.runelite.api.GrandExchangeOffer;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


public class StatusOfferList extends ArrayList<Offer> {
    public static final int NUM_SLOTS = 8;
    public static final int NUM_F2P_SLOTS = 3;

    public StatusOfferList() {
        super(NUM_SLOTS);
        for (int i = 0; i < NUM_SLOTS; i++) {
            add(Offer.getEmptyOffer(i));
        }
    }

    public static StatusOfferList fromRunelite(GrandExchangeOffer[] runeliteOffers) {
        StatusOfferList offers = new StatusOfferList();
        for (int i = 0; i < runeliteOffers.length; i++) {
            offers.set(i, Offer.fromRunelite(runeliteOffers[i], i));
        }
        return offers;
    }

    public boolean isEmptySlotNeeded(Suggestion suggestion, boolean isMember) {
        return (suggestion.getType().equals("buy") || suggestion.getType().equals("sell"))
                && !emptySlotExists(isMember);
    }

    boolean emptySlotExists(boolean isMember) {
        return findEmptySlot(isMember) != -1;
    }

    boolean reservedSlotNeeded(boolean isMember, int reservedSlots) {
        int numUsableSlots = isMember ? NUM_SLOTS : NUM_F2P_SLOTS;
        int numEmptySlots = 0;
        for (int i = 0; i < numUsableSlots; i++) {
            if (get(i).getStatus() == OfferStatus.EMPTY)    {
                numEmptySlots++;
            }
        }
        return reservedSlots > numEmptySlots && completeOfferExists();
    }

    boolean completeOfferExists() {
        return stream().anyMatch(offer -> offer.getStatus() != OfferStatus.EMPTY && !offer.isActive());
    }

    public long getGpOnMarket() {
        return stream().mapToLong(Offer::cashStackGpValue).sum();
    }

    public long getTotalGpToCollect() {
        return stream().mapToLong(Offer::getGpToCollect).sum();
    }

    JsonArray toJson(Gson gson) {
        List<JsonObject> list = stream()
                .map(offer -> offer.toJson(gson))
                .collect(Collectors.toList());
        JsonArray jsonArray = new JsonArray();
        list.forEach(jsonArray::add);
        return jsonArray;
    }

    public int findEmptySlot(boolean isMember) {
        int numUsableSlots = isMember ? NUM_SLOTS : NUM_F2P_SLOTS;
        for (int i = 0; i < numUsableSlots; i++) {
            if (get(i).getStatus() == OfferStatus.EMPTY) {
                return i;
            }
        }
        return -1;
    }
}
