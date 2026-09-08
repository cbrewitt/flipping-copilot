package copilot.model;

import java.util.*;
import net.runelite.api.*;
import static copilot.util.Constants.PLATINUM_TOKEN_VALUE;

import java.util.stream.*;

public class Inventory extends ArrayList<RSItem> {

    boolean hasSufficientGp(Suggestion suggestion) {
        return suggestion.type != SuggestionType.BUY
                || getTotalGp() >= (long) suggestion.price * suggestion.quantity;
    }

    public long getTotalGp() {
        return getTotalAmount(ItemID.COINS_995) + PLATINUM_TOKEN_VALUE * getTotalAmount(ItemID.PLATINUM_TOKEN);
    }

    public long getTotalAmount(long itemId) {
        return stream().filter(item -> item.getId() == itemId).mapToLong(RSItem::getAmount).sum();
    }

    public static Inventory fromRunelite(ItemContainer inventory, Client client) {
        var unnotedItems = new Inventory();
        if (inventory == null) { return unnotedItems; }
        Item[] items = inventory.getItems();
        for (Item item : items) {
            if (item.getId() == -1) { continue; }
            unnotedItems.add(RSItem.getUnnoted(item, client));
        }
        return unnotedItems;
    }

    public Map<Integer, Long> getItemAmounts() {
        return stream().collect(Collectors.groupingBy(RSItem::getId, Collectors.summingLong(RSItem::getAmount)));
    }

    public void mergeItem(RSItem i) {
        for(RSItem item : this) {
            if(item.id == i.id) {
                item.amount += i.amount;
                return;
            }
        }
        add(i);
    }

    public boolean missingJustCollected(Map<Integer, Long> inLimboItems) {
        return inLimboItems.entrySet().stream().anyMatch(entry -> {
            long quantity = entry.getValue();
            return quantity > 0 && getTotalAmount(entry.getKey()) < quantity;
        });
    }
}
