package copilot.ui;

import net.runelite.api.*;
import copilot.model.*;
import copilot.controller.*;
import copilot.config.*;
import lombok.extern.slf4j.*;
import net.runelite.api.widgets.*;
import net.runelite.client.config.*;

import java.util.*;

import static net.runelite.api.VarPlayer.CURRENT_GE_ITEM;

@Slf4j
public class OfferEditor {
    private final Offers offerManager;
    private final OfferHandler offerHandler;
    private final Client client;
    private final CopilotConfig config;

    private Widget text;
    private static final int MOUSE_OFF_TEXT_COLOR = 0x0040FF, MOUSE_OFF_ERROR_TEXT_COLOR = 0xAA2222;

    public OfferEditor(Offers offerManager, Widget parent, OfferHandler offerHandler, Client client, CopilotConfig config) {
        this.offerManager = offerManager; this.offerHandler = offerHandler; this.client = client; this.config = config;
        if (parent == null) { return; }

        text = parent.createChild(-1, WidgetType.TEXT);
        prepareTextWidget(text, WidgetTextAlignment.LEFT, WidgetPositionMode.ABSOLUTE_TOP, 40, 10);
    }

    private void prepareTextWidget(Widget widget, int xAlignment, int yMode, int yOffset, int xOffset) {
        widget.setTextColor(MOUSE_OFF_TEXT_COLOR); widget.setFontId(FontID.VERDANA_11_BOLD);
        widget.setYPositionMode(yMode); widget.setOriginalX(xOffset); widget.setOriginalY(yOffset);
        widget.setOriginalHeight(20); widget.setXTextAlignment(xAlignment); widget.setWidthMode(WidgetSizeMode.MINUS);
        widget.revalidate();
    }

    public void showSuggestion(Suggestion suggestion) {
        var currentItemId = client.getVarpValue(CURRENT_GE_ITEM);
        if (offerHandler.isSettingQuantity()) {
            if (currentItemId != suggestion.itemId) { return; }
            if (!Objects.equals(suggestion.offerType(), offerHandler.getOfferType())) { return; }

            shiftChatboxWidgetsDown();
            showQuantity(suggestion.quantity);
        } else if (offerHandler.isSettingPrice()) {
            if (currentItemId != suggestion.itemId
                    || !Objects.equals(suggestion.offerType(), offerHandler.getOfferType())) {
                long price = offerManager.getViewedSlotItemPrice();
                if (offerHandler.getViewedSlotPriceErrorText() != null && price <= 0) {
                    shiftChatboxWidgetsDown();
                    setErrorText(offerHandler.getViewedSlotPriceErrorText());
                    return;
                }

                if (offerManager.getViewedSlotItemId() == currentItemId) {
                    shiftChatboxWidgetsDown();
                    if (offerHandler.getViewedSlotPriceErrorText() != null) {
                        showPrice(price, offerHandler.getViewedSlotPriceErrorText());
                    } else {
                        showPrice(price);
                    }
                }
            } else {
                shiftChatboxWidgetsDown();
                showPrice(suggestion.price);
            }
        }
    }

    private void showQuantity(int quantity) {
        text.setText(setActionText("Copilot quantity: " + quantity)); text.setAction(1, "Set quantity");
        setHoverListeners(text);
        text.setOnOpListener((JavaScriptCallback) ev -> {
            offerHandler.setChatboxValue(quantity);
        });
    }

    public void showPrice(long price) { showPrice(price, null); }

    private void showPrice(long price, String warning) {
        text.setText(setActionText("Copilot price: " + String.format("%,d", price) + " gp")
                + (warning == null ? "" : ". " + warning));
        text.setAction(0, "Set price");
        setHoverListeners(text);
        text.setOnOpListener((JavaScriptCallback) ev -> {
            offerHandler.setChatboxValue(price);
        });
    }

    private String setActionText(String target) {
        var keybind = config.quickSetKeybind();
        if (keybind == null || Keybind.NOT_SET.equals(keybind)) { return "set to " + target; }
        return "Press [" + keybind + "] to set to " + target;
    }

    private void setHoverListeners(Widget widget) {
        widget.setHasListener(true);
        widget.setOnMouseRepeatListener((JavaScriptCallback) ev -> widget.setTextColor(0xFFFFFF));
        widget.setOnMouseLeaveListener((JavaScriptCallback) ev -> widget.setTextColor(MOUSE_OFF_TEXT_COLOR));
    }

    private void setErrorText(String message) {
        text.setText(message); text.setTextColor(MOUSE_OFF_ERROR_TEXT_COLOR);
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
