package com.flippingcopilot.controller;

import com.flippingcopilot.model.PortfolioChangeEvent;
import com.flippingcopilot.model.PortfolioState;
import com.flippingcopilot.rs.OsrsLoginRS;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Singleton
public class PortfolioController {

    // dependencies
    private final Client client;
    private final OsrsLoginRS osrsLoginRS;

    // state (only interact from within client thread!)
    private PortfolioState state;
    private final List<PortfolioChangeEvent> pendingChanges = new ArrayList<>();

    @Inject
    public PortfolioController(Client client, OsrsLoginRS osrsLoginRS)  {
        this.client = client;
        this.osrsLoginRS = osrsLoginRS;
    }

    public void onTick() {
        if(!osrsLoginRS.get().loggedIn) {
            return;
        }
        PortfolioState next = PortfolioState.fromRunelite(client);
        if(state == null || !state.accountHash.equals(next.accountHash)){
            state = next;
            return;
        }
        if(!next.bankLoaded && state.bankLoaded) {
            next.bankLoaded = true;
            next.bankItems = state.bankItems;
        }
        state = next;
    }
}
