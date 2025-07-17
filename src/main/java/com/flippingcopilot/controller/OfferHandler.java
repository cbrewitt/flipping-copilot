package com.flippingcopilot.controller;

import com.flippingcopilot.manager.CopilotLoginManager;
import com.flippingcopilot.model.*;
import com.flippingcopilot.ui.OfferEditor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.VarClientStr;
import net.runelite.api.Varbits;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static net.runelite.api.VarPlayer.CURRENT_GE_ITEM;

@Slf4j
@Getter
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class OfferHandler {

    private static final int GE_OFFER_INIT_STATE_CHILD_ID = 20;

    // dependencies
    private final Client client;
    private final ClientThread clientThread;
    private final SuggestionManager suggestionManager;
    private final ApiRequestHandler apiRequestHandler;
    private final OsrsLoginManager osrsLoginManager;
    private final OfferManager offerManager;
    private final HighlightController highlightController;
    private final CopilotLoginManager copilotLoginManager;

    // state
    private String viewedSlotPriceErrorText = null;

    public void fetchSlotItemPrice(boolean isViewingSlot, Supplier<OfferEditor> offerEditorSupplier) {
        if (isViewingSlot) {
            var currentItemId = client.getVarpValue(CURRENT_GE_ITEM);
            offerManager.setViewedSlotItemId(currentItemId);
            if (currentItemId == -1 || currentItemId == 0) return;

            var suggestion = suggestionManager.getSuggestion();
            if (suggestion != null && suggestion.getItemId() == currentItemId &&
                    ((Objects.equals(suggestion.getType(), "sell") && isSelling()) ||
                            Objects.equals(suggestion.getType(), "buy") && isBuying())) {
                offerManager.setLastViewedSlotItemId(suggestion.getItemId());
                offerManager.setLastViewedSlotItemPrice(suggestion.getPrice());
                offerManager.setLastViewedSlotItemPrice((int) Instant.now().getEpochSecond());
                return;
            }

            if (!copilotLoginManager.isLoggedIn()) {
                viewedSlotPriceErrorText = "Login to copilot to see item price.";
                return;
            }
            viewedSlotPriceErrorText = "Loading copilot item price..";
            Consumer<ItemPrice> itemPriceConsumer = (fetchedPrice) -> {
                clientThread.invoke(() -> {
                    if (fetchedPrice == null) {
                        viewedSlotPriceErrorText = "Unknown error";
                        return;
                    }

                    if (fetchedPrice.getMessage() != null && !fetchedPrice.getMessage().isEmpty()) {
                        viewedSlotPriceErrorText = fetchedPrice.getMessage();
                    } else {
                        viewedSlotPriceErrorText = null;
                    }
                    offerManager.setViewedSlotItemPrice(isSelling() ? fetchedPrice.getSellPrice() : fetchedPrice.getBuyPrice());
                    offerManager.setLastViewedSlotItemId(offerManager.getViewedSlotItemId());
                    offerManager.setLastViewedSlotItemPrice(offerManager.getViewedSlotItemPrice());
                    offerManager.setLastViewedSlotPriceTime((int) Instant.now().getEpochSecond());

                    highlightController.redraw();
                    log.debug("fetched item {} price: {}", offerManager.getViewedSlotItemId(), offerManager.getViewedSlotItemPrice());

                    // todo: Usage of OfferEditor is messy. It mutates a widget so we need to get the original instance
                    //  of it which is created downstream on some other event handler path. This is why we use a supplier
                    //  but probably it should be an injected class of some kind. We should clean this up in the future
                    //  but for now just need it to work as currently broken.

                    OfferEditor flippingWidget = offerEditorSupplier.get();
                    if (flippingWidget != null) {
                        flippingWidget.showPrice(offerManager.getViewedSlotItemPrice());
                    }
                });
            };


            apiRequestHandler.asyncGetItemPriceWithGraphData(currentItemId, osrsLoginManager.getPlayerDisplayName(), itemPriceConsumer, false);

        } else {
            offerManager.setViewedSlotItemPrice(-1);
            offerManager.setViewedSlotItemId(-1);
            viewedSlotPriceErrorText = null;
        }
        highlightController.redraw();
    }

    public boolean isSettingQuantity() {
        var chatboxTitleWidget = getChatboxTitleWidget();
        if (chatboxTitleWidget == null) return false;
        String chatInputText = chatboxTitleWidget.getText();
        return chatInputText.equals("How many do you wish to buy?") || chatInputText.equals("How many do you wish to sell?");
    }

    public boolean isSettingPrice() {
        var chatboxTitleWidget = getChatboxTitleWidget();
        if (chatboxTitleWidget == null) return false;
        String chatInputText = chatboxTitleWidget.getText();

        var offerTextWidget = getOfferTextWidget();
        if (offerTextWidget == null) return false;
        String offerText = offerTextWidget.getText();
        return chatInputText.equals("Set a price for each item:") && (offerText.equals("Buy offer") || offerText.equals("Sell offer"));
    }


    private Widget getChatboxTitleWidget() {
        return client.getWidget(ComponentID.CHATBOX_TITLE);
    }

    private Widget getOfferTextWidget() {
        var offerContainerWidget = client.getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER);
        if (offerContainerWidget == null) return null;
        return offerContainerWidget.getChild(GE_OFFER_INIT_STATE_CHILD_ID);
    }

    public boolean isSelling() {
        return client.getVarbitValue(Varbits.GE_OFFER_CREATION_TYPE) == 1;
    }

    public boolean isBuying() {
        return client.getVarbitValue(Varbits.GE_OFFER_CREATION_TYPE) == 0;
    }

    public String getOfferType() {
        if (isBuying()) {
            return "buy";
        } else if (isSelling()) {
            return "sell";
        } else {
            return null;
        }
    }

    public void setSuggestedAction(Suggestion suggestion) {
        var currentItemId = client.getVarpValue(CURRENT_GE_ITEM);

        if (isSettingQuantity()) {
            if (suggestion == null || currentItemId != suggestion.getItemId()) {
                return;
            }
            setChatboxValue(suggestion.getQuantity());
        } else if (isSettingPrice()) {
            int price = -1;
            if (suggestion == null || currentItemId != suggestion.getItemId()
                    || !suggestion.getType().equals(getOfferType())) {
                if (offerManager.getViewedSlotItemId() != currentItemId) {
                    return;
                }
                price = offerManager.getViewedSlotItemPrice();
            } else {
                price = suggestion.getPrice();
            }

            if (price == -1) return;

            setChatboxValue(price);
        }
    }

    public void setChatboxValue(int value) {
        var chatboxInputWidget = client.getWidget(ComponentID.CHATBOX_FULL_INPUT);
        if (chatboxInputWidget == null) return;
        chatboxInputWidget.setText(value + "*");
        client.setVarcStrValue(VarClientStr.INPUT_TEXT, String.valueOf(value));
    }
}
