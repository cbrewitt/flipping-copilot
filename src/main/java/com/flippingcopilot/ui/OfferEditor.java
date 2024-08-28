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
        widget.setHasListener(true);
        widget.setOnMouseRepeatListener((JavaScriptCallback) ev -> widget.setTextColor(0xFFFFFF));
        widget.setOnMouseLeaveListener((JavaScriptCallback) ev -> widget.setTextColor(MOUSE_OFF_TEXT_COLOR));
        widget.revalidate();
    }

    public void showSuggestion(Suggestion suggestion) {
        if (plugin.client.getVarpValue(CURRENT_GE_ITEM) != suggestion.getItemId()) {
            return;
        }

        if (plugin.offerHandler.isSettingQuantity(suggestion)) {
            shiftChatboxWidgetsDown();
            showQuantity(suggestion.getQuantity());
        } else if (plugin.offerHandler.isSettingPrice(suggestion)) {
            shiftChatboxWidgetsDown();
            showPrice(suggestion.getPrice());
        }
    }

    private void showQuantity(int quantity) {
        text.setText("set to Copilot quantity: " + quantity);
        text.setAction(1, "Set quantity");
        text.setOnOpListener((JavaScriptCallback) ev ->
        {
            plugin.offerHandler.setChatboxValue(quantity);
        });
    }

    private void showPrice(int price) {
        text.setText("set to Copilot price: " + String.format("%,d", price) + " gp");
        text.setAction(0, "Set price");
        text.setOnOpListener((JavaScriptCallback) ev ->
        {
            plugin.offerHandler.setChatboxValue(price);
        });
    }

    private void shiftChatboxWidgetsDown() {
        Widget chatboxTitle = plugin.client.getWidget(ComponentID.CHATBOX_TITLE);
        if (chatboxTitle != null) {
            chatboxTitle.setOriginalY(chatboxTitle.getOriginalY() + 7);
            chatboxTitle.revalidate();
        }
    }
}