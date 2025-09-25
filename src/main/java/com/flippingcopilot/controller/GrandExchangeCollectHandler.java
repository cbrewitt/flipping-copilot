package com.flippingcopilot.controller;

import com.flippingcopilot.model.GrandExchangeUncollectedManager;
import com.flippingcopilot.model.OsrsLoginManager;
import com.flippingcopilot.model.SuggestionManager;
import com.flippingcopilot.ui.SuggestionPanel;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class GrandExchangeCollectHandler {

    // dependencies
    private final OsrsLoginManager osrsLoginManager;
    private final GrandExchangeUncollectedManager geUncollected;
    private final SuggestionManager suggestionManager;
    private final Client client;

    @Setter
    private SuggestionPanel suggestionPanel;

    public void handleCollect(MenuOptionClicked event, int slot) {
        String menuOption = event.getMenuOption();
        Widget widget = event.getWidget();
        if (widget != null) {
            handleCollectAll(menuOption, widget);
            handleCollectWithSlotOpen(menuOption, widget, slot);
            handleCollectionBoxCollectAll(menuOption, widget);
            handleCollectionBoxCollectItem(menuOption, widget);
            handleModifyOffer(menuOption, widget);
        }
    }

    private void handleModifyOffer(String menuOption, Widget widget) {
        if (menuOption.equals("Modify offer")) {
            int slot = widget.getId() - 30474247;
            log.debug("modify offer clicked on slot {}", slot);
            suggestionManager.suggestionsDelayedUntil = client.getTickCount() + 2;
            geUncollected.clearSlotUncollected(osrsLoginManager.getAccountHash(), slot);
        }
    }

    private void handleCollectAll(String menuOption, Widget widget) {
        if (widget.getId() == 30474246) {
            if (menuOption.equals("Collect to inventory")) {
                geUncollected.clearAllUncollected(osrsLoginManager.getAccountHash());
            } else if (menuOption.equals("Collect to bank")) {
                geUncollected.clearAllUncollected(osrsLoginManager.getAccountHash());
            }
            suggestionPanel.refresh();
        }
    }

    private void handleCollectWithSlotOpen(String menuOption, Widget widget, int slot) {
        if (widget.getId() == 30474264 ) {
            if (menuOption.contains("Collect")) {
                geUncollected.clearSlotUncollected(osrsLoginManager.getAccountHash(), slot);
            } else if (menuOption.contains("Bank")) {
                geUncollected.clearSlotUncollected(osrsLoginManager.getAccountHash(), slot);
            }
            suggestionPanel.refresh();
        }
    }

    private void handleCollectionBoxCollectAll(String menuOption, Widget widget) {
        if (widget.getId() == 26345476 && menuOption.equals("Collect to bank")) {
            geUncollected.clearAllUncollected(osrsLoginManager.getAccountHash());
            suggestionPanel.refresh();
            
        } else if (widget.getId() == 26345475 && menuOption.equals("Collect to inventory")) {
            geUncollected.clearAllUncollected(osrsLoginManager.getAccountHash());
            suggestionPanel.refresh();
        }
    }

    private void handleCollectionBoxCollectItem(String menuOption, Widget widget) {
        int slot = widget.getId() - 26345477;
        if (slot >= 0 && slot <= 7) {
            if (menuOption.contains("Collect")) {
                geUncollected.clearSlotUncollected(osrsLoginManager.getAccountHash(), slot);
            } else if (menuOption.contains("Bank")) {
                geUncollected.clearSlotUncollected(osrsLoginManager.getAccountHash(), slot);
            }
            suggestionPanel.refresh();
        }
    }
}
