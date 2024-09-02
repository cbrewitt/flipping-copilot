package com.flippingcopilot.ui;

import com.flippingcopilot.controller.FlippingCopilotPlugin;
import com.flippingcopilot.model.Suggestion;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.FontID;
import net.runelite.api.widgets.*;

import static net.runelite.api.VarPlayer.CURRENT_GE_ITEM;

@Slf4j
public class OfferEditor {
    private FlippingCopilotPlugin plugin;
    private Widget text;
    private static final int MOUSE_OFF_TEXT_COLOR = 0x0040FF;
    private static final int MOUSE_OFF_ERROR_TEXT_COLOR = 0xAA2222;

    public OfferEditor(Widget parent, FlippingCopilotPlugin plugin) {
        this.plugin = plugin;
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
        var currentItemId = plugin.client.getVarpValue(CURRENT_GE_ITEM);
        if (plugin.offerHandler.isSettingQuantity()) {
            if (currentItemId != suggestion.getItemId()) {
                return;
            }

            shiftChatboxWidgetsDown();
            showQuantity(suggestion.getQuantity());
        } else if (plugin.offerHandler.isSettingPrice()) {
            int price = -1;
            if (currentItemId != suggestion.getItemId()) {
                if (plugin.offerHandler.getViewedSlotPriceErrorText() != null) {
                    shiftChatboxWidgetsDown();
                    setErrorText(plugin.offerHandler.getViewedSlotPriceErrorText());
                    return;
                }

                if (plugin.offerHandler.getViewedSlotItemId() == currentItemId) {
                    price = plugin.offerHandler.getViewedSlotItemPrice();
                }
            } else {
                price = suggestion.getPrice();
            }

            if (price == -1) return;

            shiftChatboxWidgetsDown();
            showPrice(price);
        }
    }

    private void showQuantity(int quantity) {
        text.setText("set to Copilot quantity: " + quantity);
        text.setAction(1, "Set quantity");
        setHoverListeners(text);
        text.setOnOpListener((JavaScriptCallback) ev ->
        {
            plugin.offerHandler.setChatboxValue(quantity);
        });
    }

    private void showPrice(int price) {
        text.setText("set to Copilot price: " + String.format("%,d", price) + " gp");
        text.setAction(0, "Set price");
        setHoverListeners(text);
        text.setOnOpListener((JavaScriptCallback) ev ->
        {
            plugin.offerHandler.setChatboxValue(price);
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
        Widget chatboxTitle = plugin.client.getWidget(ComponentID.CHATBOX_TITLE);
        if (chatboxTitle != null) {
            chatboxTitle.setOriginalY(chatboxTitle.getOriginalY() + 7);
            chatboxTitle.revalidate();
        }
    }
}