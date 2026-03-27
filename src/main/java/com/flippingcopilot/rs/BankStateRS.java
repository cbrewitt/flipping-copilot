package com.flippingcopilot.rs;

import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.model.BankState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Singleton
@Slf4j
public class BankStateRS extends ReactiveStateImpl<BankState> {
    private final ItemController itemController;
    private final OsrsLoginRS osrsLoginRS;

    @Inject
    public BankStateRS(ItemController itemController, OsrsLoginRS osrsLoginRS) {
        super(BankState.empty());
        this.itemController = itemController;
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
        Map<Integer, Integer> bankInventory = itemController.getRunliteBankInventory();
        if (bankInventory == null) {
            return;
        }
        set(new BankState(true, bankInventory));
    }
}
