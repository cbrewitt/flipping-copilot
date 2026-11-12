package com.flippingcopilot.rs;

import com.flippingcopilot.model.*;

import com.flippingcopilot.controller.ItemController;
import com.google.inject.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Singleton
@Slf4j
public class BankStateRS extends ReactiveStateImpl<BankState> {
    private Market lastMarket = Market.OSRS;
    private final ItemController itemController;
    private final OsrsLoginRS osrsLoginRS;

    @Inject
    public BankStateRS(ItemController itemController, OsrsLoginRS osrsLoginRS) {
        super(BankState.empty());
        this.itemController = itemController;
        this.osrsLoginRS = osrsLoginRS;
        osrsLoginRS.registerListener(state -> {
            if (state == null) {
                set(BankState.empty());
                return;
            }

            if (!state.loggedIn) {
                return;
            }

            if (lastMarket != state.market) set(BankState.empty());
            lastMarket = state.market;
            Long accountHash = state.accountHash;
            Long loadedAccountHash = get().getLoadedAccountHash();
            if (loadedAccountHash != null && !Objects.equals(loadedAccountHash, accountHash)) {
                set(BankState.empty());
            }
        });
    }

    public void onGameTick() {
        if (!osrsLoginRS.get().loggedIn) {
            return;
        }
        Map<Integer, Integer> bankInventory = itemController.getRunliteBankInventory();
        if (bankInventory == null) {
            return;
        }
        set(new BankState(true, bankInventory, osrsLoginRS.get().accountHash));
    }
}
