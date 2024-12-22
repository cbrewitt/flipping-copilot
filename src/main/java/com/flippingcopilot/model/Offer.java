package com.flippingcopilot.model;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;


@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class Offer {

    @Setter
    private OfferStatus status;

    @SerializedName("item_id")
    private int itemId;

    private int price;

    @SerializedName("amount_total")
    private int amountTotal;

    @SerializedName("amount_spent")
    private int amountSpent;

    @SerializedName("amount_traded")
    private int amountTraded;

    @SerializedName("items_to_collect")
    private int itemsToCollect;

    @SerializedName("gp_to_collect")
    private int gpToCollect;

    @SerializedName("box_id")
    private int boxId;

    private boolean active;

    @Setter
    @SerializedName("copilot_price_used")
    private boolean copilotPriceUsed;

    public static Offer getEmptyOffer(int slotId) {
        return new Offer(OfferStatus.EMPTY, 0, 0, 0, 0, 0, 0, 0, slotId, false, false);
    }


    public long cashStackGpValue() {
        if (status == OfferStatus.SELL) {
            return (long) (amountTotal - amountTraded) * price + gpToCollect;
        } else if (status == OfferStatus.BUY){
            // for a buy just take the full amount even if they have collected
            // we assume they won't start selling any collected items until their buy offer is finished
            return (long) amountTotal * price;
        } else {
            return 0;
        }
    }


    public static Offer fromRunelite(GrandExchangeOffer runeliteOffer, int slotId) {
        OfferStatus status = OfferStatus.fromRunelite(runeliteOffer.getState());
        boolean active = runeliteOffer.getState().equals(GrandExchangeOfferState.BUYING)
                || runeliteOffer.getState().equals(GrandExchangeOfferState.SELLING);
        return new Offer(status,
                runeliteOffer.getItemId(),
                runeliteOffer.getPrice(),
                runeliteOffer.getTotalQuantity(),
                runeliteOffer.getSpent(),
                runeliteOffer.getQuantitySold(),
                0,
                0,
                slotId,
                active,
                false);
    }


    JsonObject toJson(Gson gson) {
        JsonParser jsonParser = new JsonParser();
        return jsonParser.parse(gson.toJson(this)).getAsJsonObject();
    }

}
