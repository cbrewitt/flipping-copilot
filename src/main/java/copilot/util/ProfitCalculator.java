package copilot.util;

import java.util.*;
import copilot.model.*;
import copilot.rs.*;
import lombok.*;
import net.runelite.api.*;

import javax.inject.*;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ProfitCalculator {
    private static final int GE_SLOT_COUNT = 8;
    private final static long MAX_PRICE_FOR_GE_TAX = 250000000, GE_TAX_CAP = 5000000;
    private final static double GE_TAX = 0.02;
    private final static HashSet<Integer> GE_TAX_EXEMPT_ITEMS = new HashSet<>(
            Arrays.asList(8011, 365, 2309, 882, 806, 1891, 8010, 1755, 28824, 2140, 2142, 8009, 5325, 1785, 2347, 347,
                    884, 807, 28790, 379, 8008, 355, 2327, 558, 1733, 13190, 233, 351, 5341, 2552, 329, 8794, 5329,
                    5343, 1735, 315, 952, 886, 808, 8013, 361, 8007, 5331));

    private final Client client;
    private final Offers offerManager;
    private final PortfolioStateRS portfolio;

    public static long getPostTaxPrice(int itemId, long price) { return price - getTaxAmount(itemId, price); }

    public static long getTaxAmount(int itemId, long price) {
        if (GE_TAX_EXEMPT_ITEMS.contains(itemId)) { return 0; }

        if (price >= MAX_PRICE_FOR_GE_TAX) { return GE_TAX_CAP; }

        return (long) Math.floor(price * GE_TAX);
    }

    public static long calculateProfitPerItem(int itemId, long sellPrice, long avgBuyPrice) {
        return getPostTaxPrice(itemId, sellPrice) - avgBuyPrice;
    }

    public Long calculateSlotProfit(int slotIndex) {
        long accountHash = client.getAccountHash();
        var offer = offerManager.loadOffer(accountHash, slotIndex);
        if (offer == null || !offer.getOfferStatus().equals(OfferStatus.SELL)) { return null; }
        Long avgBuyPrice = portfolioUnitBuyPrice(offer.itemId);
        if (avgBuyPrice == null) { return null; }
        return calculateProfitPerItem(offer.itemId, offer.price, avgBuyPrice) * offer.totalQuantity;
    }

    public Long calculateSuggestionProfit(Suggestion suggestion) {
        if (!suggestion.isSellSuggestion() || suggestion.price <= 0) { return null; }
        Long avgBuyPrice = portfolioUnitBuyPrice(suggestion.itemId);
        if (avgBuyPrice == null) { return null; }
        return calculateProfitPerItem(suggestion.itemId, suggestion.price, avgBuyPrice) * suggestion.quantity;
    }

    public Double calculateSuggestionRoi(Suggestion suggestion, double suggestionProfit) {
        Long costBasis = calculateSuggestionCostBasis(suggestion);
        if (costBasis == null || costBasis <= 0) { return null; }
        return suggestionProfit / (double) costBasis;
    }

    public Long calculateSuggestionCostBasis(Suggestion suggestion) {
        if (suggestion == null || suggestion.price <= 0 || suggestion.quantity <= 0) { return null; }
        if (suggestion.isBuySuggestion()) { return (long) suggestion.price * suggestion.quantity; }
        if (suggestion.isSellSuggestion()) {
            Long avgBuyPrice = portfolioUnitBuyPrice(suggestion.itemId);
            if (avgBuyPrice == null) { return null; }
            return avgBuyPrice * suggestion.quantity;
        }
        return null;
    }

    public Long calculateProfitPerItem(int itemId, long sellPrice) {
        if (sellPrice <= 0) { return null; }
        Long avgBuyPrice = portfolioUnitBuyPrice(itemId);
        if (avgBuyPrice == null) { return null; }
        return calculateProfitPerItem(itemId, sellPrice, avgBuyPrice);
    }

    public long getProfitByItemName(String itemName) {
        if (itemName == null || !portfolio.get().loaded) { return 0; }
        long accountHash = client.getAccountHash();
        for (int slotIndex = 0; slotIndex < GE_SLOT_COUNT; slotIndex++) {
            var offer = offerManager.loadOffer(accountHash, slotIndex);
            if (offer == null || !offer.getOfferStatus().equals(OfferStatus.SELL)) { continue; }
            var card = portfolio.get().itemCardDataByItemId.get(offer.itemId);
            if (card == null || card.unitBuyPrice <= 0 || !itemName.equals(card.itemName)) { continue; }
            return calculateProfitPerItem(offer.itemId, offer.price, card.unitBuyPrice) * offer.totalQuantity;
        }
        return 0;
    }

    private Long portfolioUnitBuyPrice(int itemId) {
        if (!portfolio.get().loaded) { return null; }
        var card = portfolio.get().itemCardDataByItemId.get(itemId);
        if (card == null) { return null; }
        long unitBuyPrice = card.unitBuyPrice;
        return unitBuyPrice > 0 ? unitBuyPrice : null;
    }
}
