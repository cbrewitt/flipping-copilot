package com.flippingcopilot.rs;

import com.flippingcopilot.model.HeldItemSyncState;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class HeldItemSyncStateRS extends ReactiveStateImpl<HeldItemSyncState> {

    @Inject
    public HeldItemSyncStateRS(OsrsLoginRS osrsLoginRS) {
        super(HeldItemSyncState.empty());
        osrsLoginRS.registerListener(state -> {
            if (state == null || !state.loggedIn) {
                set(HeldItemSyncState.empty());
            }
        });
    }

    public void delayForTicks(int currentTick, int delayTicks) {
        int nextDelayUntilTick = Math.max(currentTick + Math.max(0, delayTicks), get().getDelayUntilTick());
        set(new HeldItemSyncState(nextDelayUntilTick));
    }
}
