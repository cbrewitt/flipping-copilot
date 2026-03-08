package com.flippingcopilot.model;

import lombok.Builder;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;

import java.util.HashMap;
import java.util.Map;

@Builder
public class PortfolioState {

    private static final int GE_SLOT_COUNT = 8;

    public Long accountHash;
    public int tick;
    public GeSlotState[] geState;
    public Map<Integer, Long> inventoryItems;
    public Map<Integer, Long> bankItems;
    public boolean bankLoaded;

    public static PortfolioState fromRunelite(Client client) {
        int tick = client.getTickCount();
        GeSlotState[] geSlots = new GeSlotState[GE_SLOT_COUNT];
        GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
        for (int slot = 0; slot < GE_SLOT_COUNT; slot++) {
            if (offers != null && slot < offers.length && offers[slot] != null) {
                geSlots[slot] = GeSlotState.fromGrandExchangeOffer(offers[slot]);
            }
        }
        ItemContainer bankContainer = client.getItemContainer(InventoryID.BANK);
        boolean bankLoaded = false;
        Map<Integer, Long> bItems = new HashMap<>();
        if (bankContainer != null) {
            bItems = Inventory.fromRunelite(bankContainer, client).getItemAmounts();
        }
        Map<Integer, Long> inventoryItems = Inventory.fromRunelite(client.getItemContainer(InventoryID.INV), client).getItemAmounts();
        return new PortfolioState(client.getAccountHash(), tick, geSlots, inventoryItems, bItems, bankLoaded);
    }
}
