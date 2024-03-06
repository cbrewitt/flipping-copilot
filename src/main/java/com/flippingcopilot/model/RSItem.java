package com.flippingcopilot.model;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;

@Getter
@AllArgsConstructor
public class RSItem {
    int id;
    long amount;

    static RSItem getUnnoted(Item item, Client client) {
        int itemId = item.getId();
        ItemComposition itemComposition = client.getItemDefinition(itemId);
        if (itemComposition.getNote() != -1) {
            itemId = itemComposition.getLinkedNoteId();
        }
        return new RSItem(itemId, item.getQuantity());
    }
}