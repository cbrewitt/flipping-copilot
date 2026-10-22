package com.flippingcopilot.model;

import java.util.*;
import java.util.function.Function;

// Live state for the logged in account, snapshots for others. 'All accounts' merges holdings into one row per item.
public final class PortfolioAggregator {

    private PortfolioAggregator() {
    }

    // selectedAccountId null = all accounts, liveAccountId null or -1 = not logged in
    public static PortfolioView build(Integer selectedAccountId,
                                      Integer liveAccountId,
                                      PortfolioState live,
                                      Map<Integer, AccountPortfolioSnapshot> snapshots,
                                      Map<Integer, String> accountIdToDisplayName,
                                      Function<Integer, String> itemNames) {
        boolean liveValid = liveAccountId != null && liveAccountId != -1 && live != null;
        String liveName = liveValid ? accountIdToDisplayName.getOrDefault(liveAccountId, "") : null;

        if (selectedAccountId != null) {
            if (liveValid && selectedAccountId.equals(liveAccountId)) {
                AccountPortfolioSnapshot s = AccountPortfolioSnapshot.of(liveName, liveAccountId, 0, live);
                return new PortfolioView(live.getSummaryData(), toRows(Collections.singletonList(s), itemNames), true,
                        Collections.singletonList(new PortfolioView.Source(liveName, null)), Collections.emptyList());
            }
            AccountPortfolioSnapshot s = snapshots.get(selectedAccountId);
            String name = displayName(selectedAccountId, s, accountIdToDisplayName);
            if (s == null) {
                return new PortfolioView(emptySummary(), Collections.emptyList(), false,
                        Collections.emptyList(), Collections.singletonList(name));
            }
            return new PortfolioView(s.summary(), toRows(Collections.singletonList(withName(s, name)), itemNames), false,
                    Collections.singletonList(new PortfolioView.Source(name, s.savedAt)), Collections.emptyList());
        }

        List<AccountPortfolioSnapshot> included = new ArrayList<>();
        List<PortfolioView.Source> sources = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        if (liveValid) {
            included.add(AccountPortfolioSnapshot.of(liveName, liveAccountId, 0, live));
            sources.add(new PortfolioView.Source(liveName, null));
        }
        List<Integer> otherAccountIds = new ArrayList<>(accountIdToDisplayName.keySet());
        otherAccountIds.remove(liveValid ? liveAccountId : null);
        otherAccountIds.sort(Comparator.comparing(id -> accountIdToDisplayName.getOrDefault(id, ""), String.CASE_INSENSITIVE_ORDER));
        for (Integer accountId : otherAccountIds) {
            AccountPortfolioSnapshot s = snapshots.get(accountId);
            String name = displayName(accountId, s, accountIdToDisplayName);
            if (s == null) {
                missing.add(name);
            } else {
                included.add(withName(s, name));
                sources.add(new PortfolioView.Source(name, s.savedAt));
            }
        }

        long marketValue = 0, unrealized = 0, cash = 0, assets = 0, lockedBuyCash = 0;
        for (AccountPortfolioSnapshot s : included) {
            marketValue += s.portfolioMarketValue;
            unrealized += s.unrealizedProfit;
            cash += s.cashValue;
            assets += s.assetsValue;
            lockedBuyCash += s.lockedBuyCash;
        }
        PortfolioSummaryData summary = new PortfolioSummaryData(marketValue, unrealized, cash, assets, lockedBuyCash);
        return new PortfolioView(summary, toRows(included, itemNames), false, sources, missing);
    }

    private static List<PortfolioRow> toRows(List<AccountPortfolioSnapshot> accounts, Function<Integer, String> itemNames) {
        Map<Integer, RowTotals> totalsByItemId = new LinkedHashMap<>();
        for (AccountPortfolioSnapshot account : accounts) {
            for (AccountPortfolioSnapshot.Item i : account.items) {
                if (i.quantity <= 0) {
                    continue;
                }
                totalsByItemId.computeIfAbsent(i.itemId, k -> new RowTotals()).add(i, account.displayName);
            }
        }
        List<PortfolioRow> rows = new ArrayList<>(totalsByItemId.size());
        totalsByItemId.forEach((itemId, t) -> rows.add(t.toRow(itemId, itemNames.apply(itemId))));
        return rows;
    }

    private static String displayName(Integer accountId, AccountPortfolioSnapshot s, Map<Integer, String> accountIdToDisplayName) {
        String name = accountIdToDisplayName.get(accountId);
        if (name == null && s != null) {
            name = s.displayName;
        }
        return name == null ? "Unknown" : name;
    }

    private static AccountPortfolioSnapshot withName(AccountPortfolioSnapshot s, String displayName) {
        if (Objects.equals(s.displayName, displayName)) {
            return s;
        }
        return new AccountPortfolioSnapshot(displayName, s.accountId, s.savedAt, s.portfolioMarketValue,
                s.unrealizedProfit, s.cashValue, s.assetsValue, s.lockedBuyCash, s.items);
    }

    private static PortfolioSummaryData emptySummary() {
        return new PortfolioSummaryData(0L, 0L, 0L, 0L, 0L);
    }

    private static class RowTotals {
        int quantity;
        long marketValue;
        long unrealizedProfit;
        // ROI only counts holdings with a known unit profit and a positive cost
        long roiProfit;
        long roiCost;
        // avg buy price only counts holdings with a known buy price
        long buySpend;
        long buyQuantity;
        int heldMinutes;
        final List<String> accounts = new ArrayList<>();

        void add(AccountPortfolioSnapshot.Item i, String account) {
            long qty = i.quantity;
            quantity += i.quantity;
            marketValue += i.postTaxSellUnitPrice * qty;
            long unitProfit = i.unrealizedUnitProfit == null ? 0L : i.unrealizedUnitProfit;
            unrealizedProfit += unitProfit * qty;
            if (i.unrealizedUnitProfit != null && i.postTaxSellUnitPrice - unitProfit > 0) {
                roiProfit += unitProfit * qty;
                roiCost += (i.postTaxSellUnitPrice - unitProfit) * qty;
            }
            if (i.unitBuyPrice > 0) {
                buySpend += i.unitBuyPrice * qty;
                buyQuantity += qty;
            }
            heldMinutes = Math.max(heldMinutes, i.heldMinutes);
            if (account != null && !account.isEmpty() && !accounts.contains(account)) {
                accounts.add(account);
            }
        }

        PortfolioRow toRow(int itemId, String itemName) {
            Double roi = roiCost > 0 ? (double) roiProfit / (double) roiCost : null;
            Long avgBuyPrice = buyQuantity > 0 ? Math.round((double) buySpend / (double) buyQuantity) : null;
            return new PortfolioRow(itemId, itemName, quantity, marketValue, unrealizedProfit, roi, avgBuyPrice,
                    heldMinutes, Collections.unmodifiableList(accounts));
        }
    }
}
