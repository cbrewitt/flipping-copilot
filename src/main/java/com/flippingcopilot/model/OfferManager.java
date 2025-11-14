package com.flippingcopilot.model;

import com.flippingcopilot.controller.Persistance;
import com.flippingcopilot.controller.FlippingCopilotPlugin;
import com.flippingcopilot.ui.SlotActivityTimer;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;


@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class OfferManager {

    private static final String OFFER_FILE_TEMPLATE = "acc_%d_%d.json";

    // dependencies
    private final Gson gson;
    private final ScheduledExecutorService executorService;

    // state
    @Getter
    @Setter
    private int lastViewedSlotItemId = -1;
    @Getter
    @Setter
    private int lastViewedSlotItemPrice = -1;
    @Getter
    @Setter
    private int lastViewedSlotPriceTime = 0;
    @Getter
    @Setter
    private int viewedSlotItemId = -1;
    @Getter
    @Setter
    private int viewedSlotItemPrice = -1;
    @Getter
    @Setter
    boolean offerJustPlaced = false;

    private final Map<Long, Map<Integer, SavedOffer>> cachedOffers = new HashMap<>();
    private final Map<Long, Map<Integer, File>> files = new HashMap<>();
    private final Map<Long, Map<Integer, SavedOffer>> lastSaved = new HashMap<>();

    @Getter
    private final List<SlotActivityTimer> slotTimers = new ArrayList<>();

    public synchronized SavedOffer loadOffer(Long accountHash, Integer slot) {
        Map<Integer, SavedOffer> slotToOffer = cachedOffers.computeIfAbsent(accountHash, (k) -> new HashMap<>());
        return slotToOffer.computeIfAbsent(slot, (k) -> {
            File file = getFile(accountHash, k);
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                return gson.fromJson(reader, SavedOffer.class);
            } catch (FileNotFoundException ignored) {
                return null;
            } catch (JsonSyntaxException | JsonIOException | IOException e) {
                log.warn("error loading saved offer json file {}", file, e);
                return null;
            }
        });
    }

    public synchronized void saveOffer(Long accountHash, Integer slot, SavedOffer offer) {
        Map<Integer, SavedOffer> slotToOffer = cachedOffers.computeIfAbsent(accountHash, (k) -> new HashMap<>());
        slotToOffer.put(slot, offer);
        saveAsync(accountHash, slot);
    }

    private void saveAsync(Long accountHash, Integer slot) {
        executorService.submit(() -> save(accountHash, slot));
    }

    public synchronized void saveAll() {
        for(Long accountHash: cachedOffers.keySet()) {
            for(Integer slot: cachedOffers.get(accountHash).keySet()) {
                save(accountHash, slot);
            }
        }
    }

    private void save(Long accountHash, Integer slot) {
        File file = getFile(accountHash,slot);
        synchronized (file) {
            SavedOffer offer = loadOffer(accountHash, slot);
            Map<Integer, SavedOffer> slotToLastSaved = lastSaved.computeIfAbsent(accountHash, (k)->new HashMap<>());
            SavedOffer lastSaved = slotToLastSaved.get(slot);
            if(!Objects.equals(offer, lastSaved)) {
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
                    String json = gson.toJson(offer);
                    writer.write(json);
                    writer.newLine();
                    slotToLastSaved.put(slot, offer);
                } catch (IOException e) {
                    log.warn("error saving offer json file {}", file, e);
                    slotToLastSaved.put(slot, null);
                }
            }
        }
    }


    private File getFile(Long accountHash, Integer slot) {
        Map<Integer, File> slotToFile = files.computeIfAbsent(accountHash, (k) -> new HashMap<>());
        return slotToFile.computeIfAbsent(slot, (k) -> new File(Persistance.COPILOT_DIR, String.format(OFFER_FILE_TEMPLATE, accountHash, slot)));
    }

    /**
     * Initializes slot timers. Should be called during plugin startup.
     */
    public void initializeSlotTimers(FlippingCopilotPlugin plugin, Client client, SuggestionPreferencesManager preferencesManager) {
        if (slotTimers.isEmpty()) {
            for (int i = 0; i < 8; i++) {
                SlotActivityTimer timer = new SlotActivityTimer(i);
                timer.setClient(client);
                timer.setPlugin(plugin);
                timer.setPreferencesManager(preferencesManager);
                slotTimers.add(timer);
            }
            log.debug("Initialized {} slot timers", slotTimers.size());
        }
    }

    /**
     * Gets the timer for a specific slot.
     */
    public SlotActivityTimer getSlotTimer(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < slotTimers.size()) {
            return slotTimers.get(slotIndex);
        }
        return null;
    }
}
