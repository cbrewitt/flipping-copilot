package com.flippingcopilot.controller;

import com.flippingcopilot.model.OfferManager;
import com.flippingcopilot.model.OfferStatus;
import com.flippingcopilot.model.SavedOffer;
import com.flippingcopilot.model.Suggestion;
import com.flippingcopilot.util.ProfitCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;

/**
 * Manages text color modifications for GE slot price widgets based on profitability.
 * Changes the price text color to indicate whether an offer is profitable or not.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SlotProfitColorizer {

    private static final int GE_SLOT_COUNT = 8;
    private static final int FIRST_SLOT_CHILD_ID = 7; // Slots are children 7-14 (0-7)
    private static final int PRICE_TEXT_WIDGET_CHILD_INDEX = 25; // Child index within each slot that contains price text
    private static final int DETAIL_VIEW_CHILD_ID = 15; // Detail view when a slot is opened
    private static final int DETAIL_PRICE_TEXT_CHILD_INDEX = 25; // Price text within detail view
    private static final int OFFER_SETUP_CHILD_ID = 26; // Offer setup screen (buy/sell)
    private static final int OFFER_SETUP_PRICE_TEXT_CHILD_INDEX = 41; // Price text within offer setup

    private static final String DEFAULT_COLOR = "ff981f"; // Game's default orange color for slot overview
    private static final String DETAIL_AND_SETUP_DEFAULT_COLOR = "ffb83f"; // Default color for detail view and offer setup

    private final Client client;
    private final FlippingCopilotConfig config;
    private final OfferManager offerManager;
    private final GrandExchange grandExchange;
    private final ProfitCalculator profitCalculator;

    /**
     * Helper method to load an offer for a given slot.
     *
     * @param slotIndex The slot index (0-7)
     * @return The SavedOffer, or null if not found
     */
    private SavedOffer getOffer(int slotIndex) {
        try {
            long accountHash = client.getAccountHash();
            return offerManager.loadOffer(accountHash, slotIndex);
        } catch (Exception e) {
            log.debug("Error loading offer for slot {}", slotIndex, e);
            return null;
        }
    }

    /**
     * Updates all GE slot price text colors based on current profitability.
     * Should be called when GE interface updates (scripts 782, 804).
     */
    public void updateAllSlots() {
        if (!config.slotPriceColorEnabled()) {
            return;
        }

        int openSlot = grandExchange.getOpenSlot();
        if (openSlot > -1) {
            updateDetailView(openSlot);
            return;
        }

        for (int slotIndex = 0; slotIndex < GE_SLOT_COUNT; slotIndex++) {
            updateOverviewSlot(slotIndex);
        }
    }

    /**
     * Updates a single slot's price text color based on profitability.
     *
     * @param slotIndex The slot index (0-7)
     */
    private void updateOverviewSlot(int slotIndex) {
        try {
            Widget slotWidget = getSlotWidget(slotIndex);
            Widget priceTextWidget = getPriceTextWidget(slotWidget);

            updatePriceTextColor(priceTextWidget, slotIndex, Color.decode("#" + DEFAULT_COLOR));
        } catch (Exception e) {
            log.error("Error updating slot {} price color", slotIndex, e);
        }
    }

    /**
     * Updates the detail view price text color when a slot is opened.
     * Widget path: 465.15[25]
     */
    private void updateDetailView(int openSlot) {
        if (openSlot == -1) {
            return;
        }

        try {
            Widget detailViewWidget = client.getWidget(InterfaceID.GE_OFFERS, DETAIL_VIEW_CHILD_ID);
            Widget priceTextWidget = detailViewWidget != null ? detailViewWidget.getChild(DETAIL_PRICE_TEXT_CHILD_INDEX) : null;

            updatePriceTextColor(priceTextWidget, openSlot, Color.decode("#" + DETAIL_AND_SETUP_DEFAULT_COLOR));
        } catch (Exception e) {
            log.debug("Error updating detail view price color", e);
        }
    }

    /**
     * Updates the price text color for a given widget based on slot profitability.
     *
     * @param priceTextWidget The widget containing the price text
     * @param slotIndex The slot index (0-7)
     * @param defaultColor The default color to use if not profitable/unprofitable
     */
    private void updatePriceTextColor(Widget priceTextWidget, int slotIndex, Color defaultColor) {
        if (!hasValidText(priceTextWidget)) {
            return;
        }

        Color targetColor = defaultColor;

        if (! isBuyOffer(slotIndex)) {
            targetColor = determineProfitColor(calculateSlotProfit(slotIndex), defaultColor);
        } else if (isOfferTracked(slotIndex)) {
            targetColor = config.slotPriceProfitableColor();
        }

        priceTextWidget.setText(applyColorTag(priceTextWidget.getText(), targetColor));
    }

    /**
     * Updates the offer setup screen price text color based on suggestion profit.
     * Should be called from HighlightController when highlighting suggestion fields.
     * Widget path: 465.26[41]
     *
     * @param suggestion The current suggestion (buy or sell)
     */
    public void colorOfferSetupPrice(Suggestion suggestion) {
        try {
            if (suggestion == null) {
                return;
            }

            Widget offerSetupWidget = client.getWidget(InterfaceID.GE_OFFERS, OFFER_SETUP_CHILD_ID);
            if (offerSetupWidget == null || offerSetupWidget.isHidden()) {
                return;
            }

            Widget priceTextWidget = offerSetupWidget.getChild(OFFER_SETUP_PRICE_TEXT_CHILD_INDEX);
            if (priceTextWidget == null) {
                return;
            }

            Color defaultColor = Color.decode("#" + DETAIL_AND_SETUP_DEFAULT_COLOR);
            Color color = determineOfferSetupColor(suggestion, defaultColor);

            priceTextWidget.setTextColor(color.getRGB() & 0xFFFFFF);
        } catch (Exception e) {
            log.debug("Error coloring offer setup price", e);
        }
    }

    /**
     * Determine the Offer Setup screen price text color based on suggestion profit.
     *
     * When buying, the price will be highlighted if it matches the suggestion price.
     * When selling, any item that will result in a profit will be shown as profitable.
     * When selling, any item that will result in a loss will be shown as unprofitable.
     * In all other cases, the default color is used.
     */
    private Color determineOfferSetupColor(Suggestion suggestion, Color defaultColor) {
        if (! grandExchange.isOfferTypeSell()) {
            if ("buy".equals(suggestion.getType()) && suggestion.getPrice() == grandExchange.getOfferPrice()) {
                return config.slotPriceProfitableColor();
            }

            return defaultColor;
        }

        Long profit = profitCalculator.calculateProfitPerItem(suggestion.getItemId(), grandExchange.getOfferPrice());

        return determineProfitColor(profit, defaultColor);
    }

    /**
     * Determine the color to apply based on profitability.
     */
    private Color determineProfitColor(Long profit, Color defaultColor) {
        if (profit == null || profit == 0) {
            return defaultColor;
        }

        if (profit < 0) {
            return config.slotPriceUnprofitableColor();
        } else {
            return config.slotPriceProfitableColor();
        }
    }

    /**
     * Resets all slots to their default color.
     * Called when the feature is disabled.
     */
    public void resetAllSlots() {
        for (int slotIndex = 0; slotIndex < GE_SLOT_COUNT; slotIndex++) {
            resetSlot(slotIndex);
        }

        resetDetailView();
        resetOfferSetup();
    }

    /**
     * Resets a single slot to default color.
     * 
     * @param slotIndex The slot index (0-7)
     */
    private void resetSlot(int slotIndex) {
        try {
            Widget slotWidget = getSlotWidget(slotIndex);
            Widget priceTextWidget = getPriceTextWidget(slotWidget);
            
            if (!hasValidText(priceTextWidget)) {
                return;
            }

            resetWidgetTextColor(priceTextWidget, DEFAULT_COLOR);
        } catch (Exception e) {
            log.error("Error resetting slot {} price color", slotIndex, e);
        }
    }

    /**
     * Resets the detail view to default color.
     */
    private void resetDetailView() {
        try {
            Widget detailViewWidget = client.getWidget(InterfaceID.GE_OFFERS, DETAIL_VIEW_CHILD_ID);
            Widget priceTextWidget = detailViewWidget != null ? detailViewWidget.getChild(DETAIL_PRICE_TEXT_CHILD_INDEX) : null;
            
            if (!hasValidText(priceTextWidget)) {
                return;
            }

            resetWidgetTextColor(priceTextWidget, DETAIL_AND_SETUP_DEFAULT_COLOR);
        } catch (Exception e) {
            log.debug("Error resetting detail view price color", e);
        }
    }

    /**
     * Resets the offer setup screen to default color.
     */
    private void resetOfferSetup() {
        try {
            Widget offerSetupWidget = client.getWidget(InterfaceID.GE_OFFERS, OFFER_SETUP_CHILD_ID);
            if (offerSetupWidget == null) {
                return;
            }

            Widget priceTextWidget = offerSetupWidget.getChild(OFFER_SETUP_PRICE_TEXT_CHILD_INDEX);
            if (priceTextWidget == null) {
                return;
            }

            priceTextWidget.setTextColor(Integer.parseInt(DETAIL_AND_SETUP_DEFAULT_COLOR, 16));
        } catch (Exception e) {
            log.debug("Error resetting offer setup price color", e);
        }
    }

    /**
     * Calculates the profit for a given slot.
     * Returns null if profit cannot be determined.
     * Only calculates profit for SELL offers.
     * 
     * @param slotIndex The slot index (0-7)
     * @return Profit in GP, or null if unknown
     */
    private Long calculateSlotProfit(int slotIndex) {
        try {
            return profitCalculator.calculateSlotProfit(slotIndex);
        } catch (Exception e) {
            log.debug("Error calculating profit for slot {}", slotIndex, e);
            return null;
        }
    }

    /**
     * Determines if the offer in a slot is a buy offer.
     * 
     * @param slotIndex The slot index (0-7)
     * @return true if buy offer, false if sell offer or unknown
     */
    private boolean isBuyOffer(int slotIndex) {
        try {
            // First try to get it from SavedOffer
            SavedOffer savedOffer = getOffer(slotIndex);
            if (savedOffer != null) {
                return savedOffer.getOfferStatus() == OfferStatus.BUY;
            }

            // Fallback to current GE offer
            GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
            if (offers != null && slotIndex < offers.length) {
                GrandExchangeOffer currentOffer = offers[slotIndex];
                if (currentOffer != null) {
                    GrandExchangeOfferState state = currentOffer.getState();
                    return state == GrandExchangeOfferState.BUYING 
                        || state == GrandExchangeOfferState.BOUGHT 
                        || state == GrandExchangeOfferState.CANCELLED_BUY;
                }
            }
        } catch (Exception e) {
            log.debug("Error determining offer type for slot {}", slotIndex, e);
        }
        
        return false; // Default to sell if unknown
    }

    /**
     * Checks if an offer is being tracked (has a SavedOffer).
     * 
     * @param slotIndex The slot index (0-7)
     * @return true if offer is tracked, false otherwise
     */
    private boolean isOfferTracked(int slotIndex) {
        return getOffer(slotIndex) != null;
    }

    /**
     * Applies the appropriate color to text based on profit and offer type.
     * 
     * @param text The original text
     * @param determinedColor The color to apply (without <col=>)
     * @return Text with color tags applied
     */
    private String applyColorTag(String text, Color determinedColor) {
        return "<col=" + colorToHex(determinedColor) + ">" + stripColorTags(text) + "</col>";
    }

    /**
     * Strips existing color tags from text.
     * 
     * @param text Text potentially containing color tags
     * @return Plain text without color tags
     */
    private String stripColorTags(String text) {
        if (text == null) {
            return "";
        }

        // Remove <col=XXXXXX> and </col> tags
        return text.replaceAll("<col=[0-9a-fA-F]{6}>", "").replaceAll("</col>", "");
    }

    /**
     * Converts a Color object to hex string (without # prefix).
     * 
     * @param color The color to convert
     * @return Hex string (e.g., "32a0fa")
     */
    private String colorToHex(Color color) {
        return String.format("%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Gets the slot widget for a given slot index.
     * 
     * @param slotIndex The slot index (0-7)
     * @return The slot widget, or null if not found
     */
    private Widget getSlotWidget(int slotIndex) {
        return client.getWidget(InterfaceID.GE_OFFERS, FIRST_SLOT_CHILD_ID + slotIndex);
    }

    /**
     * Gets the price text widget from a slot widget.
     * This is the widget at child index 25 within the slot.
     * 
     * @param slotWidget The slot widget
     * @return The price text widget, or null if not found
     */
    private Widget getPriceTextWidget(Widget slotWidget) {
        if (slotWidget == null) {
            return null;
        }

        return slotWidget.getChild(PRICE_TEXT_WIDGET_CHILD_INDEX);
    }

    /**
     * Checks if a widget and its text are valid for modification.
     * 
     * @param widget The widget to check
     * @return true if the widget has valid text, false otherwise
     */
    private boolean hasValidText(Widget widget) {
        if (widget == null) {
            return false;
        }

        String text = widget.getText();
        return text != null && !text.isEmpty();
    }

    /**
     * Resets a widget's text color to the specified default.
     * 
     * @param widget The widget to reset
     * @param defaultColor The default color hex string (without # prefix)
     */
    private void resetWidgetTextColor(Widget widget, String defaultColor) {
        String plainText = stripColorTags(widget.getText());
        widget.setText("<col=" + defaultColor + ">" + plainText + "</col>");
    }
}
