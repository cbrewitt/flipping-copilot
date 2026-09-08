package copilot.controller;

import copilot.rs.*;
import copilot.config.*;
import copilot.model.*;
import copilot.ui.flipsdialog.*;
import copilot.ui.graph.model.*;
import lombok.*;
import lombok.extern.slf4j.*;
import net.runelite.client.callback.*;
import net.runelite.client.game.chatbox.*;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.*;
import net.runelite.api.widgets.*;

import javax.inject.*;
import java.util.function.*;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class MenuHandler {

    private static final int BANK_WIDGET_GROUP = 12;
    private static final int[] BANK_ITEM_CONTAINER_CHILDREN = {12, 13, 89};
    private static final int BANK_INVENTORY_WIDGET_GROUP = 15, BANK_INVENTORY_WIDGET_CHILD = 3;

    private final CopilotConfig config;
    private final Client client;
    private final Offers offerManager;
    private final GrandExchange grandExchange;
    private final Suggestions suggestions;
    private final FlipsDialogController dialogs;
    private final Items itemController;
    private final PortfolioController portfolioController;
    private final CopilotLoginController copilotLoginController;
    private final ApiClient apiRequestHandler;
    private final PortfolioStateRS portfolioStateRS;
    private final BankStateRS bankStateRS;
    private final ClientThread clientThread;
    private final PlayerLocationController playerLocationController;
    private final ChatboxPanelManager chatboxPanelManager;

    private static final String MENU_ADD = "Add-All to portfolio", MENU_ADD_X = "Add-X to portfolio";
    private static final String MENU_REMOVE = "Remove-All from portfolio";
    private static final String MENU_REMOVE_X = "Remove-X from portfolio";

    private enum MenuLocation { INVENTORY, BANK }

    public void injectCopilotPriceGraphMenuEntry(MenuEntryAdded event) {
        if (!playerLocationController.isNearGE()) { return; }

        if (!config.priceGraphMenuOptionEnabled()) { return; }
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
                                var priceLine = buildPriceLine(offers[i]);
                                dialogs.showPriceGraphTab(itemId, false, priceLine);
                                log.debug("matched widget to slot {}, item {}", i, offers[i].getItemId());
                            }
                        }
                    });
        } else if (shouldAddInventoryPriceGraphEntry(event)) {
            int inventorySlot = event.getActionParam0(), inventoryWidgetId = event.getActionParam1();
            Widget inventoryWidget = client.getWidget(inventoryWidgetId);
            if (inventoryWidget == null || inventorySlot < 0) { return; }
            Widget[] items = inventoryWidget.getDynamicChildren();
            if (items == null || inventorySlot >= items.length) { return; }
            Widget itemWidget = items[inventorySlot];
            if (itemWidget == null || itemWidget.getItemId() <= 0) { return; }
            int itemId = itemWidget.getItemId();
            if (!isGeTradableItem(itemId)) { return; }
            String menuTarget = resolveMenuTarget(event.getTarget(), itemId);
            int graphItemId = toUnnotedItemId(itemId);
            client.getMenu()
                    .createMenuEntry(-1)
                    .setOption("Copilot graph")
                    .setTarget(menuTarget)
                    .onClick((MenuEntry e) -> dialogs.showPriceGraphTab(graphItemId, false, null));
        }
    }

    public void injectInventoryPortfolioMenuEntry(MenuEntryAdded event) {
        if (!playerLocationController.isNearGE()) { return; }

        var menuItem = getInventoryMenuItem(event);
        if (menuItem == null) { return; }
        if (!portfolioStateRS.get().loaded) { return; }

        Integer accountId = copilotLoginController.getActiveAccountId();
        var cardData = portfolioStateRS.get().itemCardDataByItemId.get(menuItem.unnotedItemId);

        int notInPortfolio = cardData == null ? 0 : cardData.getNotInPortfolioQuantity();
        int portfolioQty = cardData == null ? 0 : cardData.portfolioQuantity;
        int locationQty = getLocationQuantity(menuItem.unnotedItemId, menuItem.location);
        boolean showAdd = locationQty > 0 && (cardData == null || !cardData.isInPortfolio() || notInPortfolio > 0);
        boolean showRemove = locationQty > 0 && portfolioQty > 0;

        // Menu entries are added in reverse display order (last added = top of menu)

        // Remove X — cross-location custom amount, when portfolioQuantity > 1
        if (portfolioQty > 1) {
            addPortfolioMenuEntry(MENU_REMOVE_X, menuItem,
                    e -> promptQuantityAndToggle(menuItem, accountId, PortfolioRequest.REMOVE, "Enter quantity to remove:"));
        }

        // Remove (location-scoped) — removes only the qty present at the clicked location
        if (showRemove) {
            addPortfolioMenuEntry(MENU_REMOVE, menuItem,
                    e -> onTogglePortfolioClicked(menuItem, accountId, PortfolioRequest.REMOVE, locationQty));
        }

        // Add X — cross-location custom amount, when notInPortfolio > 1
        if (notInPortfolio > 1) {
            addPortfolioMenuEntry(MENU_ADD_X, menuItem,
                    e -> promptQuantityAndToggle(menuItem, accountId, PortfolioId.COFLIP_PORTFOLIO, "Enter quantity to add:"));
        }

        // Add (location-scoped) — adds only the qty present at the clicked location
        if (showAdd) {
            addPortfolioMenuEntry(MENU_ADD, menuItem,
                    e -> onTogglePortfolioClicked(menuItem, accountId, PortfolioId.COFLIP_PORTFOLIO, locationQty));
        }
    }

    private void addPortfolioMenuEntry(String option, InventoryMenuItem menuItem, Consumer<MenuEntry> onClick) {
        client.getMenu()
                .createMenuEntry(-1)
                .setOption(option)
                .setTarget(menuItem.menuTarget)
                .onClick(onClick);
    }

    private int getLocationQuantity(int itemId, MenuLocation location) {
        if (location == MenuLocation.INVENTORY) {
            var inv = itemController.getRunliteInventory();
            return inv == null ? 0 : Math.max(0, inv.getOrDefault(itemId, 0));
        }
        if (location == MenuLocation.BANK) {
            if (!bankStateRS.get().loaded) { return 0; }
            var bank = bankStateRS.get().items;
            return bank == null ? 0 : Math.max(0, bank.getOrDefault(itemId, 0));
        }
        return 0;
    }

    private void promptQuantityAndToggle(InventoryMenuItem menuItem, Integer accountId, int portfolioId, String prompt) {
        chatboxPanelManager.openTextInput(prompt)
                .charValidator(c -> c >= '0' && c <= '9')
                .onDone((String input) -> {
                    if (input == null || input.isEmpty()) { return; }
                    try {
                        int qty = Integer.parseInt(input);
                        if (qty > 0) { onTogglePortfolioClicked(menuItem, accountId, portfolioId, qty); }
                    } catch (NumberFormatException ignored) {
                    }
                })
                .build();
    }

    private void onTogglePortfolioClicked(InventoryMenuItem menuItem, Integer accountId, int portfolioId, int quantity) {
        if (accountId == null) { return; }
        clientThread.invokeLater(() -> {
            var runeliteInventory = itemController.getRunliteInventory();
            int bagQuantity = runeliteInventory == null ? 0 : Math.max(0, runeliteInventory.getOrDefault(menuItem.unnotedItemId, 0));

            int bankQuantity;
            if (bankStateRS.get().loaded) {
                bankQuantity = Math.max(0, bankStateRS.get().items.getOrDefault(menuItem.unnotedItemId, 0));
            } else {
                bankQuantity = -1;
            }

            var request = new PortfolioRequest(accountId, menuItem.unnotedItemId, portfolioId, bagQuantity, bankQuantity, quantity);
            apiRequestHandler.toggleItemPortfolioAsync(
                    request,
                    (userId, result) -> {
                        portfolioStateRS.updatePortfolioState(suggestions.getSuggestion(), result);
                        suggestions.setSuggestionNeeded(true);
                        int itemsUpdated = result == null || result.getPortfolioItems() == null ? 0 : result.getPortfolioItems().size();
                        log.info("toggle portfolio succeeded for item_id={}, account_id={}, portfolio_id={}, quantity={}, portfolio_items_updated={}", menuItem.unnotedItemId, accountId, portfolioId, quantity, itemsUpdated);
                    },
                    error -> log.warn("toggle portfolio failed for item_id={}, account_id={}, portfolio_id={}, quantity={}, status={}, message={}", menuItem.unnotedItemId, accountId, portfolioId, quantity, error.responseCode, error.responseMessage)
            );
            return true;
        });
    }

    private boolean shouldAddInventoryPriceGraphEntry(MenuEntryAdded event) {
        if (!grandExchange.isOpen() || !event.getOption().equals("Examine")) { return false; }
        int widgetId = event.getActionParam1();
        Widget geInventoryWidget = client.getWidget(467, 0);
        if (geInventoryWidget != null && geInventoryWidget.getId() == widgetId) { return true; }
        Widget inventoryWidget = client.getWidget(149, 0);
        return inventoryWidget != null && inventoryWidget.getId() == widgetId;
    }

    private MenuLocation getMenuLocation(int widgetId) {
        Widget geInventoryWidget = client.getWidget(467, 0);
        if (geInventoryWidget != null && geInventoryWidget.getId() == widgetId) { return MenuLocation.INVENTORY; }
        Widget inventoryWidget = client.getWidget(149, 0);
        if (inventoryWidget != null && inventoryWidget.getId() == widgetId) { return MenuLocation.INVENTORY; }
        Widget bankInventoryWidget = client.getWidget(BANK_INVENTORY_WIDGET_GROUP, BANK_INVENTORY_WIDGET_CHILD);
        if (bankInventoryWidget != null && bankInventoryWidget.getId() == widgetId) { return MenuLocation.INVENTORY; }
        for (int childId : BANK_ITEM_CONTAINER_CHILDREN) {
            Widget bankItemWidget = client.getWidget(BANK_WIDGET_GROUP, childId);
            if (bankItemWidget != null && bankItemWidget.getId() == widgetId) { return MenuLocation.BANK; }
        }
        return null;
    }

    private InventoryMenuItem getInventoryMenuItem(MenuEntryAdded event) {
        if (!"Examine".equals(event.getOption())) { return null; }

        int inventorySlot = event.getActionParam0(), inventoryWidgetId = event.getActionParam1();
        var location = getMenuLocation(inventoryWidgetId);
        if (location == null) { return null; }

        Widget inventoryWidget = client.getWidget(inventoryWidgetId);
        if (inventoryWidget == null || inventorySlot < 0) { return null; }
        Widget[] items = inventoryWidget.getDynamicChildren();
        if (items == null || inventorySlot >= items.length) { return null; }
        Widget itemWidget = items[inventorySlot];
        if (itemWidget == null || itemWidget.getItemId() <= 0) { return null; }

        int unnotedItemId = itemController.toUnnotedItemId(itemWidget.getItemId());
        var itemComposition = client.getItemDefinition(unnotedItemId);
        String itemName = itemComposition.getName();
        String menuTarget = resolveMenuTarget(event.getTarget(), unnotedItemId);
        return new InventoryMenuItem(unnotedItemId, itemName, menuTarget, location);
    }

    @lombok.RequiredArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    private static class InventoryMenuItem {
        private final int unnotedItemId;
        private final String itemName, menuTarget;
        private final MenuLocation location;

    }

    private boolean isGeTradableItem(int itemId) {
        var item = client.getItemDefinition(itemId);
        if (item.isGeTradeable()) { return true; }
        if (item.getNote() != -1) {
            int unnotedItemId = item.getLinkedNoteId();
            if (unnotedItemId > 0) {
                var unnoted = client.getItemDefinition(unnotedItemId);
                return unnoted.isGeTradeable();
            }
        }
        return false;
    }

    private String resolveMenuTarget(String eventTarget, int itemId) {
        if (eventTarget != null && !eventTarget.isBlank()) { return eventTarget; }
        var item = client.getItemDefinition(itemId);
        return "<col=ff9040>" + item.getName() + "</col>";
    }

    private int toUnnotedItemId(int itemId) {
        var item = client.getItemDefinition(itemId);
        if (item.getNote() != -1 && item.getLinkedNoteId() > 0) { return item.getLinkedNoteId(); }
        return itemId;
    }

    private PriceLine buildPriceLine(GrandExchangeOffer offer) {
        switch (offer.getState()) {
            case BOUGHT:
            case BUYING:
                return new PriceLine(offer.getPrice(), "offer buy price", false );
            case SOLD :
            case SELLING:
                return new PriceLine(offer.getPrice(), "offer sell price", true );
        }
        return null;
    }

    public void injectConfirmMenuEntry(MenuEntryAdded event) {
        if (!config.disableLeftClickConfirm()) { return; }

        if (!grandExchange.isOpen()) { return; }

        if (offerDetailsCorrect()) { return; }

        if(event.getOption().equals("Confirm") && grandExchange.isSlotOpen()) {
            log.debug("Adding deprioritized menu entry for offer");
            client.getMenu()
                    .createMenuEntry(-1)
                    .setOption("Nothing");

            event.getMenuEntry().setDeprioritized(true);
        }
    }

    public void injectSlotActionSwapMenuEntry(MenuEntryAdded event) {
        if (!config.slotActionSwap()) { return; }

        if (!grandExchange.isOpen() || grandExchange.isSlotOpen()) { return; }

        Suggestion suggestion = suggestions.getSuggestion();
        if (suggestion == null) { return; }

        if (!suggestion.isAbortSuggestion() && !suggestion.isModifySuggestion()) { return; }

        Widget slotWidget = grandExchange.getSlotWidget(suggestion.boxId);
        if (slotWidget == null || slotWidget.getId() != event.getActionParam1()) { return; }

        String target = suggestion.isAbortSuggestion() ? "Abort offer" : "Modify offer";
        if (! event.getOption().equals(target)) { event.getMenuEntry().setDeprioritized(true); }
    }

    private boolean offerDetailsCorrect() {
        Suggestion suggestion = suggestions.getSuggestion();
        if (suggestion == null) { return false; }
        String offerType = grandExchange.isOfferTypeSell() ? "sell" : "buy";
        if (client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH) == suggestion.itemId && offerType.equals(suggestion.offerType())) {
            return grandExchange.getOfferPrice() == suggestion.price
                    && grandExchange.getOfferQuantity() == suggestion.quantity;
        } else if (client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH) == offerManager.getViewedSlotItemId()
                && offerManager.getViewedSlotItemPrice() > 0) {
            return grandExchange.getOfferPrice() == offerManager.getViewedSlotItemPrice();
        }
        return false;
    }
}
