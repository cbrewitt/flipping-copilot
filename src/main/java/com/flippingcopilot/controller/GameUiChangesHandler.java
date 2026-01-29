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
import net.runelite.api.widgets.*;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import javax.inject.Singleton;

import static net.runelite.api.VarPlayer.CURRENT_GE_ITEM;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class GameUiChangesHandler {
    private static final int GE_HISTORY_TAB_WIDGET_ID = 149;

    // dependencies
    private final ClientThread clientThread;
    private final Client client;
    private final GePreviousSearch gePreviousSearch;
    private final HighlightController highlightController;
    private final SuggestionManager suggestionManager;
    private final GrandExchange grandExchange;
    private final OfferManager offerManager;
    private final OfferHandler offerHandler;
    // state
    boolean quantityOrPriceChatboxOpen;
    boolean itemSearchChatboxOpen = false;
    @Getter
    OfferEditor flippingWidget = null;

    public void onVarClientIntChanged(VarClientIntChanged event) {
        if (event.getIndex() == VarClientInt.INPUT_TYPE
                && client.getVarcIntValue(VarClientInt.INPUT_TYPE) == 14
                && client.getWidget(ComponentID.CHATBOX_GE_SEARCH_RESULTS) != null) {
            itemSearchChatboxOpen = true;
            clientThread.invokeLater(gePreviousSearch::showSuggestedItemInSearch);
        }

        if (quantityOrPriceChatboxOpen
                && event.getIndex() == VarClientInt.INPUT_TYPE
                && client.getVarcIntValue(VarClientInt.INPUT_TYPE) == 0
        ) {
            quantityOrPriceChatboxOpen = false;
            return;
        }

        if (itemSearchChatboxOpen
                && event.getIndex() == VarClientInt.INPUT_TYPE
                && client.getVarcIntValue(VarClientInt.INPUT_TYPE) == 0
        ) {
            clientThread.invokeLater(highlightController::redraw);
            itemSearchChatboxOpen = false;
            return;
        }

        //Check that it was the chat input that got enabled.
        if (event.getIndex() != VarClientInt.INPUT_TYPE
                || client.getWidget(ComponentID.CHATBOX_TITLE) == null
                || client.getVarcIntValue(VarClientInt.INPUT_TYPE) != 7
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
        if (event.getIndex() == VarClientStr.INPUT_TEXT && itemSearchChatboxOpen) {
            clientThread.invokeLater(highlightController::redraw);
        }
    }

    public void onWidgetLoaded(WidgetLoaded event) {
        if (event.getGroupId() == InterfaceID.GRAND_EXCHANGE) {
            suggestionManager.setSuggestionNeeded(true);
        }
        if (event.getGroupId() == 383
                || event.getGroupId() == InterfaceID.GRAND_EXCHANGE
                || event.getGroupId() == 213
                || event.getGroupId() == GE_HISTORY_TAB_WIDGET_ID) {
            clientThread.invokeLater(highlightController::redraw);
        }
    }

    public void onWidgetClosed(WidgetClosed event) {
        if (event.getGroupId() == InterfaceID.GRAND_EXCHANGE) {
            clientThread.invokeLater(highlightController::removeAll);
            suggestionManager.setSuggestionNeeded(true);
        }
    }

    public void onVarbitChanged(VarbitChanged event) {
        if (event.getVarpId() == 375
                || event.getVarpId() == CURRENT_GE_ITEM
                || event.getVarbitId() == 4396
                || event.getVarbitId() == 4398
                || event.getVarbitId() == 4439) {
            clientThread.invokeLater(highlightController::redraw);
        }

        if (event.getVarpId() == CURRENT_GE_ITEM) {
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
                suggestion.actioned = true;
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
}
