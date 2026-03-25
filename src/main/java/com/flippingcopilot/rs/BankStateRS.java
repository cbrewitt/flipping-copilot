package com.flippingcopilot.rs;

import com.flippingcopilot.model.BankState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Singleton
@Slf4j
public class BankStateRS extends ReactiveStateImpl<BankState> {
    private final Client client;
    private final OsrsLoginRS osrsLoginRS;

    @Inject
    public BankStateRS(Client client, OsrsLoginRS osrsLoginRS) {
        super(BankState.empty());
        this.client = client;
        this.osrsLoginRS = osrsLoginRS;
        osrsLoginRS.registerListener(state -> {
            if (state == null || !state.loggedIn) {
                set(BankState.empty());
            }
        });
    }

    public void onGameTick() {
        if (!osrsLoginRS.get().loggedIn) {
            return;
        }
        ItemContainer bankContainer = client.getItemContainer(InventoryID.BANK);
        if (bankContainer == null || bankContainer.getItems() == null) {
            return;
        }

        Map<Integer, Long> items = new HashMap<>();
        Arrays.stream(bankContainer.getItems())
                .filter(item -> item != null && item.getId() > 0 && item.getQuantity() > 0)
                .forEach(item -> items.merge(item.getId(), (long) item.getQuantity(), Long::sum));
        set(new BankState(true, items));
    }
}
