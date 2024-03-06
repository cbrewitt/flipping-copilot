package com.flippingcopilot.ui;

import com.flippingcopilot.model.Suggestion;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.VarClientStr;
import net.runelite.api.widgets.*;

import static net.runelite.api.VarPlayer.CURRENT_GE_ITEM;

public class OfferEditor {
    private final Client client;
    private Widget text;
    private static final int MOUSE_OFF_TEXT_COLOR = 0x0040FF;
    private static final int GE_OFFER_INIT_STATE_CHILD_ID = 18;

    public OfferEditor(Widget parent, Client client) {
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
        widget.setHasListener(true);
        widget.setOnMouseRepeatListener((JavaScriptCallback) ev -> widget.setTextColor(0xFFFFFF));
        widget.setOnMouseLeaveListener((JavaScriptCallback) ev -> widget.setTextColor(MOUSE_OFF_TEXT_COLOR));
        widget.revalidate();
    }

    public void showSuggestion(Suggestion suggestion) {
        if (client.getVarpValue(CURRENT_GE_ITEM) != suggestion.getItemId()) {
            return;
        }

        String chatInputText = client.getWidget(WidgetInfo.CHATBOX_TITLE).getText();
        String offerText = client.getWidget(WidgetInfo.GRAND_EXCHANGE_OFFER_CONTAINER).getChild(GE_OFFER_INIT_STATE_CHILD_ID).getText();

        if ((chatInputText.equals("How many do you wish to buy?") && suggestion.getType().equals("buy"))
                || (chatInputText.equals("How many do you wish to sell?") && suggestion.getType().equals("sell"))) {
            shiftChatboxWidgetsDown();
            showQuantity(suggestion.getQuantity());
        } else if (chatInputText.equals("Set a price for each item:")
                && ((offerText.equals("Buy offer") && suggestion.getType().equals("buy"))
                || (offerText.equals("Sell offer") && suggestion.getType().equals("sell")))) {
            shiftChatboxWidgetsDown();
            showPrice(suggestion.getPrice());
        }
    }

    private void showQuantity(int quantity) {
        text.setText("set to Copilot quantity: " + quantity);
        text.setAction(1, "Set quantity");
        text.setOnOpListener((JavaScriptCallback) ev ->
        {
            client.getWidget(WidgetInfo.CHATBOX_FULL_INPUT).setText(quantity + "*");
            client.setVarcStrValue(VarClientStr.INPUT_TEXT, String.valueOf(quantity));
        });
    }

    private void showPrice(int price) {
        text.setText("set to Copilot price: " + String.format("%,d", price) + " gp");
        text.setAction(0, "Set price");
        text.setOnOpListener((JavaScriptCallback) ev ->
        {
            client.getWidget(WidgetInfo.CHATBOX_FULL_INPUT).setText(price + "*");
            client.setVarcStrValue(VarClientStr.INPUT_TEXT, String.valueOf(price));
        });
    }

    private void shiftChatboxWidgetsDown() {
        Widget chatboxTitle = client.getWidget(WidgetInfo.CHATBOX_TITLE);
        if (chatboxTitle != null) {
            chatboxTitle.setOriginalY(chatboxTitle.getOriginalY() + 7);
            chatboxTitle.revalidate();
        }

    }
}