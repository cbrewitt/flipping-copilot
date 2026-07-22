package com.flippingcopilot.controller;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.model.OfferManager;
import com.flippingcopilot.model.OfferStatus;
import com.flippingcopilot.model.SavedOffer;
import com.flippingcopilot.util.ProfitCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;

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

    private SavedOffer getOffer(int slotIndex) {
        try {
            long accountHash = client.getAccountHash();
            return offerManager.loadOffer(accountHash, slotIndex);
        } catch (Exception e) {
            log.debug("Error loading offer for slot {}", slotIndex, e);
            return null;
        }
    }

    public void updateAllSlots() {
        if (!config.slotPriceColorEnabled()) {
            return;
        }

        int openSlot = grandExchange.getOpenSlot();
        if (openSlot > -1) {
            if (grandExchange.isSetupOfferOpen()) {
                updateOfferSetup();
            } else {
                updateDetailView(openSlot);
            }
            return;
        }

        for (int slotIndex = 0; slotIndex < GE_SLOT_COUNT; slotIndex++) {
            updateOverviewSlot(slotIndex);
        }
    }

    private void updateOverviewSlot(int slotIndex) {
        try {
            Widget slotWidget = getSlotWidget(slotIndex);
            Widget priceTextWidget = getPriceTextWidget(slotWidget);

            updatePriceTextColor(priceTextWidget, slotIndex, Color.decode("#" + DEFAULT_COLOR));
        } catch (Exception e) {
            log.error("Error updating slot {} price color", slotIndex, e);
        }
    }

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

    private void updatePriceTextColor(Widget priceTextWidget, int slotIndex, Color defaultColor) {
        if (!hasValidText(priceTextWidget)) {
            return;
        }

        Color targetColor = defaultColor;

        if (!isBuyOffer(slotIndex)) {
            targetColor = determineProfitColor(calculateSlotProfit(slotIndex), defaultColor);
        } else if (isOfferTracked(slotIndex)) {
            targetColor = config.slotPriceProfitableColor();
        }

        priceTextWidget.setText(applyColorTag(priceTextWidget.getText(), targetColor));
    }

    private void updateOfferSetup() {
        try {
            Widget offerSetupWidget = client.getWidget(InterfaceID.GE_OFFERS, OFFER_SETUP_CHILD_ID);
            if (offerSetupWidget == null || offerSetupWidget.isHidden()) {
                return;
            }

            Widget priceTextWidget = offerSetupWidget.getChild(OFFER_SETUP_PRICE_TEXT_CHILD_INDEX);
            if (priceTextWidget == null) {
                return;
            }

            Color defaultColor = Color.decode("#" + DETAIL_AND_SETUP_DEFAULT_COLOR);
            Color color = determineOfferSetupColor(defaultColor);

            priceTextWidget.setTextColor(color.getRGB() & 0xFFFFFF);
        } catch (Exception e) {
            log.debug("Error updating offer setup price color", e);
        }
    }

    private Color determineOfferSetupColor(Color defaultColor) {
        if (!grandExchange.isOfferTypeSell()) {
            // For buy offers, show profitable if we have a tracked price and the price matches it.
            int viewedItemId = offerManager.getViewedSlotItemId();
            long viewedItemPrice = offerManager.getViewedSlotItemPrice();
            if (viewedItemId > 0 && viewedItemPrice > 0 && grandExchange.getOfferPrice() == viewedItemPrice) {
                return config.slotPriceProfitableColor();
            }

            return defaultColor;
        }

        int itemId = client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH);
        if (itemId <= 0) {
            return defaultColor;
        }

        // For sell offers, calculate profit based on the current price
        Long profit = profitCalculator.calculateProfitPerItem(itemId, grandExchange.getOfferPrice());

        return determineProfitColor(profit, defaultColor);
    }

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

    public void resetAllSlots() {
        for (int slotIndex = 0; slotIndex < GE_SLOT_COUNT; slotIndex++) {
            resetSlot(slotIndex);
        }

        resetDetailView();
        resetOfferSetup();
    }

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

    public void resetOfferSetup() {
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

    private Long calculateSlotProfit(int slotIndex) {
        try {
            return profitCalculator.calculateSlotProfit(slotIndex);
        } catch (Exception e) {
            log.debug("Error calculating profit for slot {}", slotIndex, e);
            return null;
        }
    }

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

    private boolean isOfferTracked(int slotIndex) {
        return getOffer(slotIndex) != null;
    }

    private String applyColorTag(String text, Color determinedColor) {
        return "<col=" + colorToHex(determinedColor) + ">" + stripColorTags(text) + "</col>";
    }

    private String stripColorTags(String text) {
        if (text == null) {
            return "";
        }

        // Remove <col=XXXXXX> and </col> tags
        return text.replaceAll("<col=[0-9a-fA-F]{6}>", "").replaceAll("</col>", "");
    }

    private String colorToHex(Color color) {
        return String.format("%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private Widget getSlotWidget(int slotIndex) {
        return client.getWidget(InterfaceID.GE_OFFERS, FIRST_SLOT_CHILD_ID + slotIndex);
    }

    private Widget getPriceTextWidget(Widget slotWidget) {
        if (slotWidget == null) {
            return null;
        }

        return slotWidget.getChild(PRICE_TEXT_WIDGET_CHILD_INDEX);
    }

    private boolean hasValidText(Widget widget) {
        if (widget == null) {
            return false;
        }

        String text = widget.getText();
        return text != null && !text.isEmpty();
    }

    private void resetWidgetTextColor(Widget widget, String defaultColor) {
        String plainText = stripColorTags(widget.getText());
        widget.setText("<col=" + defaultColor + ">" + plainText + "</col>");
    }
}
