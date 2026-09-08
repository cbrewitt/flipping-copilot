package copilot.rs;

import copilot.model.*;
import com.google.inject.*;

@Singleton
public class HeldItemSyncStateRS extends ReactiveStateImpl<HeldItemSyncState> {

    @Inject
    public HeldItemSyncStateRS(GameLogin osrsLoginRS) {
        super(HeldItemSyncState.empty());
        osrsLoginRS.registerListener(state -> {
            if (state == null || !state.loggedIn) { set(HeldItemSyncState.empty()); }
        });
    }

    public void delayForTicks(int currentTick, int delayTicks) {
        int nextDelayUntilTick = Math.max(currentTick + Math.max(0, delayTicks), get().delayUntilTick);
        set(new HeldItemSyncState(nextDelayUntilTick));
    }
}
