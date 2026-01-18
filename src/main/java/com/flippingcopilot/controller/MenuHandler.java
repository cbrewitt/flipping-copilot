package com.flippingcopilot.controller;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.model.OfferManager;
import com.flippingcopilot.model.Suggestion;
import com.flippingcopilot.model.SuggestionManager;
import com.flippingcopilot.ui.flipsdialog.FlipsDialogController;
import com.flippingcopilot.ui.graph.model.PriceLine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;
import javax.inject.Singleton;

import static net.runelite.api.VarPlayer.CURRENT_GE_ITEM;
import static net.runelite.api.Varbits.GE_OFFER_CREATION_TYPE;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class MenuHandler {

    private final FlippingCopilotConfig config;
    private final Client client;
    private final OfferManager offerManager;
    private final GrandExchange grandExchange;
    private final SuggestionManager suggestionManager;
    private final FlipsDialogController flipsDialogController;


    public void injectCopilotPriceGraphMenuEntry(MenuEntryAdded event) {
        if(!config.priceGraphMenuOptionEnabled()) {
            return;
        }
        if (event.getOption().equals("View offer")) {
            long slotWidgetId = event.getActionParam1();
            client.getMenu()
                    .createMenuEntry(-1)
                    .setOption("Copilot price graph")
                    .onClick((MenuEntry e) -> {
                        GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
                        for (int i = 0; i < offers.length; i++) {
                            Widget slotWidget = client.getWidget(465, 7 + i);
                            if (slotWidget != null && slotWidget.getId() == slotWidgetId) {
                                int itemId = offers[i].getItemId();
                                PriceLine priceLine = buildPriceLine(offers[i]);
                                flipsDialogController.showPriceGraphTab(itemId, false, priceLine);
                                log.debug("matched widget to slot {}, item {}", i, offers[i].getItemId());
                            }
                        }
                    });
        }
    }

    private PriceLine buildPriceLine(GrandExchangeOffer offer) {
        switch (offer.getState()) {
            case BOUGHT:
            case BUYING:
                return new PriceLine(
                        offer.getPrice(),
                        "offer buy price",
                        false
                );
            case SOLD :
            case SELLING:
                return new PriceLine(
                        offer.getPrice(),
                        "offer sell price",
                        true
                );
        }
        return null;
    }

    public void injectConfirmMenuEntry(MenuEntryAdded event) {
        if(!config.disableLeftClickConfirm()) {
            return;
        }

        if(offerDetailsCorrect()) {
            return;
        }

        if(event.getOption().equals("Confirm") && grandExchange.isSlotOpen()) {
            log.debug("Adding deprioritized menu entry for offer");
            client.getMenu()
                    .createMenuEntry(-1)
                    .setOption("Nothing");

            event.getMenuEntry().setDeprioritized(true);
        }
    }

    private boolean offerDetailsCorrect() {
        Suggestion suggestion = suggestionManager.getSuggestion();
        if (suggestion == null) {
            return false;
        }
        String offerType = client.getVarbitValue(GE_OFFER_CREATION_TYPE) == 1 ? "sell" : "buy";
        if (client.getVarpValue(CURRENT_GE_ITEM) == suggestion.getItemId() && offerType.equals(suggestion.getType())) {
            return grandExchange.getOfferPrice() == suggestion.getPrice()
                    && grandExchange.getOfferQuantity() == suggestion.getQuantity();
        } else if (client.getVarpValue(CURRENT_GE_ITEM) == offerManager.getViewedSlotItemId()
                && offerManager.getViewedSlotItemPrice() > 0) {
            return grandExchange.getOfferPrice() == offerManager.getViewedSlotItemPrice();
        }
        return false;
    }
}


