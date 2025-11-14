package com.flippingcopilot.util;

import com.flippingcopilot.model.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Determines whether GE offers are profitable based on tracked flip data.
 * This matches the logic used in the tooltip profit calculation.
 */
@Slf4j
public class ProfitabilityChecker {

    /**
     * Determines if a sell offer is profitable based on tracked flip data.
     * 
     * For SELL offers: Compares (sell price after tax) vs (average buy price from tracked flip)
     * For BUY offers: Always returns UNKNOWN (can't determine profitability until we sell)
     * 
     * @param offer The saved offer to check
     * @param flip The tracked flip data for this item (can be null)
     * @param itemId The item ID
     * @return The profitability state
     */
    public static ProfitableState checkProfitability(SavedOffer offer, FlipV2 flip, int itemId) {
        if (offer == null) {
            return ProfitableState.UNKNOWN;
        }

        // Only calculate profitability for sell offers
        if (offer.getOfferStatus() != OfferStatus.SELL) {
            return ProfitableState.UNKNOWN;
        }

        // Need tracked flip data to determine profitability
        if (flip == null || FlipStatus.FINISHED.equals(flip.getStatus())) {
            return ProfitableState.UNKNOWN;
        }

        // Calculate profit: (sell price after tax) - (avg buy price from flip)
        long sellPriceAfterTax = GeTax.getPostTaxPrice(itemId, offer.getPrice());
        long avgBuyPrice = flip.getAvgBuyPrice();
        long profitPerItem = sellPriceAfterTax - avgBuyPrice;

        if (profitPerItem > 0) {
            return ProfitableState.PROFITABLE;
        } else if (profitPerItem < 0) {
            return ProfitableState.NOT_PROFITABLE;
        } else {
            // Break-even
            return ProfitableState.UNKNOWN;
        }
    }
}
