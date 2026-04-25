package com.flippingcopilot.controller;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.model.*;
import com.flippingcopilot.ui.NpcHighlightOverlay;
import com.flippingcopilot.ui.WidgetHighlightOverlay;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.VarClientStr;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;



@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class HighlightController {
    private static final int BANK_WIDGET_GROUP = 12;
    private static final int[] BANK_ITEM_CONTAINER_CHILDREN = {12, 13, 89};
    private static final String PORTFOLIO_BANK_TAG = "portfolio";
    private static final int BANK_TAG_TAB_CHILD_OFFSET = 4;
    private static final int BANK_CLOSE_BUTTON_INDEX = 11;
    private static final int GE_CLOSE_BUTTON_INDEX = 11;
    private static final Rectangle CLOSE_BUTTON_HIGHLIGHT_BOUNDS = new Rectangle(2, 2, 19, 19);
    private static final String GE_CLERK_NAME = "Grand Exchange Clerk";
    private static final String BANKER_NAME = "Banker";

    // dependencies
    private final FlippingCopilotConfig config;
    private final SuggestionManager suggestionManager;
    private final SuggestionPreferencesManager suggestionPreferencesManager;
    private final GrandExchange grandExchange;
    private final AccountStatusManager accountStatusManager;
    private final Client client;
    private final OfferManager offerManager;
    private final OverlayManager overlayManager;
    private final HighlightColorController highlightColorController;
    private final PluginManager pluginManager;
    private final BankTagsPlugin bankTagsPlugin;
    private final BankTagsService bankTagsService;
    private final ModelOutlineRenderer modelOutlineRenderer;

    // state
    private final ArrayList<Overlay> highlightOverlays = new ArrayList<>();
    private volatile boolean active = true;
    private final AtomicInteger generation = new AtomicInteger(0);

    public void activate() {
        active = true;
        generation.incrementAndGet();
    }

    public void deactivateAndRemoveAll() {
        active = false;
        final int clearGeneration = generation.incrementAndGet();
        Runnable clearTask = () -> clearOverlaysIfCurrentGeneration(clearGeneration);
        if (SwingUtilities.isEventDispatchThread()) {
            clearTask.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(clearTask);
        } catch (Exception e) {
            SwingUtilities.invokeLater(clearTask);
        }
    }

    public void redraw() {
        if (!active) {
            return;
        }
        removeAll();
        if (!active) {
            return;
        }
        if(!config.suggestionHighlights()) {
            return;
        }
        if (offerManager.isOfferJustPlaced()) {
            return;
        }
        if(suggestionManager.getSuggestionError() != null) {
            return;
        }
        Suggestion suggestion = suggestionManager.getSuggestion();
        if (suggestion == null) {
            return;
        }
        AccountStatus accountStatus = accountStatusManager.getAccountStatus();
        boolean sellFromBank = accountStatus != null && accountStatus.shouldSellFromBank(suggestion);
        // When the user must collect first (e.g. no free slot for the sell-from-bank offer),
        // sending them to the bank is wrong — they have to go to the GE clerk first.
        boolean isCollectNeeded = accountStatus != null && accountStatus.isCollectNeeded(suggestion, grandExchange.isSetupOfferOpen());
        boolean goToBank = sellFromBank && !isCollectNeeded;
        if (goToBank && grandExchange.isOpen() && highlightGrandExchangeCloseButton(suggestion)) {
            return;
        }
        if (!goToBank && isBankOpen() && !suggestion.isWaitSuggestion() && highlightBankCloseButton(suggestion)) {
            return;
        }
        if (goToBank && drawSellFromBankHighlight(suggestion, accountStatus)) {
            return;
        }
        if(!grandExchange.isOpen()) {
            if (!isBankOpen()) {
                highlightNpcAtGrandExchange(suggestion, accountStatus, goToBank);
            }
            return;
        }
        if (grandExchange.isHomeScreenOpen()) {
            drawHomeScreenHighLights(suggestion);
        } else if (grandExchange.isSlotOpen()) {
            drawOfferScreenHighlights(suggestion);
        }
    }

    private void highlightNpcAtGrandExchange(Suggestion suggestion, AccountStatus accountStatus, boolean goToBank) {
        if (accountStatus == null) {
            return;
        }
        // Player is considered at the GE only if a clerk is loaded nearby
        NPC clerk = findClosestNpcByName(GE_CLERK_NAME);
        if (clerk == null) {
            return;
        }
        NPC target;
        if (goToBank) {
            target = findClosestNpcByName(BANKER_NAME);
        } else if (drawHomeScreenHighLights(suggestion)) {
            // Reuses the existing home-screen decision tree as the actionability check;
            // widget lookups inside no-op while the GE is closed
            target = clerk;
        } else {
            return;
        }
        if (target == null) {
            return;
        }
        boolean isDumpSuggestion = suggestion.isDumpSuggestion();
        addNpcHighlight(target, () -> {
            Color base = highlightColorController.getBlueColor(isDumpSuggestion);
            if (base == null) {
                return null;
            }
            // Feathered outlines need a higher alpha than filled widget overlays to read as a glow
            return new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.min(255, base.getAlpha() * 3));
        });
    }

    private NPC findClosestNpcByName(String name) {
        Player player = client.getLocalPlayer();
        if (player == null) {
            return null;
        }
        WorldPoint playerLocation = player.getWorldLocation();
        if (playerLocation == null) {
            return null;
        }
        NPC closest = null;
        int closestDistance = Integer.MAX_VALUE;
        for (NPC npc : client.getNpcs()) {
            if (npc == null || !name.equals(npc.getName())) {
                continue;
            }
            WorldPoint npcLocation = npc.getWorldLocation();
            if (npcLocation == null) {
                continue;
            }
            int distance = npcLocation.distanceTo(playerLocation);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = npc;
            }
        }
        return closest;
    }

    private void addNpcHighlight(NPC npc, Supplier<Color> colorSupplier) {
        if (!active || npc == null) {
            return;
        }
        final int addGeneration = generation.get();
        SwingUtilities.invokeLater(() -> {
            if (!active || generation.get() != addGeneration) {
                return;
            }
            NpcHighlightOverlay overlay = new NpcHighlightOverlay(npc, colorSupplier, modelOutlineRenderer);
            highlightOverlays.add(overlay);
            overlayManager.add(overlay);
        });
    }

    private boolean drawHomeScreenHighLights(Suggestion suggestion) {
        boolean isDumpSuggestion = suggestion.isDumpSuggestion();
        Supplier<Color> blueHighlight = () -> highlightColorController.getBlueColor(isDumpSuggestion);
        Supplier<Color> redHighlight = () -> highlightColorController.getRedColor(isDumpSuggestion);
        Supplier<Color> amberHighlight = () -> highlightColorController.getAmberColor(isDumpSuggestion);
        AccountStatus accountStatus = accountStatusManager.getAccountStatus();
        if (accountStatus.isCollectNeeded(suggestion, grandExchange.isSetupOfferOpen())) {
            Widget collectButton = grandExchange.getCollectButton();
            if (collectButton != null) {
                add(collectButton, blueHighlight, new Rectangle(2, 1, 81, 18));
            }
            return true;
        }
        else if (suggestion.isAbortSuggestion()) {
            Widget slotWidget = grandExchange.getSlotWidget(suggestion.getBoxId());
            add(slotWidget, redHighlight);
            return true;
        }
        else if (suggestion.isModifySuggestion()) {
            Widget slotWidget = grandExchange.getSlotWidget(suggestion.getBoxId());
            if (slotWidget != null && !slotWidget.isHidden()) {
                add(slotWidget, amberHighlight);
            }
            return true;
        }
        else if (isScanningForDumpsSuggested(suggestion, accountStatus)) {
            highlightCreateBuyOfferButton(accountStatus, blueHighlight);
            return true;
        }
        else if (suggestion.isBuySuggestion()) {
            highlightCreateBuyOfferButton(accountStatus, blueHighlight);
            return true;
        }
        else if (suggestion.isSellSuggestion() && accountStatus.hasSufficientInventoryForSellSuggestion(suggestion)) {
            Widget itemWidget = getInventoryItemWidget(suggestion.getItemId());
            if (itemWidget != null && !itemWidget.isHidden()) {
                add(itemWidget, blueHighlight, new Rectangle(0, 0, 34, 32));
            }
            return true;
        }
        return false;
    }

    private boolean drawSellFromBankHighlight(Suggestion suggestion, AccountStatus accountStatus) {
        boolean isDumpSuggestion = suggestion.isDumpSuggestion();
        Supplier<Color> blueHighlight = () -> highlightColorController.getBlueColor(isDumpSuggestion);

        Widget bankItemWidget = getBankItemWidget(suggestion.getItemId());
        if (bankItemWidget != null && !bankItemWidget.isHidden()) {
            add(bankItemWidget, blueHighlight, new Rectangle(0, 0, 34, 32));
            return true;
        }

        Widget portfolioTagButton = getPortfolioBankTagButton();
        if (portfolioTagButton != null) {
            add(portfolioTagButton, blueHighlight);
            return true;
        }

        return false;
    }

    private boolean highlightGrandExchangeCloseButton(Suggestion suggestion) {
        return highlightCloseButton(getGrandExchangeCloseButton(), suggestion);
    }

    private boolean highlightBankCloseButton(Suggestion suggestion) {
        return highlightCloseButton(getBankCloseButton(), suggestion);
    }

    private boolean highlightCloseButton(Widget closeButton, Suggestion suggestion) {
        if (closeButton == null || closeButton.isHidden()) {
            return false;
        }

        boolean isDumpSuggestion = suggestion.isDumpSuggestion();
        Supplier<Color> blueHighlight = () -> highlightColorController.getBlueColor(isDumpSuggestion);
        add(closeButton, blueHighlight, new Rectangle(CLOSE_BUTTON_HIGHLIGHT_BOUNDS));
        return true;
    }

    private boolean isScanningForDumpsSuggested(Suggestion suggestion, AccountStatus accountStatus) {
        return suggestion.isWaitSuggestion()
                && accountStatus.emptySlotExists()
                && !accountStatus.moreGpNeeded()
                && suggestionPreferencesManager.isReceiveDumpSuggestions();
    }

    private void highlightCreateBuyOfferButton(AccountStatus accountStatus, Supplier<Color> colorSupplier) {
        int slotId = accountStatus.findEmptySlot();
        if (slotId == -1) {
            return;
        }
        Widget buyButton = grandExchange.getBuyButton(slotId);
        if (buyButton != null && !buyButton.isHidden()) {
            add(buyButton, colorSupplier, new Rectangle(0, 0, 45, 44));
        }
    }

    private void drawOfferScreenHighlights(Suggestion suggestion) {
        boolean isDumpSuggestion = suggestion.isDumpSuggestion();
        Supplier<Color> blueHighlight = () -> highlightColorController.getBlueColor(isDumpSuggestion);
        GEOfferScreenSetupOfferState s = grandExchange.getOfferScreenSetupOfferState();
        if (s == null) {
            return;
        }

        if (s.offerDetailsCorrect(suggestion)) {
            highlightConfirm(blueHighlight);
            return;
        }

        boolean offerTypeMatches = Objects.equals(s.offerType, suggestion.offerType());
        boolean itemMatches = s.currentItemId == suggestion.getItemId();

        // Prioritise certain dump alert cases
        if (suggestion.isDumpSuggestion()) {
            if (!offerTypeMatches || accountStatusManager.getAccountStatus().isCollectNeeded(suggestion, grandExchange.isSetupOfferOpen())) {
                highlightBackButton(blueHighlight);
            } else if (!s.searchOpen && s.currentItemId != -1 && !itemMatches) {
                highlightItemSearchButton(blueHighlight);
            } else if (itemMatches) {
                if (s.offerPrice != suggestion.getPrice()) {
                    highlightPrice(blueHighlight);
                }
                highlightQuantity(suggestion, s.offerQuantity, blueHighlight);
            } else if (s.searchOpen) {
                highlightItemInSearch(suggestion, blueHighlight);
            }
            return;
        }

        // Custom item choice case
        if (isCustomChoiceItem(s, offerTypeMatches, itemMatches)) {
            if (s.offerPrice == offerManager.getViewedSlotItemPrice()) {
                highlightConfirm(blueHighlight);
            } else {
                highlightPrice(blueHighlight);
            }
            return;
        }

        // Standard suggestion case
        if (offerTypeMatches && itemMatches) {
            if (s.offerPrice != suggestion.getPrice()) {
                highlightPrice(blueHighlight);
            }
            highlightQuantity(suggestion, s.offerQuantity, blueHighlight);
            return;
        }
        if(offerTypeMatches && s.currentItemId == -1 && s.searchOpen) {
            highlightItemInSearch(suggestion,blueHighlight);
            return;
        }

        if (suggestion.isAbortSuggestion() || (suggestion.isSellSuggestion() && s.isEmptyBuyState())) {
            highlightBackButton(blueHighlight);
        }

    }

    private boolean isCustomChoiceItem(GEOfferScreenSetupOfferState s, boolean offerTypeMatches, boolean itemMatches) {
        return s.currentItemId != -1 && ((!offerTypeMatches && s.offerType.equals("sell"))|| (!s.searchOpen && !itemMatches))
                && s.currentItemId == offerManager.getViewedSlotItemId()
                && offerManager.getViewedSlotItemPrice() > -1;
    }

    private void highlightItemInSearch(Suggestion suggestion, Supplier<Color> colorSupplier) {
        if (!client.getVarcStrValue(VarClientStr.INPUT_TEXT).isEmpty()) {
            return;
        }
        Widget searchResults = client.getWidget(ComponentID.CHATBOX_GE_SEARCH_RESULTS);
        if (searchResults == null) {
            return;
        }
        for (Widget widget : searchResults.getDynamicChildren()) {
            if (widget.getName().equals("<col=ff9040>" + suggestion.getName() + "</col>")) {
                add(widget, colorSupplier);
                return;
            }
        }
        Widget itemWidget = searchResults.getChild(3);
        if (itemWidget != null && itemWidget.getItemId() == suggestion.getItemId()) {
            add(itemWidget, colorSupplier);
        }
    }


    private void highlightPrice(Supplier<Color> colorSupplier) {
        Widget setPriceButton = grandExchange.getSetPriceButton();
        if (setPriceButton != null) {
            add(setPriceButton, colorSupplier, new Rectangle(1, 6, 33, 23));
        }
    }

    private void highlightQuantity(Suggestion suggestion, int offerQuantity, Supplier<Color> colorSupplier) {
        AccountStatus accountStatus = accountStatusManager.getAccountStatus();
        if (offerQuantity != suggestion.getQuantity()) {
            Widget setQuantityButton;
            if (accountStatus.getInventory().getTotalAmount(suggestion.getItemId()) == suggestion.getQuantity()
                && suggestion.isSellSuggestion()) {
                setQuantityButton = grandExchange.getSetQuantityAllButton();
            } else {
                setQuantityButton = grandExchange.getSetQuantityButton();
            }
            if (setQuantityButton != null) {
                add(setQuantityButton, colorSupplier, new Rectangle(1, 6, 33, 23));
            }
        }
    }

    private void highlightConfirm(Supplier<Color> colorSupplier) {
        Widget confirmButton = grandExchange.getConfirmButton();
        if (confirmButton != null) {
            add(confirmButton, colorSupplier, new Rectangle(1, 1, 150, 38));
        }
    }

    private Widget getItemSearchButtonWidget() {
        Widget setupContainer = client.getWidget(465, 26);
        if (setupContainer == null) {
            return null;
        }
        Widget[] children = setupContainer.getChildren();
        if (children != null && children.length > 0) {
            return children[0];
        }
        return null;
    }

    private void highlightItemSearchButton(Supplier<Color> colorSupplier) {
        Widget searchWidget = getItemSearchButtonWidget();
        if (searchWidget != null) {
            add(searchWidget, colorSupplier);
        }
    }

    private Widget getAbortButtonWidget() {
        Widget statusContainer = client.getWidget(465, 23);
        if (statusContainer == null) {
            return null;
        }
        Widget[] children = statusContainer.getChildren();
        if (children != null && children.length > 0) {
            return children[0];
        }
        return null;
    }

    private void highlightAbortButton(Supplier<Color> colorSupplier) {
        Widget abortButton = getAbortButtonWidget();
        if (abortButton != null) {
            add(abortButton, colorSupplier);
        }
    }

    private void highlightBackButton(Supplier<Color> colorSupplier) {
        Widget backButton = grandExchange.getBackButton();
        if (backButton != null) {
            add(backButton, colorSupplier);
        }
    }

    private void add(Widget widget, Supplier<Color> colorSupplier, Rectangle adjustedBounds) {
        if (!active || widget == null) {
            return;
        }
        final int addGeneration = generation.get();
        SwingUtilities.invokeLater(() -> {
            if (!active || generation.get() != addGeneration) {
                return;
            }
            WidgetHighlightOverlay overlay = new WidgetHighlightOverlay(widget, colorSupplier, adjustedBounds);
            highlightOverlays.add(overlay);
            overlayManager.add(overlay);
        });
    }

    private void add(Widget widget, Supplier<Color> colorSupplier) {
        if (widget == null) {
            return;
        }
        add(widget, colorSupplier, new Rectangle(0, 0, widget.getWidth(), widget.getHeight()));
    }

    public void removeAll() {
        final int clearGeneration = generation.incrementAndGet();
        SwingUtilities.invokeLater(() -> {
            clearOverlaysIfCurrentGeneration(clearGeneration);
        });
    }

    private void clearOverlaysIfCurrentGeneration(int expectedGeneration) {
        if (generation.get() != expectedGeneration) {
            return;
        }
        highlightOverlays.forEach(overlayManager::remove);
        highlightOverlays.clear();
    }

    private Widget getInventoryItemWidget(int unnotedItemId) {
        // Only the GE-specific inventory widget is relevant here — the regular inventory
        // (149,0) is shown when the GE is closed, and we don't highlight items in that case.
        Widget inventory = client.getWidget(467, 0);
        if (inventory == null) {
            return null;
        }

        Widget[] children = inventory.getDynamicChildren();
        if (children == null) {
            return null;
        }

        Widget notedWidget = null;
        Widget unnotedWidget = null;

        for (Widget widget : children) {
            if (widget == null) {
                continue;
            }

            int itemId = widget.getItemId();
            ItemComposition itemComposition = client.getItemDefinition(itemId);

            if (itemComposition.getNote() != -1) {
                if (itemComposition.getLinkedNoteId() == unnotedItemId) {
                    notedWidget = widget;
                }
            } else if (itemId == unnotedItemId) {
                unnotedWidget = widget;
            }
        }
        return notedWidget != null ? notedWidget : unnotedWidget;
    }

    private Widget getBankItemWidget(int unnotedItemId) {
        for (int childId : BANK_ITEM_CONTAINER_CHILDREN) {
            Widget bankItems = client.getWidget(BANK_WIDGET_GROUP, childId);
            if (bankItems == null || bankItems.isHidden()) {
                continue;
            }

            Widget itemWidget = getVisibleItemWidget(bankItems, unnotedItemId);
            if (itemWidget != null) {
                return itemWidget;
            }
        }
        return null;
    }

    private boolean isBankOpen() {
        Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
        return bank != null && !bank.isHidden();
    }

    private Widget getBankCloseButton() {
        Widget frame = client.getWidget(InterfaceID.Bankmain.FRAME);
        if (frame == null || frame.getDynamicChildren() == null) {
            return null;
        }
        Widget[] children = frame.getDynamicChildren();
        if (children.length <= BANK_CLOSE_BUTTON_INDEX) {
            return null;
        }
        return children[BANK_CLOSE_BUTTON_INDEX];
    }

    private Widget getGrandExchangeCloseButton() {
        Widget frame = client.getWidget(InterfaceID.GeOffers.FRAME);
        if (frame == null || frame.getDynamicChildren() == null) {
            return null;
        }
        Widget[] children = frame.getDynamicChildren();
        if (children.length <= GE_CLOSE_BUTTON_INDEX) {
            return null;
        }
        return children[GE_CLOSE_BUTTON_INDEX];
    }

    private Widget getPortfolioBankTagButton() {
        if (!config.portfolioBankTag()
                || !pluginManager.isPluginActive(bankTagsPlugin)
                || PORTFOLIO_BANK_TAG.equals(bankTagsService.getActiveTag())) {
            return null;
        }

        Widget parent = client.getWidget(InterfaceID.Bankmain.ITEMS_CONTAINER);
        if (parent == null || parent.isHidden() || parent.getChildren() == null) {
            return null;
        }

        Widget[] children = parent.getChildren();
        for (int i = BANK_TAG_TAB_CHILD_OFFSET; i < children.length; i += 2) {
            Widget button = children[i];
            if (button == null || button.isHidden()) {
                continue;
            }

            String widgetName = button.getName();
            if (widgetName == null) {
                continue;
            }

            if (PORTFOLIO_BANK_TAG.equals(Text.removeTags(widgetName))) {
                return button;
            }
        }
        return null;
    }

    private Widget getVisibleItemWidget(Widget itemContainer, int unnotedItemId) {
        Widget[] children = itemContainer.getDynamicChildren();
        if (children == null) {
            return null;
        }

        Rectangle containerBounds = itemContainer.getBounds();
        boolean clipToContainerBounds = hasUsableBounds(containerBounds);
        for (Widget widget : children) {
            if (widget == null || widget.isHidden() || widget.getItemQuantity() <= 0) {
                continue;
            }

            if (!matchesItemId(widget.getItemId(), unnotedItemId)) {
                continue;
            }

            Rectangle bounds = widget.getBounds();
            if (clipToContainerBounds && (bounds == null || !containerBounds.intersects(bounds))) {
                continue;
            }

            return widget;
        }
        return null;
    }

    private boolean hasUsableBounds(Rectangle bounds) {
        return bounds != null && bounds.width > 0 && bounds.height > 0;
    }

    private boolean matchesItemId(int itemId, int unnotedItemId) {
        if (itemId == unnotedItemId) {
            return true;
        }
        if (itemId <= 0) {
            return false;
        }

        ItemComposition itemComposition = client.getItemDefinition(itemId);
        return itemComposition.getNote() != -1 && itemComposition.getLinkedNoteId() == unnotedItemId;
    }
}
