package copilot.model;
import net.runelite.api.*;

import lombok.*;
@Getter
@AllArgsConstructor
public class RSItem {
    int id;
    long amount;

    static RSItem getUnnoted(Item item, Client client) {
        int itemId = item.getId();
        var itemComposition = client.getItemDefinition(itemId);
        if (itemComposition.getNote() != -1) { itemId = itemComposition.getLinkedNoteId(); }
        return new RSItem(itemId, item.getQuantity());
    }
}