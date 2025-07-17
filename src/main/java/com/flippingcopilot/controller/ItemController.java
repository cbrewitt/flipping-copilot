package com.flippingcopilot.controller;

import com.flippingcopilot.ui.FuzzySearchScorer;
import com.flippingcopilot.util.ClientThreadUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ItemController {
    private static final int NAME_CHAR_LIMIT = 25;

    // dependencies
    private final Client client;
    private final ClientThreadUtil clientThreadUtil;
    private final ItemManager itemManager;
    private final FuzzySearchScorer fuzzySearchScorer;

    // state
    private final Map<Integer, String> cachedItemNames = new ConcurrentHashMap<>();

    public List<Pair<Integer, String>> search(String input, Set<Integer> existingSelectedItems) {
        if(!client.isClientThread()) {
            return clientThreadUtil.executeInClientThread(() -> search(input, existingSelectedItems));
        }
        if(input == null || input.isBlank()) {
            return IntStream.range(0, client.getItemCount())
                    .mapToObj(itemManager::getItemComposition)
                    .filter(item -> item.isTradeable() && item.getNote() == -1)
                    .sorted(Comparator.comparing((ItemComposition i) -> !existingSelectedItems.contains(i.getId())).thenComparing(ItemComposition::getName))
                    .map((i) -> Pair.of(i.getId(), trimName(i.getName())))
                    .collect(Collectors.toList());
        }

        ToDoubleFunction<ItemComposition> comparator = fuzzySearchScorer.comparator(input);
        return IntStream.range(0, client.getItemCount())
                .mapToObj(itemManager::getItemComposition)
                .filter(item -> item.isTradeable() && item.getNote() == -1)
                .filter(item -> comparator.applyAsDouble(item) > 0)
                .sorted(Comparator.comparing((ItemComposition i) -> !existingSelectedItems.contains(i.getId())).thenComparing(Comparator.comparingDouble(comparator).reversed()
                        .thenComparing(ItemComposition::getName)))
                .map((i) -> Pair.of(i.getId(), trimName(i.getName())))
                .collect(Collectors.toList());
    }


    private String trimName(String name) {
        if(name.length() > NAME_CHAR_LIMIT) {
            return name.substring(0, NAME_CHAR_LIMIT - 1) + "..";
        }
        return name;
    }

    public Set<Integer> allItemIds() {
        if(!client.isClientThread()) {
            return clientThreadUtil.executeInClientThread(this::allItemIds);
        }
        return IntStream.range(0, client.getItemCount())
                .mapToObj(itemManager::getItemComposition)
                .filter(item -> item.isTradeable() && item.getNote() == -1)
                .map(ItemComposition::getId).collect(Collectors.toSet());
    }

    public String getItemName(Integer itemId) {
        if (cachedItemNames.containsKey(itemId)) {
            return cachedItemNames.get(itemId);
        }
        loadItemNames(itemId);
        return cachedItemNames.getOrDefault(itemId, "Name unavailable");
    }

    private int loadItemNames(Integer missing) {
        log.debug("loading item names");
        if(!client.isClientThread()) {
            return clientThreadUtil.executeInClientThread(() -> loadItemNames(missing));
        }
        // someone else beat us to it...
        if(cachedItemNames.containsKey(missing)) {
            return 0;
        }
        Map<Integer, String> itemNames = IntStream.range(0, client.getItemCount())
                .mapToObj(itemManager::getItemComposition)
                .collect(Collectors.toMap(ItemComposition::getId, ItemComposition::getName));
        cachedItemNames.putAll(itemNames);
        log.debug("loaded item names");
        return 0;
    }
}
