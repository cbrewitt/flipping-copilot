package com.flippingcopilot.controller;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.model.OfferManager;
import com.flippingcopilot.model.PortfolioItemCardData;
import com.flippingcopilot.model.Suggestion;
import com.flippingcopilot.model.SuggestionManager;
import com.flippingcopilot.model.ToggleItemPortfolioRequest;
import com.flippingcopilot.rs.BankStateRS;
import com.flippingcopilot.rs.PortfolioStateRS;
import com.flippingcopilot.ui.flipsdialog.FlipsDialogController;
import com.flippingcopilot.ui.graph.model.PriceLine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.api.*;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class MenuHandler {

    private static final int BANK_WIDGET_GROUP = 12;
    private static final int[] BANK_ITEM_CONTAINER_CHILDREN = {12, 13, 89};
    private static final int BANK_INVENTORY_WIDGET_GROUP = 15;
    private static final int BANK_INVENTORY_WIDGET_CHILD = 3;

    private final FlippingCopilotConfig config;
    private final Client client;
    private final OfferManager offerManager;
    private final GrandExchange grandExchange;
    private final SuggestionManager suggestionManager;
    private final FlipsDialogController flipsDialogController;
    private final ItemController itemController;
    private final PortfolioController portfolioController;
    private final CopilotLoginController copilotLoginController;
    private final ApiRequestHandler apiRequestHandler;
    private final PortfolioStateRS portfolioStateRS;
    private final BankStateRS bankStateRS;
    private final ClientThread clientThread;
    private final PlayerLocationController playerLocationController;

    private static final String MENU_ADD_TO_PORTFOLIO = "Add to portfolio";
    private static final String MENU_REMOVE_FROM_PORTFOLIO = "Remove from portfolio";


    public void injectCopilotPriceGraphMenuEntry(MenuEntryAdded event) {
        if (!playerLocationController.isNearGE()) {
            return;
        }

        if(!config.priceGraphMenuOptionEnabled()) {
            return;
        }
        if (event.getOption().equals("View offer")) {
            long slotWidgetId = event.getActionParam1();
            String menuTarget = event.getTarget();
            client.getMenu()
                    .createMenuEntry(-1)
                    .setOption("Copilot graph")
                    .setTarget(menuTarget)
                    .onClick((MenuEntry e) -> {
                        GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
                        for (int i = 0; i < offers.length; i++) {
                            Widget slotWidget = client.getWidget(465, 7 + i);
                            if (slotWidget != null && slotWidget.getId() == slotWidgetId) {
                                int itemId = offers[i].getItemId();
                                PriceLine priceLine = buildPriceLine(offers[i]);
                                flipsDialogController.showPriceGraphTab(itemId, false, priceLine);
                                log.debug("matched widget to slot {}, item {}", i, offers[i].getItemId());
                            }
                        }
                    });
        } else if (shouldAddInventoryPriceGraphEntry(event)) {
            int inventorySlot = event.getActionParam0();
            int inventoryWidgetId = event.getActionParam1();
            Widget inventoryWidget = client.getWidget(inventoryWidgetId);
            if (inventoryWidget == null || inventorySlot < 0) {
                return;
            }
            Widget[] items = inventoryWidget.getDynamicChildren();
            if (items == null || inventorySlot >= items.length) {
                return;
            }
            Widget itemWidget = items[inventorySlot];
            if (itemWidget == null || itemWidget.getItemId() <= 0) {
                return;
            }
            int itemId = itemWidget.getItemId();
            if (!isGeTradableItem(itemId)) {
                return;
            }
            String menuTarget = resolveMenuTarget(event.getTarget(), itemId);
            int graphItemId = toUnnotedItemId(itemId);
            client.getMenu()
                    .createMenuEntry(-1)
                    .setOption("Copilot graph")
                    .setTarget(menuTarget)
                    .onClick((MenuEntry e) -> flipsDialogController.showPriceGraphTab(graphItemId, false, null));
        }
    }

    public void injectInventoryPortfolioMenuEntry(MenuEntryAdded event) {
        if (!playerLocationController.isNearGE()) {
            return;
        }

        InventoryMenuItem menuItem = getInventoryMenuItem(event);
        if (menuItem == null) {
            return;
        }
        if (!portfolioStateRS.get().isLoaded()) {
            return;
        }

        Integer accountId = copilotLoginController.getActiveAccountId();
        PortfolioItemCardData cardData = portfolioStateRS.get().getItemCardDataByItemId().get(menuItem.unnotedItemId);
        boolean inPortfolio = cardData != null && cardData.isInPortfolio();
        String option = inPortfolio ? MENU_REMOVE_FROM_PORTFOLIO : MENU_ADD_TO_PORTFOLIO;

        client.getMenu()
                .createMenuEntry(-1)
                .setOption(option)
                .setTarget(menuItem.menuTarget)
                .onClick((MenuEntry e) -> onTogglePortfolioClicked(option, menuItem, accountId));
    }

    private void onTogglePortfolioClicked(String option, InventoryMenuItem menuItem, Integer accountId) {
        if (accountId == null) {
            return;
        }
        clientThread.invokeLater(() -> {
            Map<Integer, Integer> runeliteInventory = itemController.getRunliteInventory();
            int bagQuantity = runeliteInventory == null ? 0 : Math.max(0, runeliteInventory.getOrDefault(menuItem.unnotedItemId, 0));

            int bankQuantity;
            if (bankStateRS.get().isLoaded()) {
                bankQuantity = Math.max(0, bankStateRS.get().getItems().getOrDefault(menuItem.unnotedItemId, 0));
            } else {
                bankQuantity = -1;
            }

            int portfolioId = MENU_REMOVE_FROM_PORTFOLIO.equals(option) ? -1 : 0;
            ToggleItemPortfolioRequest request = new ToggleItemPortfolioRequest(accountId, menuItem.unnotedItemId, portfolioId, bagQuantity, bankQuantity);
            apiRequestHandler.toggleItemPortfolioAsync(
                    request,
                    (userId, result) -> {
                        Suggestion suggestion = suggestionManager.getSuggestion();
                        portfolioStateRS.updatePortfolioState(
                                suggestion == null ? null : suggestion.getBankItems(),
                                result == null ? null : result.getPortfolioItems()
                        );
                        suggestionManager.setSuggestionNeeded(true);
                        int itemsUpdated = result == null || result.getPortfolioItems() == null ? 0 : result.getPortfolioItems().size();
                        log.info("{} succeeded for item_id={}, account_id={}, portfolio_items_updated={}", option, menuItem.unnotedItemId, accountId, itemsUpdated);
                    },
                    error -> log.warn("{} failed for item_id={}, account_id={}, status={}, message={}", option, menuItem.unnotedItemId, accountId, error.getResponseCode(), error.getResponseMessage())
            );
            return true;
        });
    }

    private boolean shouldAddInventoryPriceGraphEntry(MenuEntryAdded event) {
        if (!grandExchange.isOpen() || !event.getOption().equals("Examine")) {
            return false;
        }
        int widgetId = event.getActionParam1();
        Widget geInventoryWidget = client.getWidget(467, 0);
        if (geInventoryWidget != null && geInventoryWidget.getId() == widgetId) {
            return true;
        }
        Widget inventoryWidget = client.getWidget(149, 0);
        return inventoryWidget != null && inventoryWidget.getId() == widgetId;
    }

    private boolean isInventoryWidgetId(int widgetId) {
        Widget geInventoryWidget = client.getWidget(467, 0);
        if (geInventoryWidget != null && geInventoryWidget.getId() == widgetId) {
            return true;
        }
        Widget inventoryWidget = client.getWidget(149, 0);
        if (inventoryWidget != null && inventoryWidget.getId() == widgetId) {
            return true;
        }
        for (int childId : BANK_ITEM_CONTAINER_CHILDREN) {
            Widget bankItemWidget = client.getWidget(BANK_WIDGET_GROUP, childId);
            if (bankItemWidget != null && bankItemWidget.getId() == widgetId) {
                return true;
            }
        }
        Widget bankInventoryWidget = client.getWidget(BANK_INVENTORY_WIDGET_GROUP, BANK_INVENTORY_WIDGET_CHILD);
        if (bankInventoryWidget != null && bankInventoryWidget.getId() == widgetId) {
            return true;
        }
        return false;
    }

    private InventoryMenuItem getInventoryMenuItem(MenuEntryAdded event) {
        if (!"Examine".equals(event.getOption())) {
            return null;
        }

        int inventorySlot = event.getActionParam0();
        int inventoryWidgetId = event.getActionParam1();
        if (!isInventoryWidgetId(inventoryWidgetId)) {
            return null;
        }

        Widget inventoryWidget = client.getWidget(inventoryWidgetId);
        if (inventoryWidget == null || inventorySlot < 0) {
            return null;
        }
        Widget[] items = inventoryWidget.getDynamicChildren();
        if (items == null || inventorySlot >= items.length) {
            return null;
        }
        Widget itemWidget = items[inventorySlot];
        if (itemWidget == null || itemWidget.getItemId() <= 0) {
            return null;
        }

        int unnotedItemId = itemController.toUnnotedItemId(itemWidget.getItemId());
        ItemComposition itemComposition = client.getItemDefinition(unnotedItemId);
        String itemName = itemComposition.getName();
        String menuTarget = resolveMenuTarget(event.getTarget(), unnotedItemId);
        return new InventoryMenuItem(unnotedItemId, itemName, menuTarget);
    }

    private static class InventoryMenuItem {
        private final int unnotedItemId;
        private final String itemName;
        private final String menuTarget;

        private InventoryMenuItem(int unnotedItemId, String itemName, String menuTarget) {
            this.unnotedItemId = unnotedItemId;
            this.itemName = itemName;
            this.menuTarget = menuTarget;
        }
    }

    private boolean isGeTradableItem(int itemId) {
        ItemComposition item = client.getItemDefinition(itemId);
        if (item.isTradeable()) {
            return true;
        }
        if (item.getNote() != -1) {
            int unnotedItemId = item.getLinkedNoteId();
            if (unnotedItemId > 0) {
                ItemComposition unnoted = client.getItemDefinition(unnotedItemId);
                return unnoted.isTradeable();
            }
        }
        return false;
    }

    private String resolveMenuTarget(String eventTarget, int itemId) {
        if (eventTarget != null && !eventTarget.isBlank()) {
            return eventTarget;
        }
        ItemComposition item = client.getItemDefinition(itemId);
        return "<col=ff9040>" + item.getName() + "</col>";
    }

    private int toUnnotedItemId(int itemId) {
        ItemComposition item = client.getItemDefinition(itemId);
        if (item.getNote() != -1 && item.getLinkedNoteId() > 0) {
            return item.getLinkedNoteId();
        }
        return itemId;
    }

    private PriceLine buildPriceLine(GrandExchangeOffer offer) {
        switch (offer.getState()) {
            case BOUGHT:
            case BUYING:
                return new PriceLine(
                        offer.getPrice(),
                        "offer buy price",
                        false
                );
            case SOLD :
            case SELLING:
                return new PriceLine(
                        offer.getPrice(),
                        "offer sell price",
                        true
                );
        }
        return null;
    }

    public void injectConfirmMenuEntry(MenuEntryAdded event) {
        if(!config.disableLeftClickConfirm()) {
            return;
        }

        if (!grandExchange.isOpen()) {
            return;
        }

        if(offerDetailsCorrect()) {
            return;
        }

        if(event.getOption().equals("Confirm") && grandExchange.isSlotOpen()) {
            log.debug("Adding deprioritized menu entry for offer");
            client.getMenu()
                    .createMenuEntry(-1)
                    .setOption("Nothing");

            event.getMenuEntry().setDeprioritized(true);
        }
    }

    public void injectSlotActionSwapMenuEntry(MenuEntryAdded event) {
        if (!config.slotActionSwap()) {
            return;
        }

        if (!grandExchange.isOpen() || grandExchange.isSlotOpen()) {
            return;
        }

        Suggestion suggestion = suggestionManager.getSuggestion();
        if (suggestion == null) {
            return;
        }

        if (!suggestion.isAbortSuggestion() && !suggestion.isModifySuggestion()) {
            return;
        }

        Widget slotWidget = grandExchange.getSlotWidget(suggestion.getBoxId());
        if (slotWidget == null || slotWidget.getId() != event.getActionParam1()) {
            return;
        }

        String target = suggestion.isAbortSuggestion() ? "Abort offer" : "Modify offer";
        if (! event.getOption().equals(target)) {
            event.getMenuEntry().setDeprioritized(true);
        }
    }

    private boolean offerDetailsCorrect() {
        Suggestion suggestion = suggestionManager.getSuggestion();
        if (suggestion == null) {
            return false;
        }
        String offerType = grandExchange.isOfferTypeSell() ? "sell" : "buy";
        if (client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH) == suggestion.getItemId() && offerType.equals(suggestion.offerType())) {
            return grandExchange.getOfferPrice() == suggestion.getPrice()
                    && grandExchange.getOfferQuantity() == suggestion.getQuantity();
        } else if (client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH) == offerManager.getViewedSlotItemId()
                && offerManager.getViewedSlotItemPrice() > 0) {
            return grandExchange.getOfferPrice() == offerManager.getViewedSlotItemPrice();
        }
        return false;
    }
}
