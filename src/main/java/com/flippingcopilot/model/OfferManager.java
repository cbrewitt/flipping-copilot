package com.flippingcopilot.model;

import com.flippingcopilot.controller.Persistance;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.util.HashMap;
import java.util.Map;


@Slf4j
@Singleton
public class OfferManager {

    private static final String OFFER_FILE_TEMPLATE = "acc_%d_%d.json";

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
    private final Gson gson;

    @Inject
    public OfferManager(Gson gson) {
        this.gson = gson;
    }

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
        File file = getFile(accountHash,slot);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
            String json = gson.toJson(offer);
            writer.write(json);
            writer.newLine();
        } catch (IOException e) {
            log.warn("error saving offer json file {}", file, e);
        }
    }

    private File getFile(Long accountHash, Integer slot) {
        return new File(Persistance.PARENT_DIRECTORY, String.format(OFFER_FILE_TEMPLATE, accountHash, slot));
    }
}
