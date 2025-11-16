package com.flippingcopilot.controller;

import com.flippingcopilot.model.*;
import com.flippingcopilot.ui.WidgetHighlightOverlay;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.VarClientStr;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

import static net.runelite.api.VarPlayer.CURRENT_GE_ITEM;
import static net.runelite.api.Varbits.GE_OFFER_CREATION_TYPE;


@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class HighlightController {

    // dependencies
    private final FlippingCopilotConfig config;
    private final SuggestionManager suggestionManager;
    private final GrandExchange grandExchange;
    private final AccountStatusManager accountStatusManager;
    private final Client client;
    private final OfferManager offerManager;
    private final OverlayManager overlayManager;
    private final HighlightColorController highlightColorController;
    private final SlotProfitColorizer slotProfitColorizer;

    // state
    private final ArrayList<WidgetHighlightOverlay> highlightOverlays = new ArrayList<>();

    public void redraw() {
        removeAll();
        if(!config.suggestionHighlights()) {
            return;
        }
        if(!grandExchange.isOpen()) {
            return;
        }
        if (offerManager.isOfferJustPlaced()) {
            return;
        }
        if(suggestionManager.getSuggestionError() != null) {
            return;
        }
        Suggestion suggestion = suggestionManager.getSuggestion();
        if (suggestion == null) {
            return;
        }
        if (grandExchange.isHomeScreenOpen()) {
            drawHomeScreenHighLights(suggestion);
        } else if (grandExchange.isSlotOpen()) {
            drawOfferScreenHighlights(suggestion);
        }
    }

    private void drawHomeScreenHighLights(Suggestion suggestion) {
        AccountStatus accountStatus = accountStatusManager.getAccountStatus();
        if (accountStatus.isCollectNeeded(suggestion)) {
            Widget collectButton = grandExchange.getCollectButton();
            if (collectButton != null) {
                add(collectButton, highlightColorController.getBlueColor(), new Rectangle(2, 1, 81, 18));
            }
        }
        else if (suggestion.getType().equals("abort")) {
            Widget slotWidget = grandExchange.getSlotWidget(suggestion.getBoxId());
            add(slotWidget, highlightColorController.getRedColor());
        }
        else if (suggestion.getType().equals("buy")) {
            int slotId = accountStatus.findEmptySlot();
            if (slotId != -1) {
                Widget buyButton = grandExchange.getBuyButton(slotId);
                if (buyButton != null && !buyButton.isHidden()) {
                    add(buyButton, highlightColorController.getBlueColor(), new Rectangle(0, 0, 45, 44));
                }
            }
        }
        else if (suggestion.getType().equals("sell")) {
            Widget itemWidget = getInventoryItemWidget(suggestion.getItemId());
            if (itemWidget != null && !itemWidget.isHidden()) {
                add(itemWidget, highlightColorController.getBlueColor(), new Rectangle(0, 0, 34, 32));
            }
        }
    }

    private void drawOfferScreenHighlights(Suggestion suggestion) {
        Widget offerTypeWidget = grandExchange.getOfferTypeWidget();
        if (offerTypeWidget == null) {
            return;
        }

        int currentItemId = client.getVarpValue(CURRENT_GE_ITEM);

        String offerType = client.getVarbitValue(GE_OFFER_CREATION_TYPE) == 1 ? "sell" : "buy";
        boolean matchesSuggestionOfferType = offerType.equals(suggestion.getType());

        if (matchesSuggestionOfferType) {
            if (currentItemId == suggestion.getItemId()) {
                if (config.slotPriceColorEnabled()) {
                    slotProfitColorizer.colorOfferSetupPrice(suggestion.getItemId(), suggestion.getPrice());
                }

                if (offerDetailsCorrect(suggestion)) {
                    highlightConfirm();
                } else {
                    if (grandExchange.getOfferPrice() != suggestion.getPrice()) {
                        highlightPrice();
                    }
                    highlightQuantity(suggestion);
                }
            } else if (currentItemId == -1){
                highlightItemInSearch(suggestion);
            }
        }

        // Check if unsuggested item/offer type is selected
        if (currentItemId != -1
            && (currentItemId != suggestion.getItemId() || !matchesSuggestionOfferType)
            && currentItemId == offerManager.getViewedSlotItemId()
        ) {
            if (offerManager.getViewedSlotItemPrice() > 0) {
                if (config.slotPriceColorEnabled()) {
                    slotProfitColorizer.colorOfferSetupPrice(currentItemId, offerManager.getViewedSlotItemPrice());
                }

                if (grandExchange.getOfferPrice() == offerManager.getViewedSlotItemPrice()) {
                    highlightConfirm();
                } else {
                    highlightPrice();
                }
            } else if (config.slotPriceColorEnabled()) {
                slotProfitColorizer.resetOfferSetup();
            }
        }
    }

    private void highlightItemInSearch(Suggestion suggestion) {
        if (!client.getVarcStrValue(VarClientStr.INPUT_TEXT).isEmpty()) {
            return;
        }
        Widget searchResults = client.getWidget(ComponentID.CHATBOX_GE_SEARCH_RESULTS);
        if (searchResults == null) {
            return;
        }
        for (Widget widget : searchResults.getDynamicChildren()) {
            if (widget.getName().equals("<col=ff9040>" + suggestion.getName() + "</col>")) {
                add(widget, highlightColorController.getBlueColor());
                return;
            }
        }
        Widget itemWidget = searchResults.getChild(3);
        if (itemWidget != null && itemWidget.getItemId() == suggestion.getItemId()) {
            add(itemWidget, highlightColorController.getBlueColor());
        }
    }

    private boolean offerDetailsCorrect(Suggestion suggestion) {
        return grandExchange.getOfferPrice() == suggestion.getPrice()
                && grandExchange.getOfferQuantity() == suggestion.getQuantity();
    }

    private void highlightPrice() {
        Widget setPriceButton = grandExchange.getSetPriceButton();
        if (setPriceButton != null) {
            add(setPriceButton, highlightColorController.getBlueColor(), new Rectangle(1, 6, 33, 23));
        }
    }

    private void highlightQuantity(Suggestion suggestion) {
        AccountStatus accountStatus = accountStatusManager.getAccountStatus();
        if (grandExchange.getOfferQuantity() != suggestion.getQuantity()) {
            Widget setQuantityButton;
            if (accountStatus.getInventory().getTotalAmount(suggestion.getItemId()) == suggestion.getQuantity()) {
                setQuantityButton = grandExchange.getSetQuantityAllButton();
            } else {
                setQuantityButton = grandExchange.getSetQuantityButton();
            }
            if (setQuantityButton != null) {
                add(setQuantityButton, highlightColorController.getBlueColor(), new Rectangle(1, 6, 33, 23));
            }
        }
    }

    private void highlightConfirm() {
        Widget confirmButton = grandExchange.getConfirmButton();
        if (confirmButton != null) {
            add(confirmButton, highlightColorController.getBlueColor(), new Rectangle(1, 1, 150, 38));
        }
    }

    private void add(Widget widget, Color color, Rectangle adjustedBounds) {
        SwingUtilities.invokeLater(() -> {
            WidgetHighlightOverlay overlay = new WidgetHighlightOverlay(widget, color, adjustedBounds);
            highlightOverlays.add(overlay);
            overlayManager.add(overlay);
        });
    }

    private void add(Widget widget, Color color) {
        add(widget, color, new Rectangle(0, 0, widget.getWidth(), widget.getHeight()));
    }

    public void removeAll() {
        SwingUtilities.invokeLater(() -> {
            highlightOverlays.forEach(overlayManager::remove);
            highlightOverlays.clear();
        });
    }

    private Widget getInventoryItemWidget(int unnotedItemId) {
        // Inventory has a different widget if GE is open
        Widget inventory = client.getWidget(467, 0);
        if (inventory == null) {
            inventory = client.getWidget(149, 0);
            if (inventory == null) {
                return null;
            }
        }

        Widget notedWidget = null;
        Widget unnotedWidget = null;

        for (Widget widget : inventory.getDynamicChildren()) {
            int itemId = widget.getItemId();
            ItemComposition itemComposition = client.getItemDefinition(itemId);

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
