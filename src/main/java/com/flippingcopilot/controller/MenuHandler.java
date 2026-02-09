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
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;
import javax.inject.Singleton;

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
            String menuTarget = event.getTarget();
            client.getMenu()
                    .createMenuEntry(-1)
                    .setOption("Copilot graph")
                    .setTarget(menuTarget)
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
        } else if (shouldAddInventoryPriceGraphEntry(event)) {
            int inventorySlot = event.getActionParam0();
            int inventoryWidgetId = event.getActionParam1();
            Widget inventoryWidget = client.getWidget(inventoryWidgetId);
            if (inventoryWidget == null || inventorySlot < 0) {
                return;
            }
            Widget[] items = inventoryWidget.getDynamicChildren();
            if (items == null || inventorySlot >= items.length) {
                return;
            }
            Widget itemWidget = items[inventorySlot];
            if (itemWidget == null || itemWidget.getItemId() <= 0) {
                return;
            }
            int itemId = itemWidget.getItemId();
            if (!isGeTradableItem(itemId)) {
                return;
            }
            String menuTarget = resolveMenuTarget(event.getTarget(), itemId);
            int graphItemId = toUnnotedItemId(itemId);
            client.getMenu()
                    .createMenuEntry(-1)
                    .setOption("Copilot graph")
                    .setTarget(menuTarget)
                    .onClick((MenuEntry e) -> flipsDialogController.showPriceGraphTab(graphItemId, false, null));
        }
    }

    private boolean shouldAddInventoryPriceGraphEntry(MenuEntryAdded event) {
        if (!grandExchange.isOpen() || !event.getOption().equals("Examine")) {
            return false;
        }
        int widgetId = event.getActionParam1();
        Widget geInventoryWidget = client.getWidget(467, 0);
        if (geInventoryWidget != null && geInventoryWidget.getId() == widgetId) {
            return true;
        }
        Widget inventoryWidget = client.getWidget(149, 0);
        return inventoryWidget != null && inventoryWidget.getId() == widgetId;
    }

    private boolean isGeTradableItem(int itemId) {
        ItemComposition item = client.getItemDefinition(itemId);
        if (item == null) {
            return false;
        }
        if (item.isTradeable()) {
            return true;
        }
        if (item.getNote() != -1) {
            int unnotedItemId = item.getLinkedNoteId();
            if (unnotedItemId > 0) {
                ItemComposition unnoted = client.getItemDefinition(unnotedItemId);
                return unnoted != null && unnoted.isTradeable();
            }
        }
        return false;
    }

    private String resolveMenuTarget(String eventTarget, int itemId) {
        if (eventTarget != null && !eventTarget.isBlank()) {
            return eventTarget;
        }
        ItemComposition item = client.getItemDefinition(itemId);
        if (item == null || item.getName() == null) {
            return "";
        }
        return "<col=ff9040>" + item.getName() + "</col>";
    }

    private int toUnnotedItemId(int itemId) {
        ItemComposition item = client.getItemDefinition(itemId);
        if (item == null) {
            return itemId;
        }
        if (item.getNote() != -1 && item.getLinkedNoteId() > 0) {
            return item.getLinkedNoteId();
        }
        return itemId;
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

        if (!grandExchange.isOpen()) {
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
        String offerType = grandExchange.isOfferTypeSell() ? "sell" : "buy";
        if (client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH) == suggestion.getItemId() && offerType.equals(suggestion.getType())) {
            return grandExchange.getOfferPrice() == suggestion.getPrice()
                    && grandExchange.getOfferQuantity() == suggestion.getQuantity();
        } else if (client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH) == offerManager.getViewedSlotItemId()
                && offerManager.getViewedSlotItemPrice() > 0) {
            return grandExchange.getOfferPrice() == offerManager.getViewedSlotItemPrice();
        }
        return false;
    }
}
