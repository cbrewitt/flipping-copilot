package com.flippingcopilot.model;

import lombok.AllArgsConstructor;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

/**
 * A sunk grand exchange offer. These are always sell offers for 1 item that are either SELLING 1/1 or SOLD 1/1.
 */
@AllArgsConstructor
public class SinkGrandExchangeOffer implements GrandExchangeOffer {
	private final int id;
	private final int price;

	@Override
	public int getQuantitySold()
	{
		return 1;
	}

	@Override
	public int getItemId()
	{
		return id;
	}

	@Override
	public int getTotalQuantity()
	{
		return 1;
	}

	@Override
	public int getPrice()
	{
		return price;
	}

	@Override
	public int getSpent()
	{
		return price;
	}

	@Override
	public GrandExchangeOfferState getState() {
		// these are briefly SELLING 1/1 before moving to SOLD 1/1 - this move looks spoofed
		// so for simplicity am just using SOLD
		return GrandExchangeOfferState.SOLD;
	}
}