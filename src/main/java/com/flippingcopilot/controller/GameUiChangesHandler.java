package com.flippingcopilot.controller;

import com.flippingcopilot.model.OfferManager;
import com.flippingcopilot.model.OfferStatus;
import com.flippingcopilot.model.Suggestion;
import com.flippingcopilot.model.SuggestionManager;
import com.flippingcopilot.ui.OfferEditor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.*;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import javax.inject.Singleton;


@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class GameUiChangesHandler {
    private static final int GE_HISTORY_TAB_WIDGET_ID = 149;
    private static final int SCRIPT_GE_COLLECT = 782;
    private static final int SCRIPT_GE_SLOT_REDRAW = 804;

    // dependencies
    private final ClientThread clientThread;
    private final Client client;
    private final GePreviousSearch gePreviousSearch;
    private final HighlightController highlightController;
    private final SuggestionManager suggestionManager;
    private final GrandExchange grandExchange;
    private final OfferManager offerManager;
    private final OfferHandler offerHandler;
    private final SlotProfitColorizer slotProfitColorizer;
    // state
    boolean quantityOrPriceChatboxOpen;
    boolean itemSearchChatboxOpen = false;
    @Getter
    OfferEditor flippingWidget = null;

    public void onVarClientIntChanged(VarClientIntChanged event) {
        if (event.getIndex() == VarClientID.MESLAYERMODE
                && client.getVarcIntValue(VarClientID.MESLAYERMODE) == 14
                && client.getWidget(ComponentID.CHATBOX_GE_SEARCH_RESULTS) != null) {
            itemSearchChatboxOpen = true;
            clientThread.invokeLater(gePreviousSearch::showSuggestedItemInSearch);
        }

        if (quantityOrPriceChatboxOpen
                && event.getIndex() == VarClientID.MESLAYERMODE
                && client.getVarcIntValue(VarClientID.MESLAYERMODE) == 0
        ) {
            quantityOrPriceChatboxOpen = false;
            return;
        }

        if (itemSearchChatboxOpen
                && event.getIndex() == VarClientID.MESLAYERMODE
                && client.getVarcIntValue(VarClientID.MESLAYERMODE) == 0
        ) {
            clientThread.invokeLater(highlightController::redraw);
            itemSearchChatboxOpen = false;
            return;
        }

        //Check that it was the chat input that got enabled.
        if (event.getIndex() != VarClientID.MESLAYERMODE
                || client.getWidget(ComponentID.CHATBOX_TITLE) == null
                || client.getVarcIntValue(VarClientID.MESLAYERMODE) != 7
                || client.getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER) == null) {
            return;
        }
        quantityOrPriceChatboxOpen = true;

        clientThread.invokeLater(() ->
        {
            flippingWidget = new OfferEditor(offerManager, client.getWidget(ComponentID.CHATBOX_CONTAINER), offerHandler, client);
            Suggestion suggestion = suggestionManager.getSuggestion();
            if (suggestion != null) {
                flippingWidget.showSuggestion(suggestion);
            }
        });
    }

    public void onVarClientStrChanged(VarClientStrChanged event) {
        if (event.getIndex() == VarClientID.MESLAYERINPUT && itemSearchChatboxOpen) {
            clientThread.invokeLater(highlightController::redraw);
        }
    }

    public void onWidgetLoaded(WidgetLoaded event) {
        if (event.getGroupId() == InterfaceID.GE_OFFERS) {
            suggestionManager.setSuggestionNeeded(true);
            clientThread.invokeLater(slotProfitColorizer::updateAllSlots);
        }
        if (event.getGroupId() == 383
                || event.getGroupId() == InterfaceID.GE_OFFERS
                || event.getGroupId() == 213
                || event.getGroupId() == GE_HISTORY_TAB_WIDGET_ID) {
            clientThread.invokeLater(highlightController::redraw);
        }
    }

    public void onWidgetClosed(WidgetClosed event) {
        if (event.getGroupId() == InterfaceID.GE_OFFERS) {
            clientThread.invokeLater(highlightController::removeAll);
            suggestionManager.setSuggestionNeeded(true);
        }
    }

    public void onVarbitChanged(VarbitChanged event) {
        if (event.getVarpId() == 375
                || event.getVarpId() == VarPlayerID.TRADINGPOST_SEARCH
                || event.getVarbitId() == VarbitID.GE_NEWOFFER_QUANTITY
                || event.getVarbitId() == VarbitID.GE_NEWOFFER_PRICE
                || event.getVarbitId() == VarbitID.GE_SELECTEDSLOT) {
            clientThread.invokeLater(highlightController::redraw);
        }

        if (event.getVarpId() == VarPlayerID.TRADINGPOST_SEARCH) {
            clientThread.invokeLater(() -> offerHandler.fetchSlotItemPrice(event.getValue() > -1, this::getFlippingWidget));
        }
    }

    public void handleMenuOptionClicked(MenuOptionClicked event) {
        if (event.getMenuOption().equals("Confirm") && grandExchange.isSlotOpen()) {
            log.debug("offer confirmed tick {}", client.getTickCount());
            offerManager.setOfferJustPlaced(true);
            suggestionManager.setLastOfferSubmittedTick(client.getTickCount());
            suggestionManager.setSuggestionNeeded(true);
            Suggestion suggestion = suggestionManager.getSuggestion();
            if(suggestion != null) {
                suggestion.actionedTick = client.getTickCount();
                suggestionManager.setSuggestionItemIdOnOfferSubmitted(suggestion.getItemId());
                suggestionManager.setSuggestionOfferStatusOnOfferSubmitted(suggestionOfferStatus(suggestion));
            } else {
                suggestionManager.setSuggestionItemIdOnOfferSubmitted(-1);
                suggestionManager.setSuggestionOfferStatusOnOfferSubmitted(null);
            }
        }
    }

    private OfferStatus suggestionOfferStatus(Suggestion suggestion) {
        if ("sell".equals(suggestion.getType())) {
            return OfferStatus.SELL;
        } else if ("buy".equals(suggestion.getType())) {
            return OfferStatus.BUY;
        } else {
            return null;
        }
    }

    public void onScriptPostFired(ScriptPostFired event) {
        if (event.getScriptId() == SCRIPT_GE_COLLECT || event.getScriptId() == SCRIPT_GE_SLOT_REDRAW) {
            clientThread.invokeLater(slotProfitColorizer::updateAllSlots);
        }
    }

    public void onBeforeRender(BeforeRender event) {
        if (grandExchange.isOpen()) {
            slotProfitColorizer.updateAllSlots();
        }
    }
}
