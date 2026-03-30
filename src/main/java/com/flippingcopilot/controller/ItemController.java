package com.flippingcopilot.controller;

import com.flippingcopilot.model.ItemIdName;
import com.flippingcopilot.ui.FuzzySearchScorer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


@Slf4j
@Singleton
public class ItemController {

    public static final int COINS_ITEM_ID = 995;
    public static final int PLATINUM_TOKENS_ITEM_ID = 13204;
    private static final int NAME_CHAR_LIMIT = 25;

    // dependencies
    private final FuzzySearchScorer fuzzySearchScorer;
    private final Client client;
    private final ItemManager itemManager;
    private final ClientThread clientThread;

    // state
    private final Map<Integer, String> cachedItemNames = new ConcurrentHashMap<>();
    private volatile List<ItemIdName> cachedItems = new ArrayList<>(); // volatile used to guarantee immediate visibility across threads on re-assignment
    private final AtomicBoolean initScheduled = new AtomicBoolean(false);

    @Inject
    public ItemController(Client client,
                          ClientThread clientThread,
                          ItemManager itemManager,
                          FuzzySearchScorer fuzzySearchScorer,
                          ScheduledExecutorService executorService) {
        this.fuzzySearchScorer = fuzzySearchScorer;
        this.client = client;
        this.itemManager = itemManager;
        this.clientThread = clientThread;

        Runnable initItems = () -> {
            // only allow one init attempt at a time
            if(initScheduled.compareAndSet(false, true)) {
                clientThread.invokeLater(() -> {
                    List<ItemIdName> items = IntStream.range(0, client.getItemCount())
                            .mapToObj(itemManager::getItemComposition)
                            .filter(item -> item.isTradeable() && item.getNote() == -1)
                            .map(i -> new ItemIdName(i.getId(), i.getName()))
                            .collect(Collectors.toList());
                    if (!items.isEmpty()) {
                        cachedItems = items;
                        cachedItems.forEach(i -> cachedItemNames.put(i.itemId, i.name));
                        initScheduled.set(false);
                        log.debug("initialised {} items", items.size());
                        return true;
                    }
                    // if no items are found try again
                    return false;
                });
            }
        };

        // re-init every 5 mins just in case new items are added without restart
        executorService.scheduleAtFixedRate(initItems, 0, 5, TimeUnit.MINUTES);
    }

    public List<ItemIdName> search(String input, Set<Integer> existingSelectedItems) {

        if(input == null || input.isBlank()) {
            return cachedItems.stream()
                    .sorted(Comparator.comparing((ItemIdName i) -> !existingSelectedItems.contains(i.itemId)).thenComparing(i -> i.name))
                    .collect(Collectors.toList());
        }

        ToDoubleFunction<ItemIdName> comparator = fuzzySearchScorer.comparator(input);
        return cachedItems.stream()
                .filter(item -> comparator.applyAsDouble(item) > 0)
                .sorted(Comparator.comparing((ItemIdName i) -> !existingSelectedItems.contains(i.itemId)).thenComparing(Comparator.comparingDouble(comparator).reversed()
                        .thenComparing(i -> i.name)))
                .collect(Collectors.toList());
    }


    private String trimName(String name) {
        if(name.length() > NAME_CHAR_LIMIT) {
            return name.substring(0, NAME_CHAR_LIMIT - 1) + "..";
        }
        return name;
    }

    public Set<Integer> allItemIds() {
        return cachedItemNames.keySet();
    }

    public String getItemName(Integer itemId) {
        return cachedItemNames.getOrDefault(itemId, "Name unavailable");
    }

    public void loadImage(Integer itemId, Consumer<AsyncBufferedImage> c) {
        clientThread.invokeLater(() -> {
            c.accept(itemManager.getImage(itemId));
        });
    }

    public int toUnnotedItemId(int itemId) {
        ItemComposition composition = itemManager.getItemComposition(itemId);
        if (composition != null && composition.getNote() != -1 && composition.getLinkedNoteId() > 0) {
            return composition.getLinkedNoteId();
        }
        return itemId;
    }

    public long totalCash(Map<Integer, Integer> itemQuantities) {
        if (itemQuantities == null) {
            return 0L;
        }
        return itemQuantities.getOrDefault(COINS_ITEM_ID, 0)
                + (long) itemQuantities.getOrDefault(PLATINUM_TOKENS_ITEM_ID, 0) * 1000L;
    }

    public Map<Integer, Integer> getRunliteInventory() {
        return buildRuneliteItemMap(InventoryID.INVENTORY);
    }

    public Map<Integer, Integer> getRunliteBankInventory() {
        return buildRuneliteItemMap(InventoryID.BANK);
    }

    private Map<Integer, Integer> buildRuneliteItemMap(InventoryID inventoryID) {
        ItemContainer container = client.getItemContainer(inventoryID);
        if (container == null) {
            return null;
        }
        Map<Integer, Integer> totals = new HashMap<>();
        for (Item item : container.getItems()) {
            if (item == null || item.getId() <= 0 || item.getQuantity() <= 0) {
                continue;
            }
            Integer unnotedTradeableItemId = toUnnotedTradeableItemId(item.getId());
            if (unnotedTradeableItemId == null) {
                continue;
            }
            totals.merge(unnotedTradeableItemId, item.getQuantity(), Integer::sum);
        }
        return totals;
    }

    private Integer toUnnotedTradeableItemId(int itemId) {
        int unnotedItemId = toUnnotedItemId(itemId);
        if (unnotedItemId == COINS_ITEM_ID || unnotedItemId == PLATINUM_TOKENS_ITEM_ID) {
            return unnotedItemId;
        }
        ItemComposition unnoted = itemManager.getItemComposition(unnotedItemId);
        if (unnoted == null || !unnoted.isTradeable()) {
            return null;
        }
        return unnotedItemId;
    }
}
