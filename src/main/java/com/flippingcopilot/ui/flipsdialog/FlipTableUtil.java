package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.model.FlipV2;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

final class FlipTableUtil {
    static final Map<String, Comparator<FlipV2>> COMPARATORS = new HashMap<>();

    static {
        COMPARATORS.put("Last sell time", Comparator.comparing(FlipV2::lastTransactionTime).reversed());
        COMPARATORS.put("First buy time", Comparator.comparing(FlipV2::getOpenedTime));
        COMPARATORS.put("Account", Comparator.comparing(FlipV2::getAccountId));
        COMPARATORS.put("Item", Comparator.comparing(f -> f.getCachedItemName() != null ? f.getCachedItemName() : ""));
        COMPARATORS.put("Status", Comparator.comparing(FlipV2::getStatus));
        COMPARATORS.put("Bought", Comparator.comparing(FlipV2::getOpenedQuantity));
        COMPARATORS.put("Sold", Comparator.comparing(FlipV2::getClosedQuantity));
        COMPARATORS.put("Avg. buy price", Comparator.comparing(FlipV2::getSpent));
        COMPARATORS.put("Avg. sell price", Comparator.comparing(FlipV2::getReceivedPostTax));
        COMPARATORS.put("Tax", Comparator.comparing(FlipV2::getTaxPaid));
        COMPARATORS.put("Profit", Comparator.comparing(FlipV2::getProfit));
        COMPARATORS.put("Profit ea.", Comparator.comparing(FlipTableUtil::profitEach));
    }

    private FlipTableUtil() {
    }

    static long averageBuy(FlipV2 flip) {
        return flip.getOpenedQuantity() > 0 ? flip.getSpent() / flip.getOpenedQuantity() : 0L;
    }

    static long averageSell(FlipV2 flip) {
        return flip.getClosedQuantity() == 0
                ? 0L
                : (flip.getReceivedPostTax() + flip.getTaxPaid()) / flip.getClosedQuantity();
    }

    static long profitEach(FlipV2 flip) {
        return flip.getClosedQuantity() > 0 ? flip.getProfit() / flip.getClosedQuantity() : 0L;
    }
}
