package com.flippingcopilot.util;

import com.flippingcopilot.controller.FlippingCopilotPlugin;
import com.flippingcopilot.model.Suggestion;
import net.runelite.api.VarClientStr;
import net.runelite.api.widgets.ComponentID;

import static net.runelite.api.VarPlayer.CURRENT_GE_ITEM;

public class OfferHandler {

    private static final int GE_OFFER_INIT_STATE_CHILD_ID = 20;

    private FlippingCopilotPlugin plugin;

    public OfferHandler(FlippingCopilotPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isSettingQuantity(Suggestion suggestion) {
        var chatboxTitleWidget = plugin.client.getWidget(ComponentID.CHATBOX_TITLE);
        if (chatboxTitleWidget == null) return false;
        String chatInputText = chatboxTitleWidget.getText();
        return (chatInputText.equals("How many do you wish to buy?") && suggestion.getType().equals("buy"))
                || (chatInputText.equals("How many do you wish to sell?") && suggestion.getType().equals("sell"));
    }

    public boolean isSettingPrice(Suggestion suggestion) {
        var chatboxTitleWidget = plugin.client.getWidget(ComponentID.CHATBOX_TITLE);
        if (chatboxTitleWidget == null) return false;
        String chatInputText = chatboxTitleWidget.getText();
        var offerContainerWidget = plugin.client.getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER);
        if (offerContainerWidget == null) return false;
        var initChild = offerContainerWidget.getChild(GE_OFFER_INIT_STATE_CHILD_ID);
        if (initChild == null) return false;
        String offerText = initChild.getText();
        return (chatInputText.equals("Set a price for each item:")
                && ((offerText.equals("Buy offer") && suggestion.getType().equals("buy"))
                || (offerText.equals("Sell offer") && suggestion.getType().equals("sell"))));
    }

    public void setSuggestedAction(Suggestion suggestion) {
        if (plugin.client.getVarpValue(CURRENT_GE_ITEM) != suggestion.getItemId()) {
            return;
        }

        if (isSettingQuantity(suggestion)) {
            setChatboxValue(suggestion.getQuantity());
        } else if (isSettingPrice(suggestion)) {
            setChatboxValue(suggestion.getPrice());
        }
    }

    public void setChatboxValue(int value) {
        var chatboxInputWidget = plugin.client.getWidget(ComponentID.CHATBOX_FULL_INPUT);
        if (chatboxInputWidget == null) return;
        chatboxInputWidget.setText(value + "*");
        plugin.client.setVarcStrValue(VarClientStr.INPUT_TEXT, String.valueOf(value));
    }
}
