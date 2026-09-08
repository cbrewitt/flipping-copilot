package copilot.model;

import net.runelite.api.*;
import com.google.inject.*;
import lombok.*;
import lombok.extern.slf4j.*;

import java.util.*;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @javax.inject.Inject)
public class Uncollected {

    // dependencies
    private final Client client;

    // stated
    private int lastUncollectedAddedTick = -1, lastClearedTick = -1;
    private final Map<Integer, Long> lastClearedUncollected = new HashMap<>();
    private final List<Integer> lastClearedSlots = new ArrayList<>();
    // accountId -> [slot -> [itemID -> quantity]]
    private final Map<Long, Map<Integer, Map<Integer, Long>>> uncollected = new HashMap<>();

    public synchronized boolean HasUncollected(Long accountHash) {
        var slotToUncollected = accountSlots(accountHash);
        boolean found = false;
        for (var slot : slotToUncollected.entrySet()) {
            for (var item : slot.getValue().entrySet()) {
                if (item.getValue() > 0) {
                    log.debug("{} slot {} item {} uncollected {}", accountHash, slot.getKey(), item.getKey(), item.getValue());
                    found = true;
                }
            }
        }
        return found;
    }

    public synchronized Map<Integer, Long> loadAllUncollected(Long accountHash) {
        var slotToUncollected = accountSlots(accountHash);
        Map<Integer, Long> itemIdToQuantity = new HashMap<>();
        slotToUncollected.values().forEach(itemIdToQty -> itemIdToQty.forEach((k, v) -> itemIdToQuantity.merge(k, v, Long::sum)));
        return itemIdToQuantity;
    }

    public synchronized Map<Integer, Long> loadSlotUncollected(Long accountHash, Integer slot) {
        var slotToUncollected = accountSlots(accountHash);
        return slotToUncollected.computeIfAbsent(slot, (k) -> new HashMap<>());
    }

    public synchronized void addUncollected(Long accountHash, Integer slot, int itemId, long quantity, long gp) {
        lastUncollectedAddedTick = client.getTickCount();
        var itemIdToQuantity = loadSlotUncollected(accountHash, slot);
        if (!itemIdToQuantity.containsKey(itemId)) {
           // must be a new offer
           itemIdToQuantity.clear();
        }
        if(quantity > 0) {
            log.debug("tick {} added {} of item {} to uncollected", client.getTickCount(), quantity, itemId);
            itemIdToQuantity.merge(itemId, quantity, Long::sum);
        }
        if (gp > 0) {
            log.debug("tick {} added {} gp to uncollected", client.getTickCount(), gp);
            itemIdToQuantity.merge(ItemID.COINS_995, gp, Long::sum);
        }
    }

    public synchronized void ensureSlotClear(Long accountHash, int slot) { accountSlots(accountHash).remove(slot); }

    public synchronized void clearSlotUncollected(Long accountHash, int slot) {
        var slotUncollected = loadSlotUncollected(accountHash, slot);
        beginClear();
        lastClearedSlots.add(slot);
        slotUncollected.forEach((key, value) -> lastClearedUncollected.merge(key, value, Long::sum));
        accountSlots(accountHash).remove(slot);
    }

    public synchronized void clearAllUncollected(Long accountHash) {
        log.debug("tick {} clearAllUncollected", client.getTickCount());
        var allUncollected = loadAllUncollected(accountHash);
        beginClear();
        lastClearedSlots.addAll(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7));
        allUncollected.forEach((key, value) -> {
            if(value > 0) {
                log.debug("tick {} cleared item {}, qty {}", client.getTickCount(), key, value);
                lastClearedUncollected.merge(key, value, Long::sum);
            }
        });
        uncollected.remove(accountHash);
    }

    private Map<Integer, Map<Integer, Long>> accountSlots(Long accountHash) {
        return uncollected.computeIfAbsent(accountHash, key -> new HashMap<>());
    }

    private void beginClear() {
        int tick = client.getTickCount();
        if (tick != lastClearedTick) {
            lastClearedUncollected.clear();
            lastClearedSlots.clear();
            lastClearedTick = tick;
        }
    }

    public synchronized int getLastClearedTick() { return lastClearedTick; }

    public synchronized Map<Integer, Long> getLastClearedUncollected() { return lastClearedUncollected; }

    public synchronized List<Integer> getLastClearedSlots() { return lastClearedSlots; }

    public synchronized int getLastUncollectedAddedTick() { return lastUncollectedAddedTick; }

    public synchronized void reset() {
        lastClearedUncollected.clear();
        lastClearedTick = -1;
        lastUncollectedAddedTick = -1;
        uncollected.clear();
    }
}
