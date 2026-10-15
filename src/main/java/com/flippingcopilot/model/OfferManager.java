package com.flippingcopilot.model;

import com.flippingcopilot.controller.Persistance;
import com.google.gson.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.nio.file.NoSuchFileException;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;


@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class OfferManager {

    private static final String OFFER_FILE_TEMPLATE = "acc_%d_%d.json";

    private final Persistance persistance;

    // dependencies
    private final Gson gson;
    private final ScheduledExecutorService executorService;

    // state
    @Getter
    @Setter
    private int lastViewedSlotItemId = -1;
    @Getter
    @Setter
    private long lastViewedSlotItemPrice = -1;
    @Getter
    @Setter
    private int lastViewedSlotPriceTime = 0;
    @Getter
    @Setter
    private int viewedSlotItemId = -1;
    @Getter
    @Setter
    private long viewedSlotItemPrice = -1;
    @Getter
    @Setter
    boolean offerJustPlaced = false;

    private final Map<Long, Map<Integer, SavedOffer>> cachedOffers = new HashMap<>();
    private final Map<Long, Map<Integer, Filepath>> files = new HashMap<>();
    private final Map<Long, Map<Integer, SavedOffer>> lastSaved = new HashMap<>();

    public synchronized SavedOffer loadOffer(Long accountHash, Integer slot) {
        Map<Integer, SavedOffer> slotToOffer = cachedOffers.computeIfAbsent(accountHash, (k) -> new HashMap<>());
        return slotToOffer.computeIfAbsent(slot, (k) -> {
            Filepath file = getFile(accountHash, k);
            try (BufferedReader reader = file.openBufferedReader()) {
                return gson.fromJson(reader, SavedOffer.class);
            } catch (NoSuchFileException ignored) {
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
        Filepath file = getFile(accountHash,slot);
        synchronized (file) {
            SavedOffer offer = loadOffer(accountHash, slot);
            Map<Integer, SavedOffer> slotToLastSaved = lastSaved.computeIfAbsent(accountHash, (k)->new HashMap<>());
            SavedOffer lastSaved = slotToLastSaved.get(slot);
            if(!Objects.equals(offer, lastSaved)) {
                try (BufferedWriter writer = file.openBufferedWriter()) {
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


    private Filepath getFile(Long accountHash, Integer slot) {
        Map<Integer, Filepath> slotToFile = files.computeIfAbsent(accountHash, (k) -> new HashMap<>());
        return slotToFile.computeIfAbsent(slot, (k) -> persistance.getDirectory().joinSegment(String.format(OFFER_FILE_TEMPLATE, accountHash, slot)));
    }
}
