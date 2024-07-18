package com.flippingcopilot.controller;

import com.flippingcopilot.model.Suggestion;
import com.flippingcopilot.ui.OfferEditor;
import com.flippingcopilot.ui.WidgetHighlightOverlay;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.widgets.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.function.Supplier;

import static com.flippingcopilot.ui.UIUtilities.BLUE_HIGHLIGHT_COLOR;
import static com.flippingcopilot.ui.UIUtilities.RED_HIGHLIGHT_COLOR;
import static net.runelite.api.VarPlayer.CURRENT_GE_ITEM;
import static net.runelite.api.Varbits.GE_OFFER_CREATION_TYPE;

@Slf4j
public class GameUiChangesHandler {
    private static final int GE_HISTORY_TAB_WIDGET_ID = 149;
    private final FlippingCopilotPlugin plugin;
    boolean quantityOrPriceChatboxOpen;
    boolean offerJustPlaced = false;
    private ArrayList<WidgetHighlightOverlay> highlightOverlays = new ArrayList<>();
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

    public void onWidgetLoaded(WidgetLoaded event) {
        //ge history widget loaded
        //GE_HISTORY_TAB_WIDGET_ID does not load when history tab is opened from the banker right click. It only loads when
        //the "history" button is clicked for the ge interface. However, 383 loads in both situations.
        if (event.getGroupId() == 383) {
            log.debug("GE history tab loaded 1");
            redrawSuggestionHighlights();
        }

        //if either ge interface or bank pin interface is loaded, hide the ge history tab panel again
        if (event.getGroupId() == InterfaceID.GRAND_EXCHANGE || event.getGroupId() == 213) {
            log.debug("Grand Exchange widget loaded");
            redrawSuggestionHighlights();
        }

        //remove highlighted item
        //The player opens the trade history tab from the ge interface. Necessary since the back button isn't considered hidden here.
        //this (id 149 and not id 383) will also trigger when the player just exits out of the ge interface offer window screen, which is good
        //as then the highlight won't linger in that case.
        if (event.getGroupId() == GE_HISTORY_TAB_WIDGET_ID) {
            log.debug("GE history tab loaded 2");
            // this triggers when the ge is closed and also when history tab is opened
            // can using existing method in GE class to check if GE is closed
            redrawSuggestionHighlights();
        }

    }

    public void onWidgetClosed(WidgetClosed event) {
        if (event.getGroupId() == InterfaceID.GRAND_EXCHANGE) {
            removeHighlights();
        }
    }

    public void onVarbitChanged(VarbitChanged event) {
        //when a user clicks on a slot or leaves one, this event triggers
        if (event.getVarpId() == 375) {
            log.debug("user has clicked slot or left slot");
            redrawSuggestionHighlights();
        }

        // when the offer item is changed, this triggers
        if (event.getVarpId() == CURRENT_GE_ITEM) {
            redrawSuggestionHighlights();
        }

        // when the price or quantity is changed, this event triggers
        if(event.getVarbitId() == 4396 || event.getVarbitId() == 4398) {
            redrawSuggestionHighlights();
        }
    }


    void redrawSuggestionHighlights() {
        removeHighlights();
        if (offerJustPlaced) {
            return;
        }
        Suggestion suggestion = plugin.suggestionHandler.getCurrentSuggestion();
        if (suggestion == null) {
            return;
        }
        if (plugin.grandExchange.isHomeScreenOpen()) {
            drawHomeScreenHighLights(suggestion);
        } else if (plugin.grandExchange.isSlotOpen()) {
            drawOfferScreenHighlights(suggestion);
        }
    }

