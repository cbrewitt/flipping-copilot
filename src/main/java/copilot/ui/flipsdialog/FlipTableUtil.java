package copilot.ui.flipsdialog;
import static java.util.Comparator.*;
import static java.util.Map.entry;

import copilot.model.*;

import java.util.*;

final class FlipTableUtil {
    // Sort comparators map
    static final Map<String, Comparator<Flip>> COMPARATORS = new HashMap<>(Map.ofEntries(
        // Last sell time - special handling for non-closed trades
        entry("Last sell time", comparing(Flip::lastTransactionTime).reversed()),
        entry("First buy time", comparing(Flip::getOpenedTime)),
        entry("Account", comparing(Flip::getAccountId)),
        entry("Item", comparing(f -> f.cachedItemName != null ? f.cachedItemName : "")),
        entry("Status", comparing(Flip::getStatus)),
        entry("Bought", comparing(Flip::getOpenedQuantity)),
        entry("Sold", comparing(Flip::getClosedQuantity)),
        entry("Avg. buy price", comparing(Flip::getSpent)),
        entry("Avg. sell price", comparing(Flip::getReceivedPostTax)),
        entry("Tax", comparing(Flip::getTaxPaid)),
        entry("Profit", comparing(Flip::getProfit)),
        entry("Profit ea.", comparing(FlipTableUtil::profitEach))
    ));

    private FlipTableUtil() {
    }

    static long averageBuy(Flip flip) { return flip.openedQuantity > 0 ? flip.spent / flip.openedQuantity : 0L; }

    static long averageSell(Flip flip) {
        return flip.closedQuantity == 0
                ? 0L
                : (flip.receivedPostTax + flip.taxPaid) / flip.closedQuantity;
    }

    static long profitEach(Flip flip) { return flip.closedQuantity > 0 ? flip.profit / flip.closedQuantity : 0L; }
}
