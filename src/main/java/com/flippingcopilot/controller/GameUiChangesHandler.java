package com.flippingcopilot.controller;

import com.flippingcopilot.model.Suggestion;
import com.flippingcopilot.ui.OfferEditor;
import net.runelite.api.*;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.widgets.*;


public class GameUiChangesHandler {
    FlippingCopilotPlugin plugin;
    boolean quantityOrPriceChatboxOpen;
    GameUiChangesHandler(FlippingCopilotPlugin plugin) {
        this.plugin = plugin;
    }

    public void onVarClientIntChanged(VarClientIntChanged event) {
        Client client = plugin.getClient();

        if (event.getIndex() == VarClientInt.INPUT_TYPE
                && client.getVarcIntValue(VarClientInt.INPUT_TYPE) == 14
                && client.getWidget(ComponentID.CHATBOX_GE_SEARCH_RESULTS) != null) {
            plugin.getClientThread().invokeLater(this::showSuggestedItemInSearch);
        }

        if (quantityOrPriceChatboxOpen
                && event.getIndex() == VarClientInt.INPUT_TYPE
                && client.getVarcIntValue(VarClientInt.INPUT_TYPE) == 0
        ) {
            quantityOrPriceChatboxOpen = false;

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

        plugin.getClientThread().invokeLater(() ->
        {
            OfferEditor flippingWidget = new OfferEditor(client.getWidget(ComponentID.CHATBOX_CONTAINER), client);
            Suggestion suggestion = plugin.suggestionHandler.getCurrentSuggestion();
            flippingWidget.showSuggestion(suggestion);
        });
    }

    private void showSuggestedItemInSearch() {
        Suggestion suggestion = plugin.suggestionHandler.getCurrentSuggestion();
        Client client = plugin.getClient();
        Widget searchResults = client.getWidget(ComponentID.CHATBOX_GE_SEARCH_RESULTS);
        Widget previousSearch = searchResults.getChild(0);

        if (suggestion.getType().equals("buy") && previousSearch != null) {
            previousSearch.setOnOpListener(754, suggestion.getItemId(), 84);
            previousSearch.setOnKeyListener(754, suggestion.getItemId(), -2147483640);
            previousSearch.setName("<col=ff9040>" + suggestion.getName() + "</col>");

            Widget previousSearchText = searchResults.getChild(1);
            if(previousSearchText == null) {
                return;
            }
            previousSearchText.setText("Copilot item:");

            Widget itemName = searchResults.getChild(2);
            itemName.setText(suggestion.getName());

            Widget item = searchResults.getChild(3);
            item.setItemId(suggestion.getItemId());
        }
    }
}