    private void drawHomeScreenHighLights(Suggestion suggestion) {
        if (plugin.suggestionHandler.isCollectNeeded()) {
            Widget collectButton = plugin.grandExchange.getCollectButton();
            if (collectButton != null) {
                addHighlight(collectButton, BLUE_HIGHLIGHT_COLOR);
            }
        }
        else if (suggestion.getType().equals("abort")) {
            Widget slotWidget = plugin.grandExchange.getSlotWidget(suggestion.getBoxId());
            addHighlight(slotWidget, RED_HIGHLIGHT_COLOR);
        }
        else if (suggestion.getType().equals("buy")) {
            int slotId = plugin.accountStatus.getOffers().findEmptySlot();
            if (slotId != -1) {
                Widget buyButton = plugin.grandExchange.getBuyButton(slotId);
                if (buyButton != null && !buyButton.isHidden()) {
                    addHighlight(buyButton, BLUE_HIGHLIGHT_COLOR);
                }
            }
        }
        else if (suggestion.getType().equals("sell")) {
            Widget itemWidget = getInventoryItemWidget(suggestion.getItemId());
            if (itemWidget != null && !itemWidget.isHidden()) {
                addHighlight(itemWidget, BLUE_HIGHLIGHT_COLOR);
            }
        }
    }

    private void drawOfferScreenHighlights(Suggestion suggestion) {
        if (plugin.client.getVarpValue(CURRENT_GE_ITEM) == suggestion.getItemId()) {
            Widget offerTypeWidget = plugin.grandExchange.getOfferTypeWidget();
            String offerType = plugin.client.getVarbitValue(GE_OFFER_CREATION_TYPE) == 1 ? "sell" : "buy";
            if (offerTypeWidget != null && offerType.equals(suggestion.getType())) {
                // check if price and quantity are correct
                if (plugin.grandExchange.getOfferPrice() == suggestion.getPrice() && plugin.grandExchange.getOfferQuantity() == suggestion.getQuantity()) {
                    Widget confirmButton = plugin.grandExchange.getConfirmButton();
                    if (confirmButton != null) {
                        addHighlight(confirmButton, BLUE_HIGHLIGHT_COLOR);
                    }
                } else {
                    if (plugin.grandExchange.getOfferPrice() != suggestion.getPrice()) {
                        Widget setPriceButton = plugin.grandExchange.getSetPriceButton();
                        if (setPriceButton != null) {
                            addHighlight(setPriceButton, BLUE_HIGHLIGHT_COLOR);
                        }
                    }
                    if (plugin.grandExchange.getOfferQuantity() != suggestion.getQuantity()) {
                        Widget setQuantityButton = plugin.grandExchange.getSetQuantityButton();
                        if (setQuantityButton != null) {
                            addHighlight(setQuantityButton, BLUE_HIGHLIGHT_COLOR);
                        }
                    }
                }
            }
        }
    }

    private void addHighlight(Widget widget, Color color) {
        WidgetHighlightOverlay overlay = new WidgetHighlightOverlay(widget, color);
        highlightOverlays.add(overlay);
        plugin.overlayManager.add(overlay);
    }

    void removeHighlights() {
        highlightOverlays.forEach(plugin.overlayManager::remove);
        highlightOverlays.clear();
    }

    private Widget getInventoryItemWidget(int unnotedItemId) {
        // Inventory has a different widget if GE is open
        Widget inventory = plugin.getClient().getWidget(467, 0);
        if (inventory == null) {
            inventory = plugin.getClient().getWidget(149, 0);
            if (inventory == null) {
                return null;
            }
        }
        for (Widget widget : inventory.getDynamicChildren()) {
            int itemId = widget.getItemId();
            ItemComposition itemComposition = plugin.client.getItemDefinition(itemId);
            if (itemComposition.getNote() != -1) {
                itemId = itemComposition.getLinkedNoteId();
            }
            if (itemId == unnotedItemId) {
                return widget;
            }
        }

        return null;
    }

    public void handleMenuOptionClicked(MenuOptionClicked event) {
        if (event.getMenuOption().equals("Confirm") && plugin.grandExchange.isSlotOpen()) {
            offerJustPlaced = true;
        }
    }
}
