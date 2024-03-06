package com.flippingcopilot.controller;

import com.flippingcopilot.model.AccountStatus;
import lombok.AllArgsConstructor;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;


@AllArgsConstructor
public class GrandExchangeCollectHandler {
    AccountStatus accountStatus;
    SuggestionHandler suggestionHandler;

    void handleCollect(MenuOptionClicked event, int slot) {
        String menuOption = event.getMenuOption();
        Widget widget = event.getWidget();

        if (widget != null) {
            handleCollectAll(menuOption, widget);
            handleCollectWithSlotOpen(menuOption, widget, slot);
            handleCollectionBoxCollectAll(menuOption, widget);
            handleCollectionBoxCollectItem(menuOption, widget);
        }
    }

    private void handleCollectAll(String menuOption, Widget widget) {
        if (widget.getId() == 30474246) {
            if (menuOption.equals("Collect to inventory")) {
                accountStatus.moveAllCollectablesToInventory();
            } else if (menuOption.equals("Collect to bank")) {
                accountStatus.removeCollectables();
            }
            else {
                return;
            }
            suggestionHandler.displaySuggestion();
        }
    }

    private void handleCollectWithSlotOpen(String menuOption, Widget widget, int slot) {
        if (widget.getId() == 30474264 ) {
            if (menuOption.contains("Collect")) {
                accountStatus.moveCollectedItemToInventory(slot, widget.getItemId());
            } else if (menuOption.contains("Bank")) {
                accountStatus.removeCollectedItem(slot, widget.getItemId());
            } else {
                return;
            }
            suggestionHandler.displaySuggestion();
        }
    }

    private void handleCollectionBoxCollectAll(String menuOption, Widget widget) {
        if (widget.getId() == 26345476 && menuOption.equals("Collect to bank")) {
            accountStatus.removeCollectables();
        } else if (widget.getId() == 26345475 && menuOption.equals("Collect to inventory")) {
            accountStatus.moveAllCollectablesToInventory();
        } else {
            return;
        }
        suggestionHandler.displaySuggestion();
    }

    private void handleCollectionBoxCollectItem(String menuOption, Widget widget) {
        int slot = widget.getId() - 26345477;
        if (slot >= 0 && slot <= 7) {
            if (menuOption.contains("Collect")) {
                accountStatus.moveCollectedItemToInventory(slot, widget.getItemId());
            } else if (menuOption.contains("Bank")) {
                accountStatus.removeCollectedItem(slot, widget.getItemId());
            } else {
                return;
            }
            suggestionHandler.displaySuggestion();
        }
    }

}
