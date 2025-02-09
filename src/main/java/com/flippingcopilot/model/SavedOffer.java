package com.flippingcopilot.model;

import lombok.Data;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

import java.util.Objects;

@Data
public class SavedOffer
{
	private int itemId;
	private int quantitySold;
	private int totalQuantity;
	private int price;
	private int spent;
	private GrandExchangeOfferState state;
	private boolean copilotPriceUsed;
	private boolean wasCopilotSuggestion;


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

	public boolean isFreeSlot() {
		switch (state) {
			case CANCELLED_SELL:
			case CANCELLED_BUY:
			case EMPTY:
			case BOUGHT:
			case SOLD:
				return true;
			default:
				return false;
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
