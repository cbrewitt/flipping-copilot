package copilot.model;

import net.runelite.api.Client;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class UncollectedStateTest {
    @Test
    public void collectionsAccumulateWithinTickAndResetOnNextTick() {
        var tick = new AtomicInteger(10);
        Client client = (Client) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{Client.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getTickCount")) return tick.get();
                    throw new AssertionError(method);
                });
        var state = new Uncollected(client);
        assertFalse(state.HasUncollected(1L));
        state.addUncollected(1L, 0, 100, 5, 20);
        state.addUncollected(1L, 0, 100, 3, 0);
        state.addUncollected(1L, 1, 100, 2, 10);
        state.addUncollected(2L, 0, 200, 7, 0);
        assertEquals(Map.of(100, 10L, 995, 30L), state.loadAllUncollected(1L));
        assertTrue(state.HasUncollected(1L));

        state.clearSlotUncollected(1L, 0);
        assertEquals(Map.of(100, 8L, 995, 20L), state.getLastClearedUncollected());
        assertEquals(Collections.singletonList(0), state.getLastClearedSlots());
        assertEquals(Map.of(100, 2L, 995, 10L), state.loadAllUncollected(1L));
        state.clearAllUncollected(1L);
        assertEquals(Map.of(100, 10L, 995, 30L), state.getLastClearedUncollected());
        assertEquals(Arrays.asList(0, 0, 1, 2, 3, 4, 5, 6, 7), state.getLastClearedSlots());
        assertEquals(10, state.getLastClearedTick());
        assertFalse(state.HasUncollected(1L));
        assertEquals(Map.of(200, 7L), state.loadAllUncollected(2L));

        tick.set(11);
        state.addUncollected(1L, 2, 101, 4, 8);
        state.clearSlotUncollected(1L, 2);
        assertEquals(Map.of(101, 4L, 995, 8L), state.getLastClearedUncollected());
        assertEquals(Collections.singletonList(2), state.getLastClearedSlots());
        assertEquals(11, state.getLastClearedTick());
        assertEquals(11, state.getLastUncollectedAddedTick());
        state.ensureSlotClear(2L, 0);
        assertFalse(state.HasUncollected(2L));
        assertEquals(Map.of(101, 4L, 995, 8L), state.getLastClearedUncollected());
    }
}
