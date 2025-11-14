package com.flippingcopilot.ui;

import com.flippingcopilot.controller.FlippingCopilotConfig;
import com.flippingcopilot.controller.FlippingCopilotPlugin;
import com.flippingcopilot.model.SavedOffer;
import com.flippingcopilot.model.SuggestionPreferencesManager;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.ColorUtil;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;

/**
 * Manages the timer display for a single GE slot.
 */
@Slf4j
public class SlotActivityTimer {
    
    private static final String BUY_SPACER = "          ";   // 10 spaces
    private static final String SELL_SPACER = "          ";  // 10 spaces
    private static final int FONT_ID = 495;
    private static final int DEFAULT_FONT_ID = 496;
    
    @Getter
    private final int slotIndex;
    
    @Setter
    private Client client;
    
    @Setter
    private FlippingCopilotPlugin plugin;
    
    @Setter
    private SuggestionPreferencesManager preferencesManager;
    
    private Widget slotWidget;
    private Widget slotStateWidget;
    
    @Getter
    private SavedOffer currentOffer;
    
    @Getter
    private Long tradeStartTime; // Epoch seconds
    
    private boolean offerOccurredAtUnknownTime = false;

    public SlotActivityTimer(int slotIndex) {
        this.slotIndex = slotIndex;
    }

    /**
     * Sets the slot widget and initializes child references.
     */
    public void setWidget(Widget slotWidget) {
        this.slotWidget = slotWidget;
        if (slotWidget != null) {
            this.slotStateWidget = slotWidget.getChild(16);
        }
    }

    public Widget getSlotWidget() {
        return slotWidget;
    }

    /**
     * Updates the timer display. Called every second.
     */
    public void updateTimerDisplay() {
        if (client == null || plugin == null) {
            return;
        }

        if (slotWidget == null) {
            return;
        }

        // Early return if we don't have the necessary data to show timers
        if (slotWidget.isHidden() || currentOffer == null || offerOccurredAtUnknownTime || tradeStartTime == null) {
            if (slotStateWidget != null) {
                slotStateWidget.setFontId(DEFAULT_FONT_ID);
                slotStateWidget.setXTextAlignment(1);
            }
            return;
        }

        // Reload widget references (widgets can get unloaded)
        reloadWidgetReferences();

        if (slotStateWidget == null) {
            return;
        }

        if (!isSlotFilled()) {
            // Slot is empty, reset to default
            slotStateWidget.setText("Empty");
            slotStateWidget.setFontId(DEFAULT_FONT_ID);
            slotStateWidget.setXTextAlignment(1);
            return;
        }

        // Update timer text
        String formattedTime = createFormattedTimeString();
        setText(formattedTime);
        slotStateWidget.setFontId(FONT_ID);
        slotStateWidget.setXTextAlignment(0);
    }

    /**
     * Reloads widget references from the client.
     */
    private void reloadWidgetReferences() {
        Widget geWidget = client.getWidget(465, 7 + slotIndex);
        if (geWidget != null) {
            slotWidget = geWidget;
            slotStateWidget = geWidget.getChild(16);
        }
    }

    /**
     * Sets the current offer being tracked and starts the timer.
     */
    public void setCurrentOffer(SavedOffer offer) {
        this.currentOffer = offer;
        if (offer != null && offer.getState() != GrandExchangeOfferState.EMPTY) {
            if (offer.getTradeStartTime() != null) {
                this.tradeStartTime = offer.getTradeStartTime();
                this.offerOccurredAtUnknownTime = false;
            } else {
                // New offer without start time, set it now
                this.tradeStartTime = Instant.now().getEpochSecond();
                this.offerOccurredAtUnknownTime = false;
                offer.setTradeStartTime(this.tradeStartTime);
            }
        } else {
            reset();
        }
    }

    /**
     * Resets the timer for when offer completes or slot empties.
     */
    public void reset() {
        this.currentOffer = null;
        this.tradeStartTime = null;
        this.offerOccurredAtUnknownTime = false;
        resetToDefault();
    }

    /**
     * Checks if the slot has an active offer by checking the actual client state.
     */
    private boolean isSlotFilled() {
        GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
        if (offers == null || slotIndex >= offers.length) {
            return false;
        }
        return offers[slotIndex].getItemId() != 0;
    }

