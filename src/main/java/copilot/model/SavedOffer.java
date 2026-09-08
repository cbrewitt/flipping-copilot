package copilot.model;

import net.runelite.api.*;
import lombok.*;

import java.util.*;

@Data
public class SavedOffer {
	public int itemId, quantitySold;
	public int totalQuantity;
	public long price, spent;
	public GrandExchangeOfferState state;
	public boolean copilotPriceUsed, wasCopilotSuggestion;

	public static SavedOffer fromGrandExchangeOffer(GrandExchangeOffer offer) {
		SavedOffer o =  new SavedOffer();
		o.setItemId(offer.getItemId());
		o.setQuantitySold(offer.getQuantitySold());
		o.setTotalQuantity(offer.getTotalQuantity());
		o.setPrice(offer.getPrice());
		o.setSpent(offer.getSpent());
		o.setState(offer.getState());
		return o;
	}

	public OfferStatus getOfferStatus() {
		switch (state) {
			case SELLING:
			case CANCELLED_SELL:
			case SOLD:
				return OfferStatus.SELL;
			case BUYING:
			case CANCELLED_BUY:
			case BOUGHT:
				return OfferStatus.BUY;
			default:
				return OfferStatus.EMPTY;
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		SavedOffer that = (SavedOffer) o;
		return itemId == that.itemId && quantitySold == that.quantitySold && totalQuantity == that.totalQuantity && price == that.price && spent == that.spent && state == that.state;
	}

	@Override
	public int hashCode() {
		return Objects.hash(itemId, quantitySold, totalQuantity, price, spent, state, copilotPriceUsed);
	}
}
