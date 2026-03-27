package com.flippingcopilot.controller;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.LinkedHashSet;
import java.util.Set;

@Slf4j
@Singleton
public class PortfolioController {
    // dependencies
    private final Client client;
    private final ItemController itemController;

    @Inject
    public PortfolioController(Client client,
                               ItemController itemController) {
        this.client = client;
        this.itemController = itemController;
    }

    public Set<Integer> getActiveGrandExchangeItemIdsForSync() {
        Set<Integer> itemIds = new LinkedHashSet<>();
        GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
        if (offers == null) {
            return itemIds;
        }
        for (GrandExchangeOffer offer : offers) {
            if (offer == null) {
                continue;
            }
            GrandExchangeOfferState state = offer.getState();
            if (state != GrandExchangeOfferState.EMPTY) {
                int itemId = offer.getItemId();
                if (itemId > 0) {
                    itemIds.add(itemController.toUnnotedItemId(itemId));
                }
            }
        }
        return itemIds;
    }

}