    /**
     * Formats and sets the timer text with colors.
     */
    private void setText(String timeString) {
        if (slotStateWidget == null) {
            return;
        }

        FlippingCopilotConfig config = plugin.getConfig();
        
        // Get the actual offer from the client (not cached)
        GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
        if (offers == null || slotIndex >= offers.length) {
            return;
        }
        GrandExchangeOffer clientOffer = offers[slotIndex];
        
        // Determine buy vs sell from the actual client offer
        String slotStateString;
        String spacer;
        Color stateTextColor;
        
        GrandExchangeOfferState state = clientOffer.getState();
        if (state == GrandExchangeOfferState.BOUGHT || 
            state == GrandExchangeOfferState.BUYING || 
            state == GrandExchangeOfferState.CANCELLED_BUY) {
            slotStateString = "Buy";
            spacer = BUY_SPACER;
            stateTextColor = config.slotTimerBuyColor();
        } else {
            slotStateString = "Sell";
            spacer = SELL_SPACER;
            stateTextColor = config.slotTimerSellColor();
        }

        // Determine timer color
        Color timeColor = getTimerColor(state);
        
        // Prevent overflow
        if (timeString.length() > 9) {
            timeString = "   --:--:--";
        }

        // Build the text
        String text = "  <html>" + 
            ColorUtil.wrapWithColorTag(slotStateString, stateTextColor) + 
            spacer + 
            ColorUtil.wrapWithColorTag(timeString, timeColor) + 
            "</html>";

        slotStateWidget.setText(text);
    }

    /**
     * Determines the color of the timer based on offer state and duration.
     */
    private Color getTimerColor(GrandExchangeOfferState state) {
        // Green for completed
        if (state == GrandExchangeOfferState.BOUGHT || 
            state == GrandExchangeOfferState.SOLD ||
            state == GrandExchangeOfferState.CANCELLED_BUY ||
            state == GrandExchangeOfferState.CANCELLED_SELL) {
            return new Color(0, 180, 0);
        }

        // Check for stagnation based on user's adjustment frequency
        if (tradeStartTime != null && !offerOccurredAtUnknownTime && preferencesManager != null) {
            long minutesElapsed = Duration.between(
                Instant.ofEpochSecond(tradeStartTime), 
                Instant.now()
            ).toMinutes();
            
            if (minutesElapsed >= preferencesManager.getTimeframe()) {
                return UIUtilities.OUTDATED_COLOR;
            }
        }

        return Color.WHITE;
    }

    /**
     * Creates the formatted time string (HH:MM:SS).
     */
    public String createFormattedTimeString() {
        if (tradeStartTime == null) {
            if (offerOccurredAtUnknownTime) {
                return "??:??:??";
            }
            return "00:00:00";
        }

        Instant start = Instant.ofEpochSecond(tradeStartTime);
        Instant end = Instant.now();
        
        GrandExchangeOfferState state = currentOffer != null ? currentOffer.getState() : null;
        if (state == GrandExchangeOfferState.BOUGHT || state == GrandExchangeOfferState.SOLD) {
            // For completed offers, could show completion time if we track it
            // For now, just show elapsed time
        }

        Duration duration = Duration.between(start, end);
        return formatDuration(duration);
    }

    /**
     * Formats a duration as HH:MM:SS.
     */
    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    /**
     * Resets the slot state widget to Jagex's default appearance.
     */
    public void resetToDefault() {
        if (slotStateWidget == null || client == null) {
            return;
        }

        if (!isSlotFilled()) {
            slotStateWidget.setText("Empty");
        } else {
            // Get the actual client offer to determine Buy/Sell
            GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
            if (offers != null && slotIndex < offers.length) {
                GrandExchangeOffer clientOffer = offers[slotIndex];
                GrandExchangeOfferState state = clientOffer.getState();
                if (state == GrandExchangeOfferState.BOUGHT || 
                    state == GrandExchangeOfferState.BUYING || 
                    state == GrandExchangeOfferState.CANCELLED_BUY) {
                    slotStateWidget.setText("Buy");
                } else {
                    slotStateWidget.setText("Sell");
                }
            }
        }
        
        slotStateWidget.setFontId(DEFAULT_FONT_ID);
        slotStateWidget.setXTextAlignment(1); // Centered
    }
}
