package com.flippingcopilot.model;

import net.runelite.api.*;
import net.runelite.api.ItemID;
import static com.flippingcopilot.util.Constants.PLATINUM_TOKEN_VALUE;

import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;


public class Inventory extends ArrayList<RSItem> {

    boolean hasSufficientGp(Suggestion suggestion) {
        return !suggestion.getType().equals("buy")
                || getTotalGp() >= (long) suggestion.getPrice() * suggestion.getQuantity();
    }

    boolean hasSufficientItems(Suggestion suggestion) {
        return !suggestion.getType().equals("sell")
                || getTotalAmount(suggestion.getItemId()) >= suggestion.getQuantity();
    }

    public long getTotalGp() {
        return getTotalAmount(ItemID.COINS_995) + PLATINUM_TOKEN_VALUE * getTotalAmount(ItemID.PLATINUM_TOKEN);
    }

    public long getTotalAmount(long itemId) {
        long amount = 0;
        for (RSItem item : this) {
            if (item.getId() == itemId) {
                amount += item.getAmount();
            }
        }
        return amount;
    }


    public static Inventory fromRunelite(ItemContainer inventory, Client client) {
        Inventory unnotedItems = new Inventory();
        Item[] items = inventory.getItems();
        for (Item item : items) {
            if (item.getId() == -1) {
                continue;
            }
            unnotedItems.add(RSItem.getUnnoted(item, client));
        }
        return unnotedItems;
    }

    Map<Integer, Long> getItemAmounts() {
        return stream().collect(Collectors.groupingBy(RSItem::getId,
                        Collectors.summingLong(RSItem::getAmount)));
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

     public boolean missingJustCollected( Map<Integer, Long> inLimboItems) {
         for (Map.Entry<Integer, Long> entry : inLimboItems.entrySet()) {
             Integer itemId = entry.getKey();
             Long qty = entry.getValue();
             if (qty > 0) {
                 long inventoryQty = getTotalAmount(itemId);
                 if (inventoryQty < qty) {
                     return true;
                 }
             }
         }
         return false;
    }
}
