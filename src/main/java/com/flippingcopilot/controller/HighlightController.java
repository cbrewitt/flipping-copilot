package com.flippingcopilot.controller;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.model.*;
import com.flippingcopilot.ui.WidgetHighlightOverlay;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.VarClientStr;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Supplier;



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
    private final ClientThread clientThread;

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
        boolean isDumpSuggestion = suggestion.isDumpSuggestion();
        Supplier<Color> blueHighlight = () -> highlightColorController.getBlueColor(isDumpSuggestion);
        Supplier<Color> redHighlight = () -> highlightColorController.getRedColor(isDumpSuggestion);
        AccountStatus accountStatus = accountStatusManager.getAccountStatus();
        if (accountStatus.isCollectNeeded(suggestion)) {
            Widget collectButton = grandExchange.getCollectButton();
            if (collectButton != null) {
                add(collectButton, blueHighlight, new Rectangle(2, 1, 81, 18));
            }
        }
        else if (suggestion.getType().equals("abort")) {
            Widget slotWidget = grandExchange.getSlotWidget(suggestion.getBoxId());
            add(slotWidget, redHighlight);
        }
        else if (suggestion.getType().equals("buy")) {
            int slotId = accountStatus.findEmptySlot();
            if (slotId != -1) {
                Widget buyButton = grandExchange.getBuyButton(slotId);
                if (buyButton != null && !buyButton.isHidden()) {
                    add(buyButton, blueHighlight, new Rectangle(0, 0, 45, 44));
                }
            }
        }
        else if (suggestion.getType().equals("sell")) {
            Widget itemWidget = getInventoryItemWidget(suggestion.getItemId());
            if (itemWidget != null && !itemWidget.isHidden()) {
                add(itemWidget, blueHighlight, new Rectangle(0, 0, 34, 32));
            }
        }
    }

    private void drawOfferScreenHighlights(Suggestion suggestion) {
        boolean isDumpSuggestion = suggestion.isDumpSuggestion();
        Supplier<Color> blueHighlight = () -> highlightColorController.getBlueColor(isDumpSuggestion);
        GEOfferScreenSetupOfferState s = grandExchange.getOfferScreenSetupOfferState();
        if (s == null) {
            return;
        }

        if (s.offerDetailsCorrect(suggestion)) {
            highlightConfirm(blueHighlight);
            return;
        }

        boolean offerTypeMatches = Objects.equals(s.offerType, suggestion.getType());
        boolean itemMatches = s.currentItemId == suggestion.getItemId();

        // Prioritise certain dump alert cases
        if(suggestion.isDumpAlert) {
            if (!offerTypeMatches) {
                highlightBackButton(blueHighlight);
            } else if (!s.searchOpen && s.currentItemId != -1 && !itemMatches) {
                highlightItemSearchButton(blueHighlight);
            } else if (itemMatches) {
                if (s.offerPrice != suggestion.getPrice()) {
                    highlightPrice(blueHighlight);
                }
                highlightQuantity(suggestion, s.offerQuantity, blueHighlight);
            } else if (s.searchOpen) {
                highlightItemInSearch(suggestion, blueHighlight);
            }
            return;
        }

        // Custom item choice case
        if (isCustomChoiceItem(s, offerTypeMatches, itemMatches)) {
            if (s.offerPrice == offerManager.getViewedSlotItemPrice()) {
                highlightConfirm(blueHighlight);
            } else {
                highlightPrice(blueHighlight);
            }
            return;
        }

        // Standard suggestion case
        if (offerTypeMatches && itemMatches) {
            if (s.offerPrice != suggestion.getPrice()) {
                highlightPrice(blueHighlight);
            }
            highlightQuantity(suggestion, s.offerQuantity, blueHighlight);
            return;
        }
        if(offerTypeMatches && s.currentItemId == -1 && s.searchOpen) {
            highlightItemInSearch(suggestion,blueHighlight);
            return;
        }

        if(suggestion.getType().equals("abort") || suggestion.getType().equals("sell") && s.isEmptyBuyState()) {
            highlightBackButton(blueHighlight);
        }

    }

    private boolean isCustomChoiceItem(GEOfferScreenSetupOfferState s, boolean offerTypeMatches, boolean itemMatches) {
        return s.currentItemId != -1 && ((!offerTypeMatches && s.offerType.equals("sell"))|| (!s.searchOpen && !itemMatches))
                && s.currentItemId == offerManager.getViewedSlotItemId()
                && offerManager.getViewedSlotItemPrice() > -1;
    }

    private int getOfferItemId() {
        Widget detailsContainer = client.getWidget(465, 15);
        if (detailsContainer == null) {
            return -1;
        }
        Widget itemWidget = detailsContainer.getChild(7);
        if (itemWidget == null) {
            return -1;
        }
        return itemWidget.getItemId();
    }

    private void highlightItemInSearch(Suggestion suggestion, Supplier<Color> colorSupplier) {
        if (!client.getVarcStrValue(VarClientStr.INPUT_TEXT).isEmpty()) {
            return;
        }
        Widget searchResults = client.getWidget(ComponentID.CHATBOX_GE_SEARCH_RESULTS);
        if (searchResults == null) {
            return;
        }
        for (Widget widget : searchResults.getDynamicChildren()) {
            if (widget.getName().equals("<col=ff9040>" + suggestion.getName() + "</col>")) {
                add(widget, colorSupplier);
                return;
            }
        }
        Widget itemWidget = searchResults.getChild(3);
        if (itemWidget != null && itemWidget.getItemId() == suggestion.getItemId()) {
            add(itemWidget, colorSupplier);
        }
    }


    private void highlightPrice(Supplier<Color> colorSupplier) {
        Widget setPriceButton = grandExchange.getSetPriceButton();
        if (setPriceButton != null) {
            add(setPriceButton, colorSupplier, new Rectangle(1, 6, 33, 23));
        }
    }

    private void highlightQuantity(Suggestion suggestion, int offerQuantity, Supplier<Color> colorSupplier) {
        AccountStatus accountStatus = accountStatusManager.getAccountStatus();
        if (offerQuantity != suggestion.getQuantity()) {
            Widget setQuantityButton;
            if (accountStatus.getInventory().getTotalAmount(suggestion.getItemId()) == suggestion.getQuantity()) {
                setQuantityButton = grandExchange.getSetQuantityAllButton();
            } else {
                setQuantityButton = grandExchange.getSetQuantityButton();
            }
            if (setQuantityButton != null) {
                add(setQuantityButton, colorSupplier, new Rectangle(1, 6, 33, 23));
            }
        }
    }

    private void highlightConfirm(Supplier<Color> colorSupplier) {
        Widget confirmButton = grandExchange.getConfirmButton();
        if (confirmButton != null) {
            add(confirmButton, colorSupplier, new Rectangle(1, 1, 150, 38));
        }
    }

    private Widget getItemSearchButtonWidget() {
        Widget setupContainer = client.getWidget(465, 26);
        if (setupContainer == null) {
            return null;
        }
        Widget[] children = setupContainer.getChildren();
        if (children != null && children.length > 0) {
            return children[0];
        }
        return null;
    }

    private void highlightItemSearchButton(Supplier<Color> colorSupplier) {
        Widget searchWidget = getItemSearchButtonWidget();
        if (searchWidget != null) {
            add(searchWidget, colorSupplier);
        }
    }

    private Widget getAbortButtonWidget() {
        Widget statusContainer = client.getWidget(465, 23);
        if (statusContainer == null) {
            return null;
        }
        Widget[] children = statusContainer.getChildren();
        if (children != null && children.length > 0) {
            return children[0];
        }
        return null;
    }

    private void highlightAbortButton(Supplier<Color> colorSupplier) {
        Widget abortButton = getAbortButtonWidget();
        if (abortButton != null) {
            add(abortButton, colorSupplier);
        }
    }

    private void highlightBackButton(Supplier<Color> colorSupplier) {
        Widget backButton = grandExchange.getBackButton();
        if (backButton != null) {
            add(backButton, colorSupplier);
        }
    }

    private void add(Widget widget, Supplier<Color> colorSupplier, Rectangle adjustedBounds) {
        SwingUtilities.invokeLater(() -> {
            WidgetHighlightOverlay overlay = new WidgetHighlightOverlay(widget, colorSupplier, adjustedBounds);
            highlightOverlays.add(overlay);
            overlayManager.add(overlay);
        });
    }

    private void add(Widget widget, Supplier<Color> colorSupplier) {
        add(widget, colorSupplier, new Rectangle(0, 0, widget.getWidth(), widget.getHeight()));
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
