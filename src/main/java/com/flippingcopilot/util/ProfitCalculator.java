package com.flippingcopilot.util;

import com.flippingcopilot.manager.CopilotLoginManager;
import com.flippingcopilot.model.*;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.HashSet;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ProfitCalculator {
    private static final int GE_SLOT_COUNT = 8;
    private final static int MAX_PRICE_FOR_GE_TAX = 250000000;
    private final static int GE_TAX_CAP = 5000000;
    private final static double GE_TAX = 0.02;
    private final static HashSet<Integer> GE_TAX_EXEMPT_ITEMS = new HashSet<>(
            Arrays.asList(8011, 365, 2309, 882, 806, 1891, 8010, 1755, 28824, 2140, 2142, 8009, 5325, 1785, 2347, 347,
                    884, 807, 28790, 379, 8008, 355, 2327, 558, 1733, 13190, 233, 351, 5341, 2552, 329, 8794, 5329,
                    5343, 1735, 315, 952, 886, 808, 8013, 361, 8007, 5331));

    private final Client client;
    private final OfferManager offerManager;
    private final FlipManager flipManager;
    private final OsrsLoginManager osrsLoginManager;
    private final CopilotLoginManager copilotLoginManager;

    /**
     * Calculates the post-tax price for an item.
     */
    public static int getPostTaxPrice(int itemId, int price) {
        return price - getTaxAmount(itemId, price);
    }

    /**
     * Calculates the GE tax amount for an item.
     */
    public static int getTaxAmount(int itemId, int price) {
        if (GE_TAX_EXEMPT_ITEMS.contains(itemId)) {
            return 0;
        }

        if (price >= MAX_PRICE_FOR_GE_TAX) {
            return GE_TAX_CAP;
        }

        return (int)Math.floor(price * GE_TAX);
    }

    /**
     * Calculates the profit per item for a sell at the given price.
     * 
     * @param itemId The item ID
     * @param sellPrice The price to sell at
     * @param avgBuyPrice The average buy price
     * @return The profit per item in GP (post-tax)
     */
    public static long calculateProfitPerItem(int itemId, int sellPrice, long avgBuyPrice) {
        return getPostTaxPrice(itemId, sellPrice) - avgBuyPrice;
    }

    /**
     * Calculates the profit for a sell offer based on the buy price from a flip.
     * 
     * @param offer The sell offer
     * @param flip The flip containing the average buy price
     * @return The profit in GP (post-tax)
     */
    public static long calculateOfferProfit(SavedOffer offer, FlipV2 flip) {
        long profitPerItem = calculateProfitPerItem(offer.getItemId(), offer.getPrice(), flip.getAvgBuyPrice());
        return profitPerItem * offer.getTotalQuantity();
    }

    /**
     * Calculates the profit for a sell offer in a specific GE slot.
     * Only calculates profit for SELL offers.
     * 
     * @param slotIndex The GE slot index (0-7)
     * @return The profit in GP, or null if cannot be calculated
     */
    public Long calculateSlotProfit(int slotIndex) {
        String displayName = osrsLoginManager.getPlayerDisplayName();
        if (displayName == null) {
            return null;
        }

        long accountHash = client.getAccountHash();
        SavedOffer offer = offerManager.loadOffer(accountHash, slotIndex);
        
        if (offer == null || !offer.getOfferStatus().equals(OfferStatus.SELL)) {
            return null;
        }

        Integer accountId = copilotLoginManager.getAccountId(displayName);
        if (accountId == null || accountId == -1) {
            return null;
        }

        FlipV2 flip = flipManager.getLastFlipByItemId(accountId, offer.getItemId());
        if (flip == null || FlipStatus.FINISHED.equals(flip.getStatus())) {
            return null;
        }

        return calculateOfferProfit(offer, flip);
    }

    /**
     * Calculates the profit for a suggested sell offer.
     * Used for sell suggestions before the offer is actually placed.
     * 
     * @param suggestion The sell suggestion
     * @return The profit in GP, or null if cannot be calculated
     */
    public Long calculateSuggestionProfit(Suggestion suggestion) {
        if (!"sell".equals(suggestion.getType())) {
            return null;
        }

        String displayName = osrsLoginManager.getPlayerDisplayName();
        if (displayName == null) {
            return null;
        }

        Integer accountId = copilotLoginManager.getAccountId(displayName);
        if (accountId == null || accountId == -1) {
            return null;
        }

        // Create a transaction from the suggestion to estimate profit
        Transaction t = new Transaction();
        t.setItemId(suggestion.getItemId());
        t.setPrice(suggestion.getPrice());
        t.setQuantity(suggestion.getQuantity());
        t.setAmountSpent(suggestion.getPrice() * suggestion.getQuantity());
        t.setType(OfferStatus.SELL);

        return flipManager.estimateTransactionProfit(accountId, t);
    }

    /**
     * Calculates the profit per item for a given item and price for the current player.
     * This is useful for determining profitability before placing an offer.
     * Only calculates for flips that are not yet finished.
     * 
     * @param itemId The item ID
     * @param sellPrice The price to sell at
     * @return Profit per item in GP (post-tax revenue minus buy price), or null if cannot be calculated
     */
    public Long calculateProfitPerItem(int itemId, int sellPrice) {
        if (sellPrice <= 0) {
            return null;
        }

        String displayName = osrsLoginManager.getPlayerDisplayName();
        if (displayName == null) {
            return null;
        }

        Integer accountId = copilotLoginManager.getAccountId(displayName);
        if (accountId == null || accountId == -1) {
            return null;
        }

        FlipV2 flip = flipManager.getLastFlipByItemId(accountId, itemId);
        if (flip == null || FlipStatus.FINISHED.equals(flip.getStatus())) {
            return null;
        }

        long avgBuyPrice = flip.getAvgBuyPrice();
        if (avgBuyPrice <= 0) {
            return null;
        }

        return calculateProfitPerItem(itemId, sellPrice, avgBuyPrice);
    }

    /**
     * Finds the profit for a sell offer by item name.
     * Searches through all GE slots to find a matching sell offer.
     * 
     * @param itemName The item name to search for
     * @return The profit in GP, or 0 if not found
     */
    public long getProfitByItemName(String itemName) {
        String displayName = osrsLoginManager.getPlayerDisplayName();
        if (displayName == null) {
            return 0;
        }

        Integer accountId = copilotLoginManager.getAccountId(displayName);
        if (accountId == null || accountId == -1) {
            return 0;
        }

        long accountHash = client.getAccountHash();
        
        for (int slotIndex = 0; slotIndex < GE_SLOT_COUNT; slotIndex++) {
            SavedOffer offer = offerManager.loadOffer(accountHash, slotIndex);
            
            if (offer == null || !offer.getOfferStatus().equals(OfferStatus.SELL)) {
                continue;
            }

            FlipV2 flip = flipManager.getLastFlipByItemId(accountId, offer.getItemId());
            if (flip == null || FlipStatus.FINISHED.equals(flip.getStatus())) {
                continue;
            }

            if (flip.getCachedItemName().equals(itemName)) {
                return calculateOfferProfit(offer, flip);
            }
        }

        return 0;
    }
}
