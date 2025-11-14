package com.flippingcopilot.ui;

import com.flippingcopilot.controller.FlippingCopilotConfig;
import com.flippingcopilot.controller.FlippingCopilotPlugin;
import com.flippingcopilot.manager.CopilotLoginManager;
import com.flippingcopilot.model.*;
import com.flippingcopilot.util.ProfitabilityChecker;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;

/**
 * Manages colored borders for GE slots based on profitability.
 * Uses tracked flip data to determine if sell offers are profitable.
 */
@Slf4j
@Singleton
public class SlotStateDrawer {

    private final Client client;
    private final FlippingCopilotPlugin plugin;
    private final OfferManager offerManager;
    private final OsrsLoginManager osrsLoginManager;
    private final CopilotLoginManager copilotLoginManager;
    private final FlipManager flipManager;

    @Inject
    public SlotStateDrawer(Client client, FlippingCopilotPlugin plugin, OfferManager offerManager,
                           OsrsLoginManager osrsLoginManager, CopilotLoginManager copilotLoginManager,
                           FlipManager flipManager) {
        this.client = client;
        this.plugin = plugin;
        this.offerManager = offerManager;
        this.osrsLoginManager = osrsLoginManager;
        this.copilotLoginManager = copilotLoginManager;
        this.flipManager = flipManager;
    }

    private Widget[] slotWidgets = new Widget[8];

    /**
     * Sets the slot widgets. Should be called when GE interface is opened/refreshed.
     */
    public void setSlotWidgets(Widget[] widgets) {
        if (widgets != null && widgets.length == 8) {
            this.slotWidgets = widgets;
        }
    }

    /**
     * Main entry point to refresh all slot visuals.
     */
    public void refreshSlotVisuals() {
        if (!shouldEnhanceSlots()) {
            return;
        }

        Long accountHash = client.getAccountHash();
        if (accountHash == null) {
            return;
        }

        // Refresh each slot
        for (int slotIndex = 0; slotIndex < 8; slotIndex++) {
            Widget slotWidget = getSlotWidget(slotIndex);
            if (slotWidget == null) {
                continue;
            }

            SavedOffer offer = offerManager.loadOffer(accountHash, slotIndex);
            if (offer == null || offer.getState() == net.runelite.api.GrandExchangeOfferState.EMPTY) {
                resetSlot(slotIndex);
                continue;
            }

            // Get profitability state
            ProfitableState state = determineProfitability(offer);
            
            // Draw the colored border
            drawOnSlot(slotIndex, state);
        }
    }

    /**
     * Determines if slot enhancements should be shown.
     */
    private boolean shouldEnhanceSlots() {
        FlippingCopilotConfig config = plugin.getConfig();
        
        if (slotWidgets == null) {
            return false;
        }

        if (osrsLoginManager.getPlayerDisplayName() == null) {
            return false;
        }

        if (!config.coloredSlotsEnabled()) {
            return false;
        }

        return true;
    }

    /**
     * Gets the widget for a specific slot.
     */
    private Widget getSlotWidget(int slotIndex) {
        // Try cached widget first
        if (slotWidgets[slotIndex] != null) {
            return slotWidgets[slotIndex];
        }

        // Reload from client
        Widget widget = client.getWidget(465, 7 + slotIndex);
        if (widget != null) {
            slotWidgets[slotIndex] = widget;
        }
        return widget;
    }

    /**
     * Determines the profitability of an offer based on tracked flip data.
     */
    private ProfitableState determineProfitability(SavedOffer offer) {
        if (offer == null) {
            return ProfitableState.UNKNOWN;
        }

        // Buy offers are always shown as blue
        if (offer.getOfferStatus() == OfferStatus.BUY) {
            return ProfitableState.PROFITABLE;
        }

        // For sell offers, check profitability
        String displayName = osrsLoginManager.getPlayerDisplayName();
        if (displayName == null) {
            return ProfitableState.UNKNOWN;
        }

        Integer accountId = copilotLoginManager.getAccountId(displayName);
        if (accountId == null || accountId == -1) {
            return ProfitableState.UNKNOWN;
        }

        // Get the tracked flip for this item
        int itemId = offer.getItemId();
        FlipV2 flip = flipManager.getLastFlipByItemId(accountId, itemId);

        // Use the profitability checker for sell offers
        // Returns PROFITABLE/NOT_PROFITABLE/UNKNOWN based on tracked flip data
        return ProfitabilityChecker.checkProfitability(offer, flip, itemId);
    }

    /**
     * Draws colored borders on a slot.
     */
    private void drawOnSlot(int slotIndex, ProfitableState state) {
        Widget slotWidget = getSlotWidget(slotIndex);
        if (slotWidget == null) {
            return;
        }

        Map<Integer, Integer> spriteMap = getSpriteMapForState(state);
        
        // Apply sprite IDs to all border components
        for (int childIdx : GeSpriteLoader.DYNAMIC_CHILDREN_IDXS) {
            Widget child = slotWidget.getChild(childIdx);
            if (child != null && spriteMap.containsKey(childIdx)) {
                int spriteId = spriteMap.get(childIdx);
                if (spriteId != -1) {  // -1 means don't change
                    child.setSpriteId(spriteId);
                }
            }
        }
    }

    /**
     * Resets a slot to default appearance.
     */
    public void resetSlot(int slotIndex) {
        Widget slotWidget = getSlotWidget(slotIndex);
        if (slotWidget == null) {
            return;
        }

        Map<Integer, Integer> spriteMap = GeSpriteLoader.CHILDREN_IDX_TO_DEFAULT_SPRITE_ID;
        
        for (int childIdx : GeSpriteLoader.DYNAMIC_CHILDREN_IDXS) {
            Widget child = slotWidget.getChild(childIdx);
            if (child != null && spriteMap.containsKey(childIdx)) {
                int spriteId = spriteMap.get(childIdx);
                if (spriteId != -1) {
                    child.setSpriteId(spriteId);
                }
            }
        }
    }

    /**
     * Resets all slots to default.
     */
    public void resetAllSlots() {
        for (int i = 0; i < 8; i++) {
            resetSlot(i);
        }
    }

    /**
     * Gets the sprite ID map for a given profitability state.
     */
    private Map<Integer, Integer> getSpriteMapForState(ProfitableState state) {
        switch (state) {
            case PROFITABLE:
                return GeSpriteLoader.CHILDREN_IDX_TO_BLUE_SPRITE_ID;
            case NOT_PROFITABLE:
                return GeSpriteLoader.CHILDREN_IDX_TO_RED_SPRITE_ID;
            case UNKNOWN:
            default:
                return GeSpriteLoader.CHILDREN_IDX_TO_YELLOW_SPRITE_ID;
        }
    }
}
