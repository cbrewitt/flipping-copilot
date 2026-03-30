package com.flippingcopilot.rs;

import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.model.PortfolioItemCardData;
import com.flippingcopilot.model.PortfolioState;
import com.flippingcopilot.model.PortfolioSummaryData;
import com.flippingcopilot.model.Offer;
import com.flippingcopilot.model.OfferStatus;
import com.flippingcopilot.model.StatusOfferList;
import com.flippingcopilot.model.Suggestion;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.runelite.client.callback.ClientThread;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.ItemID;

@Singleton
public class PortfolioStateRS extends ReactiveStateImpl<PortfolioState> {
    private final ItemController itemController;
    private final BankStateRS bankStateRS;
    private final ClientThread clientThread;

    @Inject
    public PortfolioStateRS(OsrsLoginRS osrsLoginRS,
                            ItemController itemController,
                            BankStateRS bankStateRS,
                            ClientThread clientThread) {
        super(PortfolioState.empty());
        this.itemController = itemController;
        this.bankStateRS = bankStateRS;
        this.clientThread = clientThread;
        osrsLoginRS.registerListener(state -> {
            if (state == null || !state.loggedIn) {
                set(PortfolioState.empty());
            }
        });
    }

    public PortfolioState buildPortfolioState(Map<Integer, Integer> bank,
                                              Map<Integer, Integer> runeliteInventory,
                                              List<Suggestion.PortfolioItem> portfolioItems,
                                              StatusOfferList offers,
                                              Map<Integer, Long> uncollected) {
        if (portfolioItems == null) {
            long cashValue = itemController.totalCash(bank)
                    + itemController.totalCash(runeliteInventory)
                    + totalUncollectedCash(uncollected);
            return new PortfolioState(true, Collections.emptyMap(), new PortfolioSummaryData(cashValue, 0L, cashValue, 0L));
        }

        Map<Integer, Integer> geQuantitiesByItemId = buildGeQuantitiesByItemId(offers, uncollected);
        Map<Integer, PortfolioItemCardData> map = new LinkedHashMap<>();
        for (Suggestion.PortfolioItem portfolioItem : portfolioItems) {
            if (portfolioItem == null) {
                continue;
            }
            int itemId = portfolioItem.getItemId();
            int inventoryQty = safeQty(runeliteInventory, itemId);
            int bankQty = safeQty(bank, itemId);
            int openFlipsQuantity = Math.max(0, portfolioItem.getAmount());

            PortfolioItemCardData data = new PortfolioItemCardData(
                    itemId,
                    itemController.getItemName(itemId),
                    safeQty(geQuantitiesByItemId, itemId),
                    inventoryQty,
                    bankQty,
                    openFlipsQuantity,
                    1,
                    portfolioItem.getPostTaxSellUnitPrice(),
                    calculateUnrealizedUnitProfit(portfolioItem),
                    Math.max(0, portfolioItem.heldMinutes),
                    portfolioItem.inPortfolio
            );
            map.put(itemId, data);
        }
        return new PortfolioState(true, map, buildSummaryData(map, bank, runeliteInventory, uncollected));
    }

    public void updatePortfolioState(Map<Integer, Integer> suggestionBank,
                                     List<Suggestion.PortfolioItem> portfolioItems,
                                     StatusOfferList offers,
                                     Map<Integer, Long> uncollected) {
        clientThread.invokeLater(() -> {
            Map<Integer, Integer> runeliteBank = bankStateRS.get().isLoaded() ? bankStateRS.get().getItems() : null;
            Map<Integer, Integer> effectiveBank = runeliteBank == null ? suggestionBank : runeliteBank;
            set(buildPortfolioState(
                    effectiveBank,
                    itemController.getRunliteInventory(),
                    portfolioItems,
                    offers,
                    uncollected
            ));
            return true;
        });
    }

    public void updatePortfolioState(Map<Integer, Integer> suggestionBank,
                                     List<Suggestion.PortfolioItem> portfolioItems) {
        updatePortfolioState(suggestionBank, portfolioItems, null, null);
    }

    private int safeQty(Map<Integer, Integer> map, int itemId) {
        if (map == null) {
            return 0;
        }
        Integer v = map.get(itemId);
        return v == null ? 0 : Math.max(0, v);
    }

    private Long calculateUnrealizedUnitProfit(Suggestion.PortfolioItem portfolioItem) {
        if (portfolioItem == null || portfolioItem.getAmount() <= 0) {
            return null;
        }
        return portfolioItem.getPostTaxSellUnitPrice() - portfolioItem.getUnitBuyPrice();
    }

    private Map<Integer, Integer> buildGeQuantitiesByItemId(StatusOfferList offers,
                                                             Map<Integer, Long> uncollected) {
        Map<Integer, Integer> geByItemId = new LinkedHashMap<>();

        if (offers != null) {
            for (Offer offer : offers) {
                if (offer == null || offer.getItemId() <= 0) {
                    continue;
                }
                int itemId = itemController.toUnnotedItemId(offer.getItemId());
                if (offer.getStatus() == OfferStatus.SELL) {
                    int unsoldAmount = Math.max(0, offer.getAmountTotal() - offer.getAmountTraded());
                    if (unsoldAmount > 0) {
                        geByItemId.merge(itemId, unsoldAmount, Integer::sum);
                    }
                }
            }
        }

        if (uncollected != null) {
            for (Map.Entry<Integer, Long> entry : uncollected.entrySet()) {
                Integer itemId = entry.getKey();
                Long quantity = entry.getValue();
                if (itemId == null || quantity == null || quantity <= 0 || itemId == ItemID.COINS_995) {
                    continue;
                }
                int normalizedItemId = itemController.toUnnotedItemId(itemId);
                int uncollectedQty = quantity > Integer.MAX_VALUE ? Integer.MAX_VALUE : quantity.intValue();
                geByItemId.merge(normalizedItemId, uncollectedQty, Integer::sum);
            }
        }

        return geByItemId;
    }

    private PortfolioSummaryData buildSummaryData(Map<Integer, PortfolioItemCardData> map,
                                                  Map<Integer, Integer> bank,
                                                  Map<Integer, Integer> runeliteInventory,
                                                  Map<Integer, Long> uncollected) {
        long assetsValue = 0L;
        long unrealizedProfit = 0L;
        for (PortfolioItemCardData item : map.values()) {
            if (!item.isInPortfolio()) {
                continue;
            }
            assetsValue += item.getPostTaxSellUnitPrice() * item.getOpenFlipsQuantity();
            unrealizedProfit += item.flipsUnrealizedProfit();
        }
        long cashValue = itemController.totalCash(bank)
                + itemController.totalCash(runeliteInventory)
                + totalUncollectedCash(uncollected);
        return new PortfolioSummaryData(assetsValue + cashValue, unrealizedProfit, cashValue, assetsValue);
    }

    private long totalUncollectedCash(Map<Integer, Long> uncollected) {
        if (uncollected == null) {
            return 0L;
        }
        Long coins = uncollected.get(ItemID.COINS_995);
        return coins == null ? 0L : Math.max(0L, coins);
    }
}
