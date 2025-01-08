package com.flippingcopilot.controller;

import com.flippingcopilot.model.Suggestion;
import com.flippingcopilot.model.SuggestionManager;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class GePreviousSearch {

    private final SuggestionManager suggestionManager;
    private final GrandExchange grandExchange;
    private final HighlightController highlightController;
    private final Client client;


    public void showSuggestedItemInSearch() {
        Suggestion suggestion = suggestionManager.getSuggestion();
        if (suggestion != null && suggestion.getType().equals("buy")) {
            if (grandExchange.isPreviousSearchSet() && grandExchange.showLastSearchEnabled()) {
                setPreviousSearch(suggestion.getItemId(), suggestion.getName());
            } else {
                createPreviousSearchWidget(suggestion.getItemId(), suggestion.getName());
                createPreviousSearchItemNameWidget(suggestion.getName());
                createPreviousSearchItemWidget(suggestion.getItemId());
                createPreviousSearchTextWidget();
            }
            highlightController.redraw();
        }
    }

    private void setPreviousSearch(int itemId, String itemName) {
        Widget searchResults = client.getWidget(ComponentID.CHATBOX_GE_SEARCH_RESULTS);
        Widget previousSearch = searchResults.getChild(0);
        previousSearch.setOnOpListener(754, itemId, 84);
        previousSearch.setOnKeyListener(754, itemId, -2147483640);
        previousSearch.setName("<col=ff9040>" + itemName + "</col>");
        Widget previousSearchText = searchResults.getChild(1);
        previousSearchText.setText("Copilot item:");
        Widget itemNameWidget = searchResults.getChild(2);
        itemNameWidget.setText(itemName);
        Widget item = searchResults.getChild(3);
        item.setItemId(itemId);
    }

    private void createPreviousSearchWidget(int itemId, String itemName) {
        Widget parentWidget = client.getWidget(ComponentID.CHATBOX_GE_SEARCH_RESULTS);
        Widget widget = parentWidget.createChild(WidgetType.RECTANGLE);
        widget.setTextColor(0xFFFFFF);
        widget.setOpacity(255);
        widget.setName("<col=ff9040>" + itemName + "</col>");
        widget.setHasListener(true);
        widget.setFilled(true);
        widget.setOriginalX(114);
        widget.setOriginalY(0);
        widget.setOriginalWidth(256);
        widget.setOriginalHeight(32);
        widget.setOnOpListener(754, itemId, 84);
        widget.setOnKeyListener(754, itemId, -2147483640);
        widget.setHasListener(true);
        widget.setAction(0, "Select");
        // set opacity to 200 when mouse is hovering
        widget.setOnMouseOverListener((JavaScriptCallback) ev -> {
            widget.setOpacity(200);
        });
        // set opacity back to 255 when mouse is not hovering
        widget.setOnMouseLeaveListener((JavaScriptCallback) ev -> {
            widget.setOpacity(255);
        });

        widget.revalidate();
    }

    private void createPreviousSearchItemNameWidget(String itemName) {
        Widget parentWidget = client.getWidget(ComponentID.CHATBOX_GE_SEARCH_RESULTS);
        Widget widget = parentWidget.createChild(WidgetType.TEXT);
        widget.setText(itemName);
        widget.setFontId(495);
        widget.setOriginalX(254);
        widget.setOriginalY(0);
        widget.setOriginalWidth(116);
        widget.setOriginalHeight(32);
        widget.setYTextAlignment(1);
        widget.revalidate();
    }

    private void createPreviousSearchItemWidget(int itemId) {
        Widget parentWidget = client.getWidget(ComponentID.CHATBOX_GE_SEARCH_RESULTS);
        Widget widget = parentWidget.createChild(WidgetType.GRAPHIC);
        widget.setItemId(itemId);
        widget.setItemQuantity(1);
        widget.setItemQuantityMode(0);
        widget.setRotationX(550);
        widget.setModelZoom(1031);
        widget.setBorderType(1);
        widget.setOriginalX(214);
        widget.setOriginalY(0);
        widget.setOriginalWidth(36);
        widget.setOriginalHeight(32);
        widget.revalidate();
    }

    private void createPreviousSearchTextWidget() {
        Widget parentWidget = client.getWidget(ComponentID.CHATBOX_GE_SEARCH_RESULTS);
        Widget widget = parentWidget.createChild(WidgetType.TEXT);
        widget.setText("Copilot item:");
        widget.setFontId(495);
        widget.setOriginalX(114);
        widget.setOriginalY(0);
        widget.setOriginalWidth(95);
        widget.setOriginalHeight(32);
        widget.setYTextAlignment(1);
        widget.revalidate();
    }
}
