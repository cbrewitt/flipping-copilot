package com.flippingcopilot.util;

import com.flippingcopilot.model.*;
import com.flippingcopilot.rs.PortfolioStateRS;
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
    private final PortfolioStateRS portfolioStateRS;

    public static int getPostTaxPrice(int itemId, int price) {
        return price - getTaxAmount(itemId, price);
    }

    public static int getTaxAmount(int itemId, int price) {
        if (GE_TAX_EXEMPT_ITEMS.contains(itemId)) {
            return 0;
        }

        if (price >= MAX_PRICE_FOR_GE_TAX) {
            return GE_TAX_CAP;
        }

        return (int)Math.floor(price * GE_TAX);
    }

    public static long calculateProfitPerItem(int itemId, int sellPrice, long avgBuyPrice) {
        return getPostTaxPrice(itemId, sellPrice) - avgBuyPrice;
    }

    public Long calculateSlotProfit(int slotIndex) {
        long accountHash = client.getAccountHash();
        SavedOffer offer = offerManager.loadOffer(accountHash, slotIndex);
        if (offer == null || !offer.getOfferStatus().equals(OfferStatus.SELL)) {
            return null;
        }
        Long avgBuyPrice = portfolioUnitBuyPrice(offer.getItemId());
        if (avgBuyPrice == null) {
            return null;
        }
        return calculateProfitPerItem(offer.getItemId(), offer.getPrice(), avgBuyPrice) * offer.getTotalQuantity();
    }

    public Long calculateSuggestionProfit(Suggestion suggestion) {
        if (!suggestion.isSellSuggestion() || suggestion.getPrice() <= 0) {
            return null;
        }
        Long avgBuyPrice = portfolioUnitBuyPrice(suggestion.getItemId());
        if (avgBuyPrice == null) {
            return null;
        }
        return calculateProfitPerItem(suggestion.getItemId(), suggestion.getPrice(), avgBuyPrice) * suggestion.getQuantity();
    }

    public Double calculateSuggestionRoi(Suggestion suggestion, double suggestionProfit) {
        Long costBasis = calculateSuggestionCostBasis(suggestion);
        if (costBasis == null || costBasis <= 0) {
            return null;
        }
        return suggestionProfit / (double) costBasis;
    }

    public Long calculateSuggestionCostBasis(Suggestion suggestion) {
        if (suggestion == null || suggestion.getPrice() <= 0 || suggestion.getQuantity() <= 0) {
            return null;
        }
        if (suggestion.isBuySuggestion()) {
            return (long) suggestion.getPrice() * suggestion.getQuantity();
        }
        if (suggestion.isSellSuggestion()) {
            Long avgBuyPrice = portfolioUnitBuyPrice(suggestion.getItemId());
            if (avgBuyPrice == null) {
                return null;
            }
            return avgBuyPrice * suggestion.getQuantity();
        }
        return null;
    }

    public Long calculateProfitPerItem(int itemId, int sellPrice) {
        if (sellPrice <= 0) {
            return null;
        }
        Long avgBuyPrice = portfolioUnitBuyPrice(itemId);
        if (avgBuyPrice == null) {
            return null;
        }
        return calculateProfitPerItem(itemId, sellPrice, avgBuyPrice);
    }

    public long getProfitByItemName(String itemName) {
        if (itemName == null || !portfolioStateRS.get().isLoaded()) {
            return 0;
        }
        long accountHash = client.getAccountHash();
        for (int slotIndex = 0; slotIndex < GE_SLOT_COUNT; slotIndex++) {
            SavedOffer offer = offerManager.loadOffer(accountHash, slotIndex);
            if (offer == null || !offer.getOfferStatus().equals(OfferStatus.SELL)) {
                continue;
            }
            PortfolioItemCardData card = portfolioStateRS.get().getItemCardDataByItemId().get(offer.getItemId());
            if (card == null || card.getUnitBuyPrice() <= 0 || !itemName.equals(card.getItemName())) {
                continue;
            }
            return calculateProfitPerItem(offer.getItemId(), offer.getPrice(), card.getUnitBuyPrice()) * offer.getTotalQuantity();
        }
        return 0;
    }

    private Long portfolioUnitBuyPrice(int itemId) {
        if (!portfolioStateRS.get().isLoaded()) {
            return null;
        }
        PortfolioItemCardData card = portfolioStateRS.get().getItemCardDataByItemId().get(itemId);
        if (card == null) {
            return null;
        }
        long unitBuyPrice = card.getUnitBuyPrice();
        return unitBuyPrice > 0 ? unitBuyPrice : null;
    }
}
