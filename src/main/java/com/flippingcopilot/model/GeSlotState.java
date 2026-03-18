package com.flippingcopilot.model;

import lombok.*;
import net.runelite.api.GrandExchangeOffer;


@Getter
@AllArgsConstructor
@EqualsAndHashCode
@Data
public class GeSlotState {

    public OfferStatus status;
    public int quantitySold;
    public int ItemId;
    public int totalQuantity;
    public int price;
    public int spent;

    public static GeSlotState fromGrandExchangeOffer(GrandExchangeOffer runeliteOffer) {
        return runeliteOffer == null ? null : new GeSlotState(OfferStatus.fromRunelite(runeliteOffer.getState()),
                runeliteOffer.getItemId(),
                runeliteOffer.getPrice(),
                runeliteOffer.getTotalQuantity(),
                runeliteOffer.getSpent(),
                runeliteOffer.getQuantitySold());
    }
}
