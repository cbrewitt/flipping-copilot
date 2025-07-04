package com.flippingcopilot.ui;

import com.flippingcopilot.controller.GrandExchange;
import com.flippingcopilot.controller.OfferHandler;
import com.flippingcopilot.model.OfferManager;
import com.flippingcopilot.model.Suggestion;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.widgets.*;

import static net.runelite.api.VarPlayer.CURRENT_GE_ITEM;

@Slf4j
public class OfferEditor {
    private final OfferManager offerManager;
    private final OfferHandler offerHandler;
    private final Client client;

    private Widget text;
    private Widget priceGraphText;
    private static final int MOUSE_OFF_TEXT_COLOR = 0x0040FF;
    private static final int MOUSE_OFF_ERROR_TEXT_COLOR = 0xAA2222;

    public OfferEditor(OfferManager offerManager, Widget parent, OfferHandler offerHandler, Client client) {
        this.offerManager = offerManager;
        this.offerHandler = offerHandler;
        this.client = client;
        if (parent == null) {
            return;
        }

        text = parent.createChild(-1, WidgetType.TEXT);
        prepareTextWidget(text, WidgetTextAlignment.LEFT, WidgetPositionMode.ABSOLUTE_TOP, 40, 10);
    }

    private void prepareTextWidget(Widget widget, int xAlignment, int yMode, int yOffset, int xOffset) {
        widget.setTextColor(MOUSE_OFF_TEXT_COLOR);
        widget.setFontId(FontID.VERDANA_11_BOLD);
        widget.setYPositionMode(yMode);
        widget.setOriginalX(xOffset);
        widget.setOriginalY(yOffset);
        widget.setOriginalHeight(20);
        widget.setXTextAlignment(xAlignment);
        widget.setWidthMode(WidgetSizeMode.MINUS);
        widget.revalidate();
    }

    public void showSuggestion(Suggestion suggestion) {
        var currentItemId = client.getVarpValue(CURRENT_GE_ITEM);
        if (offerHandler.isSettingQuantity()) {
            if (currentItemId != suggestion.getItemId()) {
                return;
            }
            if (!suggestion.getType().equals(offerHandler.getOfferType())) {
                return;
            }

            shiftChatboxWidgetsDown();
            showQuantity(suggestion.getQuantity());
        } else if (offerHandler.isSettingPrice()) {
            if (currentItemId != suggestion.getItemId()
                    || !suggestion.getType().equals(offerHandler.getOfferType())) {
                int price = offerManager.getViewedSlotItemPrice();
                if (offerHandler.getViewedSlotPriceErrorText() != null && price <= 0) {
                    shiftChatboxWidgetsDown();
                    setErrorText(offerHandler.getViewedSlotPriceErrorText());
                    return;
                }

                if (offerManager.getViewedSlotItemId() == currentItemId) {
                    shiftChatboxWidgetsDown();
                    if (offerHandler.getViewedSlotPriceErrorText() != null) {
                        showPriceWithWarning(price, offerHandler.getViewedSlotPriceErrorText());
                    } else {
                        showPrice(price);
                    }
                }
            } else {
                shiftChatboxWidgetsDown();
                showPrice(suggestion.getPrice());
            }
        }
    }

    private void showQuantity(int quantity) {
        text.setText("set to Copilot quantity: " + quantity);
        text.setAction(1, "Set quantity");
        setHoverListeners(text);
        text.setOnOpListener((JavaScriptCallback) ev ->
        {
            offerHandler.setChatboxValue(quantity);
        });
    }

    public void showPrice(int price) {
        text.setText("set to Copilot price: " + String.format("%,d", price) + " gp");
        text.setAction(0, "Set price");
        setHoverListeners(text);
        text.setOnOpListener((JavaScriptCallback) ev ->
        {
            offerHandler.setChatboxValue(price);
        });
    }

    private void showPriceWithWarning(int price, String warning) {
        text.setText("set to Copilot price: " + String.format("%,d", price) + " gp. " + warning);
        text.setAction(0, "Set price");
        setHoverListeners(text);
        text.setOnOpListener((JavaScriptCallback) ev ->
        {
            offerHandler.setChatboxValue(price);
        });
    }

    private void setHoverListeners(Widget widget) {
        widget.setHasListener(true);
        widget.setOnMouseRepeatListener((JavaScriptCallback) ev -> widget.setTextColor(0xFFFFFF));
        widget.setOnMouseLeaveListener((JavaScriptCallback) ev -> widget.setTextColor(MOUSE_OFF_TEXT_COLOR));
    }

    private void setErrorText(String message) {
        text.setText(message);
        text.setTextColor(MOUSE_OFF_ERROR_TEXT_COLOR);
        text.revalidate();
    }

    private void shiftChatboxWidgetsDown() {
        Widget chatboxTitle = client.getWidget(ComponentID.CHATBOX_TITLE);
        if (chatboxTitle != null) {
            chatboxTitle.setOriginalY(chatboxTitle.getOriginalY() + 7);
            chatboxTitle.revalidate();
        }
    }
}
