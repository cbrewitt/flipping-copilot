package com.flippingcopilot.controller;

import com.flippingcopilot.model.Suggestion;
import com.flippingcopilot.model.SuggestionPreferencesManager;
import com.flippingcopilot.model.SuggestionManager;
import com.flippingcopilot.model.AccountStatus;
import com.flippingcopilot.model.AccountStatusManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
@Slf4j
public class GePreviousSearch {

    private final SuggestionManager suggestionManager;
    private final SuggestionPreferencesManager suggestionPreferencesManager;
    private final AccountStatusManager accountStatusManager;
    private final GrandExchange grandExchange;
    private final HighlightController highlightController;
    private final Client client;


    public void showSuggestedItemInSearch() {
        Suggestion suggestion = suggestionManager.getSuggestion();
        if (suggestion == null) {
            return;
        }

        if (isScanningForDumpsSuggested(suggestion)) {
            if ((grandExchange.isPreviousSearchSet() || copilotPreviousSearchItemExists()) && grandExchange.showLastSearchEnabled()) {
                setScanningForDumpsMessage();
            } else {
                createPreviousSearchWidget(-1, "");
                createPreviousSearchItemNameWidget("");
                createPreviousSearchItemWidget(-1);
                createPreviousSearchTextWidget();
                setScanningForDumpsMessage();
            }
            highlightController.redraw();
            return;
        }

        if (suggestion.getType().equals("buy")) {
            if ((grandExchange.isPreviousSearchSet() || copilotPreviousSearchItemExists()) && grandExchange.showLastSearchEnabled()) {
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

    private boolean isScanningForDumpsSuggested(Suggestion suggestion) {
        AccountStatus accountStatus = accountStatusManager.getAccountStatus();
        return accountStatus != null
                && "wait".equals(suggestion.getType())
                && grandExchange.isOpen()
                && accountStatus.emptySlotExists()
                && !accountStatus.moreGpNeeded()
                && suggestionPreferencesManager.isReceiveDumpSuggestions();
    }

    private boolean copilotPreviousSearchItemExists() {
        Widget searchResults = client.getWidget(ComponentID.CHATBOX_GE_SEARCH_RESULTS);
        if(searchResults == null || searchResults.getChildren() == null || searchResults.getChildren().length < 2) {
            return false;
        }
        for (int i = 0; i < searchResults.getChildren().length; i++) {
            Widget child = searchResults.getChild(i);
            if (child == null) {
                continue;
            }
            String text = child.getText();
            if (text == null) {
                continue;
            }
            if(text.startsWith("Copilot item:") || text.startsWith("Scanning for dumps...")) {
                return true;
            }
        }
        return false;
    }

    private void setPreviousSearch(int itemId, String itemName) {
        Widget searchResults = client.getWidget(ComponentID.CHATBOX_GE_SEARCH_RESULTS);
        Widget previousSearch = searchResults.getChild(0);
        previousSearch.setHasListener(true);
        previousSearch.setOnOpListener(754, itemId, 84);
        previousSearch.setOnKeyListener(754, itemId, -2147483640);
        previousSearch.setName("<col=ff9040>" + itemName + "</col>");
        previousSearch.setAction(0, "Select");
        previousSearch.revalidate();

        Widget previousSearchText = searchResults.getChild(1);
        previousSearchText.setText("Copilot item:");
        previousSearchText.setOriginalWidth(95);
        previousSearchText.setXTextAlignment(WidgetTextAlignment.LEFT);
        previousSearchText.revalidate();

        Widget itemNameWidget = searchResults.getChild(2);
        itemNameWidget.setText(itemName);
        itemNameWidget.revalidate();

        Widget item = searchResults.getChild(3);
        item.setItemId(itemId);
        item.revalidate();
    }

    private void setScanningForDumpsMessage() {
        Widget searchResults = client.getWidget(ComponentID.CHATBOX_GE_SEARCH_RESULTS);
        if (searchResults == null) {
            return;
        }

        Widget previousSearch = searchResults.getChild(0);
        if (previousSearch != null) {
            previousSearch.setHasListener(false);
            previousSearch.setName("");
            previousSearch.setAction(0, "");
            previousSearch.revalidate();
        }

        Widget previousSearchText = searchResults.getChild(1);
        if (previousSearchText != null) {
            previousSearchText.setText("Scanning for dumps...");
            previousSearchText.setOriginalWidth(256);
            previousSearchText.setXTextAlignment(WidgetTextAlignment.CENTER);
            previousSearchText.revalidate();
        }

        Widget itemNameWidget = searchResults.getChild(2);
        if (itemNameWidget != null) {
            itemNameWidget.setText("");
            itemNameWidget.revalidate();
        }

        Widget item = searchResults.getChild(3);
        if (item != null) {
            item.setItemId(-1);
            item.revalidate();
        }
    }

    private void createPreviousSearchWidget(int itemId, String itemName) {
        Widget parentWidget = client.getWidget(ComponentID.CHATBOX_GE_SEARCH_RESULTS);
        Widget widget = parentWidget.createChild(0, WidgetType.RECTANGLE);
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

    private void createPreviousSearchTextWidget() {
        Widget parentWidget = client.getWidget(ComponentID.CHATBOX_GE_SEARCH_RESULTS);
        Widget widget = parentWidget.createChild(1, WidgetType.TEXT);
        widget.setText("Copilot item:");
        widget.setFontId(495);
        widget.setOriginalX(114);
        widget.setOriginalY(0);
        widget.setOriginalWidth(95);
        widget.setOriginalHeight(32);
        widget.setYTextAlignment(1);
        widget.revalidate();
    }

    private void createPreviousSearchItemNameWidget(String itemName) {
        Widget parentWidget = client.getWidget(ComponentID.CHATBOX_GE_SEARCH_RESULTS);
        Widget widget = parentWidget.createChild(2, WidgetType.TEXT);
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
        Widget widget = parentWidget.createChild(3, WidgetType.GRAPHIC);
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


}
