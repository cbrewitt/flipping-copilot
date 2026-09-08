package copilot.model;

import lombok.*;

@AllArgsConstructor
public class OfferSetup {

    public final String offerType;
    public final int currentItemId, offerPrice;
    public final int offerQuantity;
    public final boolean searchOpen;

    public boolean offerDetailsCorrect(Suggestion suggestion) {
        return offerType.equals(suggestion.offerType())
                && currentItemId == suggestion.itemId
                && offerPrice == suggestion.price
                && offerQuantity == suggestion.quantity;
    }

    public boolean isEmptyBuyState() { return offerType.equals("buy") && currentItemId == -1; }
}
