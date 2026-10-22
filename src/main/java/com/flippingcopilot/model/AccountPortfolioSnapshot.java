package com.flippingcopilot.model;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

// Last known portfolio of an account, used for accounts not logged into this client.
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AccountPortfolioSnapshot {

    @SerializedName("display_name")
    public String displayName;

    @SerializedName("account_id")
    public int accountId;

    @SerializedName("saved_at")
    public long savedAt; // epoch seconds

    @SerializedName("portfolio_market_value")
    public long portfolioMarketValue;

    @SerializedName("unrealized_profit")
    public long unrealizedProfit;

    @SerializedName("cash_value")
    public long cashValue;

    @SerializedName("assets_value")
    public long assetsValue;

    @SerializedName("locked_buy_cash")
    public long lockedBuyCash;

    @SerializedName("items")
    public List<Item> items = new ArrayList<>();

    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Item {
        @SerializedName("item_id")
        public int itemId;

        @SerializedName("quantity")
        public int quantity;

        @SerializedName("unit_buy_price")
        public long unitBuyPrice;

        @SerializedName("post_tax_sell_unit_price")
        public long postTaxSellUnitPrice;

        @SerializedName("unrealized_unit_profit")
        public Long unrealizedUnitProfit;

        @SerializedName("held_minutes")
        public int heldMinutes;
    }

    public static AccountPortfolioSnapshot of(String displayName, int accountId, long savedAt, PortfolioState state) {
        PortfolioSummaryData s = state.getSummaryData();
        List<Item> items = new ArrayList<>();
        for (PortfolioItemCardData i : state.getItemCardDataByItemId().values()) {
            if (i.isInPortfolio()) {
                items.add(new Item(i.getItemId(), i.getPortfolioQuantity(), i.getUnitBuyPrice(),
                        i.getPostTaxSellUnitPrice(), i.getUnrealizedUnitProfit(), i.getHeldMinutes()));
            }
        }
        return new AccountPortfolioSnapshot(displayName, accountId, savedAt, s.getPortfolioMarketValue(),
                s.getUnrealizedProfit(), s.getCashValue(), s.getAssetsValue(), s.getLockedBuyCash(), items);
    }

    public PortfolioSummaryData summary() {
        return new PortfolioSummaryData(portfolioMarketValue, unrealizedProfit, cashValue, assetsValue, lockedBuyCash);
    }
}
