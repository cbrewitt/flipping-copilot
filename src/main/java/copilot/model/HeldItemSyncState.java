package copilot.model;

import lombok.*;

@Value
public class HeldItemSyncState {
    public int delayUntilTick;

    public static HeldItemSyncState empty() { return new HeldItemSyncState(0); }
}
