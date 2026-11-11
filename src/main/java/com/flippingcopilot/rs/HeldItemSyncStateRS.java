package com.flippingcopilot.rs;

import com.flippingcopilot.model.*;

import com.google.inject.*;

@Singleton
public class HeldItemSyncStateRS extends ReactiveStateImpl<HeldItemSyncState> {
    private Market lastMarket = Market.OSRS;

    @Inject
    public HeldItemSyncStateRS(OsrsLoginRS osrsLoginRS) {
        super(HeldItemSyncState.empty());
        osrsLoginRS.registerListener(state -> {
            boolean marketChanged = state != null && state.market != lastMarket;
            if (state != null) lastMarket = state.market;
            if (state == null || !state.loggedIn || marketChanged) {
                set(HeldItemSyncState.empty());
            }
        });
    }

    public void delayForTicks(int currentTick, int delayTicks) {
        int nextDelayUntilTick = Math.max(currentTick + Math.max(0, delayTicks), get().getDelayUntilTick());
        set(new HeldItemSyncState(nextDelayUntilTick));
    }
}
