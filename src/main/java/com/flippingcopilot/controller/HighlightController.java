package com.flippingcopilot.controller;

import com.flippingcopilot.model.Suggestion;
import com.flippingcopilot.ui.WidgetHighlightOverlay;
import net.runelite.api.ItemComposition;
import net.runelite.api.VarClientStr;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;

import java.awt.*;
import java.util.ArrayList;

import static com.flippingcopilot.ui.UIUtilities.BLUE_HIGHLIGHT_COLOR;
import static com.flippingcopilot.ui.UIUtilities.RED_HIGHLIGHT_COLOR;
import static net.runelite.api.VarPlayer.CURRENT_GE_ITEM;
import static net.runelite.api.Varbits.GE_OFFER_CREATION_TYPE;

public class HighlightController {
    FlippingCopilotPlugin plugin;
    private final ArrayList<WidgetHighlightOverlay> highlightOverlays = new ArrayList<>();

    public HighlightController(FlippingCopilotPlugin plugin) {
        this.plugin = plugin;
    }

    public void redraw() {
        removeAll();
        if(!plugin.config.suggestionHighlights()) {
            return;
        }
        if (plugin.gameUiChangesHandler.isOfferJustPlaced()) {
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
                add(collectButton, BLUE_HIGHLIGHT_COLOR, new Rectangle(2, 1, 81, 18));
            }
        }
        else if (suggestion.getType().equals("abort")) {
            Widget slotWidget = plugin.grandExchange.getSlotWidget(suggestion.getBoxId());
            add(slotWidget, RED_HIGHLIGHT_COLOR);
        }
        else if (suggestion.getType().equals("buy")) {
            int slotId = plugin.accountStatus.getOffers().findEmptySlot();
            if (slotId != -1) {
                Widget buyButton = plugin.grandExchange.getBuyButton(slotId);
                if (buyButton != null && !buyButton.isHidden()) {
                    add(buyButton, BLUE_HIGHLIGHT_COLOR, new Rectangle(0, 0, 45, 44));
                }
            }
        }
        else if (suggestion.getType().equals("sell")) {
            Widget itemWidget = getInventoryItemWidget(suggestion.getItemId());
            if (itemWidget != null && !itemWidget.isHidden()) {
                add(itemWidget, BLUE_HIGHLIGHT_COLOR, new Rectangle(0, 0, 34, 32));
            }
        }
    }

    private void drawOfferScreenHighlights(Suggestion suggestion) {
        Widget offerTypeWidget = plugin.grandExchange.getOfferTypeWidget();
        String offerType = plugin.client.getVarbitValue(GE_OFFER_CREATION_TYPE) == 1 ? "sell" : "buy";
        if (offerTypeWidget != null) {
            if (offerType.equals(suggestion.getType())) {
                if (plugin.client.getVarpValue(CURRENT_GE_ITEM) == suggestion.getItemId()) {
                    if (offerDetailsCorrect(suggestion)) {
                        highlightConfirm();
                    } else {
                        if (plugin.grandExchange.getOfferPrice() != suggestion.getPrice()) {
                            highlightPrice();
                        }
                        highlightQuantity(suggestion);
                    }
                } else if (plugin.client.getVarpValue(CURRENT_GE_ITEM ) == -1){
                    highlightItemInSearch(suggestion);
                }
            }
            // Check if unsuggested item/offer type is selected
            if (plugin.client.getVarpValue(CURRENT_GE_ITEM) != -1
                    && (plugin.client.getVarpValue(CURRENT_GE_ITEM) != suggestion.getItemId()
                        || !offerType.equals(suggestion.getType()))
                    && plugin.client.getVarpValue(CURRENT_GE_ITEM) == plugin.offerHandler.getViewedSlotItemId()
                    && plugin.offerHandler.getViewedSlotItemPrice() > 0) {
                if (plugin.grandExchange.getOfferPrice() == plugin.offerHandler.getViewedSlotItemPrice()) {
                    highlightConfirm();
                } else {
                    highlightPrice();
                }
            }
        }
    }

    private void highlightItemInSearch(Suggestion suggestion) {
        if (!plugin.client.getVarcStrValue(VarClientStr.INPUT_TEXT).isEmpty()) {
            return;
        }
        Widget searchResults = plugin.client.getWidget(ComponentID.CHATBOX_GE_SEARCH_RESULTS);
        if (searchResults == null) {
            return;
        }
        for (Widget widget : searchResults.getDynamicChildren()) {
            if (widget.getName().equals("<col=ff9040>" + suggestion.getName() + "</col>")) {
                add(widget, BLUE_HIGHLIGHT_COLOR);
                return;
            }
        }
        Widget itemWidget = searchResults.getChild(3);
        if (itemWidget != null && itemWidget.getItemId() == suggestion.getItemId()) {
            add(itemWidget, BLUE_HIGHLIGHT_COLOR);
        }
    }

    private boolean offerDetailsCorrect(Suggestion suggestion) {
        return plugin.grandExchange.getOfferPrice() == suggestion.getPrice()
                && plugin.grandExchange.getOfferQuantity() == suggestion.getQuantity();
    }

    private void highlightPrice() {
        Widget setPriceButton = plugin.grandExchange.getSetPriceButton();
        if (setPriceButton != null) {
            add(setPriceButton, BLUE_HIGHLIGHT_COLOR, new Rectangle(1, 6, 33, 23));
        }
    }

    private void highlightQuantity(Suggestion suggestion) {
        if (plugin.grandExchange.getOfferQuantity() != suggestion.getQuantity()) {
            Widget setQuantityButton;
            if (plugin.accountStatus.getInventory().getTotalAmount(suggestion.getItemId()) == suggestion.getQuantity()) {
                setQuantityButton = plugin.grandExchange.getSetQuantityAllButton();
            } else {
                setQuantityButton = plugin.grandExchange.getSetQuantityButton();
            }
            if (setQuantityButton != null) {
                add(setQuantityButton, BLUE_HIGHLIGHT_COLOR, new Rectangle(1, 6, 33, 23));
            }
        }
    }

    private void highlightConfirm() {
        Widget confirmButton = plugin.grandExchange.getConfirmButton();
        if (confirmButton != null) {
            add(confirmButton, BLUE_HIGHLIGHT_COLOR, new Rectangle(1, 1, 150, 38));
        }
    }

    private void add(Widget widget, Color color, Rectangle adjustedBounds) {
        WidgetHighlightOverlay overlay = new WidgetHighlightOverlay(widget, color, adjustedBounds);
        highlightOverlays.add(overlay);
        plugin.overlayManager.add(overlay);
    }

    private void add(Widget widget, Color color) {
        add(widget, color, new Rectangle(0, 0, widget.getWidth(), widget.getHeight()));
    }

    public void removeAll() {
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

        Widget notedWidget = null;
        Widget unnotedWidget = null;

        for (Widget widget : inventory.getDynamicChildren()) {
            int itemId = widget.getItemId();
            ItemComposition itemComposition = plugin.client.getItemDefinition(itemId);

            if (itemComposition.getNote() != -1) {
                if (itemComposition.getLinkedNoteId() == unnotedItemId) {
                    notedWidget = widget;
                }
            } else if (itemId == unnotedItemId) {
                unnotedWidget = widget;
            }
        }
        return notedWidget != null ? notedWidget : unnotedWidget;
    }
}
