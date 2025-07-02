package com.flippingcopilot.model;

import com.flippingcopilot.controller.Persistance;
import com.flippingcopilot.ui.FuzzySearchScorer;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Singleton
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SuggestionPreferencesManager {

    private static final String SHARED_PREFERENCES_FILE = "shared_preferences.json";

    // dependencies
    private final Gson gson;
    private final FuzzySearchScorer fuzzySearchScorer;
    private final Client client;
    private final ItemManager itemManager;
    private final ScheduledExecutorService executorService;

    // state
    private SuggestionPreferences sharedPreferences;
    
    public synchronized SuggestionPreferences getPreferences() {
        if (sharedPreferences == null) {
            sharedPreferences = load();

            // Set these false reduce avoid user error
            sharedPreferences.setF2pOnlyMode(false);
            sharedPreferences.setSellOnlyMode(false);
        }
        return sharedPreferences;
    }

    public synchronized void setSellOnlyMode(boolean sellOnlyMode) {
        SuggestionPreferences preferences = getPreferences();
        preferences.setSellOnlyMode(sellOnlyMode);
        saveAsync();
        log.debug("Sell only mode is now: {}", sellOnlyMode);
    }

    public synchronized void setF2pOnlyMode(boolean f2pOnlyMode) {
        SuggestionPreferences preferences = getPreferences();
        preferences.setF2pOnlyMode(f2pOnlyMode);
        saveAsync();
        log.debug("F2p only mode is now: {}", f2pOnlyMode);
    }

    public synchronized void setTimeframe(int minutes) {
        SuggestionPreferences preferences = getPreferences();
        preferences.setTimeframe(minutes);
        saveAsync();
        log.debug("Timeframe is now: {} minutes", minutes);
    }

    public synchronized int getTimeframe() {
        return getPreferences().getTimeframe();
    }

    public synchronized void blockItem(int itemId) {
        SuggestionPreferences preferences = getPreferences();
        List<Integer> blockedList = preferences.getBlockedItemIds();
        if(blockedList == null) {
            blockedList = new ArrayList<>();
        }
        if(!blockedList.contains(itemId)) {
            blockedList.add(itemId);
        }
        preferences.setBlockedItemIds(blockedList);
        saveAsync();
        log.debug("blocked item {}", itemId);
    }

    public synchronized void unblockItem(int itemId) {
        SuggestionPreferences preferences = getPreferences();
        List<Integer> blockedList = preferences.getBlockedItemIds();
        if(blockedList == null) {
            blockedList = new ArrayList<>();
        }
        blockedList.removeIf(i -> i==itemId);
        preferences.setBlockedItemIds(blockedList);
        saveAsync();
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

    private SuggestionPreferences load() {
        File file = getSharedFile();
        if (!file.exists()) {
            SuggestionPreferences merged = mergeExistingPreferences();
            sharedPreferences = merged;
            saveAsync();
            return merged;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return gson.fromJson(reader, SuggestionPreferences.class);
        } catch (FileNotFoundException ignored) {
            return new SuggestionPreferences();
        } catch (JsonSyntaxException | JsonIOException | IOException e) {
            log.warn("error loading preferences json file {}", file, e);
            return new SuggestionPreferences();
        }
    }

    private SuggestionPreferences mergeExistingPreferences() {
        SuggestionPreferences mergedPreferences = new SuggestionPreferences();
        Set<Integer> mergedBlockedItems = new HashSet<>();

        File parentDir = Persistance.PARENT_DIRECTORY;
        if (!parentDir.exists()) {
            return mergedPreferences;
        }

        File[] files = parentDir.listFiles((dir, name) -> name.matches("acc_-?\\d+_preferences\\.json"));
        if (files != null) {
            for (File file : files) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    SuggestionPreferences accountPrefs = gson.fromJson(reader, SuggestionPreferences.class);
                    if (accountPrefs != null && accountPrefs.getBlockedItemIds() != null) {
                        mergedBlockedItems.addAll(accountPrefs.getBlockedItemIds());
                    }
                } catch (Exception e) {
                    log.warn("Error reading preferences file {} during merge", file, e);
                }
            }
        }

        mergedPreferences.setBlockedItemIds(new ArrayList<>(mergedBlockedItems));
        log.info("Merged preferences from {} existing account files", files != null ? files.length : 0);
        return mergedPreferences;
    }

    private void saveAsync() {
        executorService.submit(() -> {
            File file = getSharedFile();
            synchronized (file) {
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
                    String json = gson.toJson(sharedPreferences);
                    writer.write(json);
                    writer.newLine();
                } catch (IOException e) {
                    log.warn("error saving preferences json file {}", file, e);
                }
            }
        });
    }

    private File getSharedFile() {
        return new File(Persistance.PARENT_DIRECTORY, SHARED_PREFERENCES_FILE);
    }
}
