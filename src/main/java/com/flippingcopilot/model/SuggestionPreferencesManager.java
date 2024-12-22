package com.flippingcopilot.model;

import com.flippingcopilot.controller.Persistance;
import com.flippingcopilot.ui.FuzzySearchScorer;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.util.*;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


@Singleton
@Slf4j
public class SuggestionPreferencesManager {

    private static final String SUGGESTION_PREFERENCES_FILE_TEMPLATE = "acc_%d_preferences.json";

    // dependencies
    private final Gson gson;
    private final OsrsLoginManager osrsLoginManager;
    private final FuzzySearchScorer fuzzySearchScorer;
    private final Client client;
    private final ItemManager itemManager;

    // state
    private final Map<Long, SuggestionPreferences> cached = new HashMap<>();

    @Inject
    public SuggestionPreferencesManager(Gson gson, OsrsLoginManager osrsLoginManager, FuzzySearchScorer fuzzySearchScorer, Client client, ItemManager itemManager) {
        this.gson = gson;
        this.osrsLoginManager = osrsLoginManager;
        this.fuzzySearchScorer = fuzzySearchScorer;
        this.client = client;
        this.itemManager = itemManager;
    }

    public synchronized SuggestionPreferences getPreferences() {
        Long accountHash = osrsLoginManager.getAccountHash();
        return cached.computeIfAbsent(accountHash, this::load);
    }

    public synchronized void setSellOnlyMode(boolean sellOnlyMode) {
        Long accountHash = osrsLoginManager.getAccountHash();
        SuggestionPreferences preferences = cached.computeIfAbsent(accountHash, this::load);
        preferences.setSellOnlyMode(sellOnlyMode);
        save(accountHash, preferences);
        log.debug("Sell only mode is now: {}", sellOnlyMode);
    }

    public synchronized void blockItem(int itemId) {
        Long accountHash = osrsLoginManager.getAccountHash();
        SuggestionPreferences preferences = cached.computeIfAbsent(accountHash, this::load);
        List<Integer> blockedList = preferences.getBlockedItemIds();
        if(blockedList == null) {
            blockedList = new ArrayList<>();
        }
        if(!blockedList.contains(itemId)) {
            blockedList.add(itemId);
        }
        preferences.setBlockedItemIds(blockedList);
        save(accountHash, preferences);
        log.debug("blocked item {}", itemId);
    }

    public synchronized void unblockItem(int itemId) {
        Long accountHash = osrsLoginManager.getAccountHash();
        SuggestionPreferences preferences = cached.computeIfAbsent(accountHash, this::load);
        List<Integer> blockedList = preferences.getBlockedItemIds();
        if(blockedList == null) {
            blockedList = new ArrayList<>();
        }
        blockedList.removeIf(i -> i==itemId);
        preferences.setBlockedItemIds(blockedList);
        save(accountHash, preferences);
        log.debug("unblocked item {}", itemId);
    }

    public List<Pair<Integer, String>> search(String input) {
        Set<Integer> blockedItems = new HashSet<>(blockedItems());
        if(input == null || input.isBlank()) {
            return IntStream.range(0, client.getItemCount())
                    .mapToObj(itemManager::getItemComposition)
                    .filter(item -> item.isTradeable() && item.getNote() == -1)
                    .sorted(Comparator.comparing((ItemComposition i) -> !blockedItems.contains(i.getId())).thenComparing(ItemComposition::getName))
                    .limit(250)
                    .map((i) -> Pair.of(i.getId(), trimName(i.getName())))
                    .collect(Collectors.toList());
        }

        ToDoubleFunction<ItemComposition> comparator = fuzzySearchScorer.comparator(input);
        return IntStream.range(0, client.getItemCount())
            .mapToObj(itemManager::getItemComposition)
            .filter(item -> item.isTradeable() && item.getNote() == -1)
            .filter(item -> comparator.applyAsDouble(item) > 0)
            .sorted(Comparator.comparing((ItemComposition i) -> !blockedItems.contains(i.getId())).thenComparing(Comparator.comparingDouble(comparator).reversed()
                    .thenComparing(ItemComposition::getName)))
            .limit(250)
            .map((i) -> Pair.of(i.getId(), trimName(i.getName())))
            .collect(Collectors.toList());
    }

    public List<Integer> blockedItems() {
        return getPreferences().getBlockedItemIds();
    }

    private String trimName(String name) {
        if(name.length() > 23) {
            return name.substring(0, 23) + "..";
        }
        return name;
    }

    private SuggestionPreferences load(Long accountHash) {
        File file = getFile(accountHash);
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return gson.fromJson(reader, SuggestionPreferences.class);
        } catch (FileNotFoundException ignored) {
            return new SuggestionPreferences();
        } catch (JsonSyntaxException | JsonIOException | IOException e) {
            log.warn("error loading preferences json file {}", file, e);
            return new SuggestionPreferences();
        }
    }

    private void save(Long accountHash, SuggestionPreferences p) {
        File file = getFile(accountHash);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
            String json = gson.toJson(p);
            writer.write(json);
            writer.newLine();
        } catch (IOException e) {
            log.warn("error saving preferences json file {}", file, e);
        }
    }

    private File getFile(Long accountHash) {
        return new File(Persistance.PARENT_DIRECTORY, String.format(SUGGESTION_PREFERENCES_FILE_TEMPLATE, accountHash));
    }
}
