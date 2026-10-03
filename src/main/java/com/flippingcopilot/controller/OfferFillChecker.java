package com.flippingcopilot.controller;

import com.flippingcopilot.model.WikiLatestPrice;
import com.flippingcopilot.model.OfferManager;
import com.flippingcopilot.model.OfferStatus;
import com.flippingcopilot.model.SavedOffer;
import com.flippingcopilot.ui.UIUtilities;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.LongSupplier;

@Slf4j
@RequiredArgsConstructor
public class OfferFillChecker {

    public interface PriceFetcher {
        void fetch(int itemId, Consumer<WikiLatestPrice> consumer);
    }

    private final OfferManager offerManager;
    private final BooleanSupplier enabled;
    private final LongSupplier clock;
    private final Consumer<Runnable> clientInvoke;
    private final PriceFetcher fetcher;
    private final IntFunction<String> itemName;
    private final Consumer<String> notifySink;

    private final Map<String, String> notified = new HashMap<>();

    private volatile boolean sessionActive;
    private volatile long capturedHash;
    private volatile boolean capturedWorldSupported;

    private boolean chainInFlight;

    public static boolean filled(SavedOffer offer, long lastSeen, WikiLatestPrice p) {
        if (p == null) {
            return false;
        }

        boolean sell = offer.getOfferStatus() == OfferStatus.SELL;
        Long price = sell ? p.getHigh() : p.getLow();
        Long time = sell ? p.getHighTime() : p.getLowTime();
        if (price == null || time == null || time <= lastSeen) {
            return false;
        }

        return sell ? price > offer.getPrice() : price < offer.getPrice();
    }

    public void onOsrsLoggedIn(long accountHash, boolean worldSupported) {
        if (sessionActive && capturedWorldSupported && !worldSupported) {
            stampCapturedSeen();
        }
        capturedHash = accountHash;
        capturedWorldSupported = worldSupported;
        sessionActive = true;
    }

    public void onOsrsLoginScreen() {
        if (sessionActive && capturedWorldSupported) {
            stampCapturedSeen();
        }
        sessionActive = false;
    }

    public void onSessionEndShutdown() {
        if (sessionActive && capturedWorldSupported) {
            stampCapturedSeen();
        }
        offerManager.flushSeen();
        sessionActive = false;
    }

    private void stampCapturedSeen() {
        offerManager.stampSeen(capturedHash, clock.getAsLong(), null);
    }

    private long currentAccountHash() {
        return sessionActive ? capturedHash : -1L;
    }

    public void poll() {
        try {
            if (sessionActive && capturedWorldSupported) {
                stampCapturedSeen();
            }

            if (!enabled.getAsBoolean()) {
                return;
            }

            OfferManager.EnumerationResult enumeration = offerManager.listRestingOffers();
            clientInvoke.accept(() -> reconcileAndFetch(enumeration));
        } catch (Throwable t) {
            log.warn("offer fill check failed", t);
        }
    }

    private void reconcileAndFetch(OfferManager.EnumerationResult enumeration) {
        if (chainInFlight) {
            return;
        }
        long current = currentAccountHash();
        long now = clock.getAsLong();
        Map<Integer, List<OfferManager.RestingOffer>> byItem = new HashMap<>();
        Set<String> stillResting = new HashSet<>();

        for (OfferManager.RestingOffer ro : enumeration.resting) {
            stillResting.add(ro.fingerprint());
            if (ro.accountHash != current && ro.lastSeen <= now - 300 && ro.lastSeen > now - 3 * 24 * 3600 && !notified.containsKey(ro.fingerprint())) {
                byItem.computeIfAbsent(ro.offer.getItemId(), k -> new ArrayList<>()).add(ro);
            }
        }

        notified.entrySet().removeIf(e -> enumeration.resolvedSlotKeys.contains(e.getValue()) && !stillResting.contains(e.getKey()));
        chainInFlight = true;
        fetchNext(new ArrayList<>(byItem.entrySet()), 0);
    }

    private void fetchNext(List<Map.Entry<Integer, List<OfferManager.RestingOffer>>> queue, int i) {
        if (i >= queue.size()) {
            chainInFlight = false;
            return;
        }

        Map.Entry<Integer, List<OfferManager.RestingOffer>> e = queue.get(i);
        fetcher.fetch(e.getKey(), p -> clientInvoke.accept(() -> {
            try {
                onLatest(e.getValue(), p);
            } catch (RuntimeException ex) {
                log.warn("offer fill notification failed", ex);
            }
            fetchNext(queue, i + 1);
        }));
    }

    private void onLatest(List<OfferManager.RestingOffer> offers, WikiLatestPrice p) {
        if (p == null) {
            return;
        }
        long current = currentAccountHash();
        for (OfferManager.RestingOffer ro : offers) {
            if (ro.accountHash == current || notified.containsKey(ro.fingerprint()) || !filled(ro.offer, ro.lastSeen, p)) {
                continue;
            }
            notified.put(ro.fingerprint(), ro.slotKey());
            notifySink.accept(buildMessage(ro.offer, itemName.apply(ro.offer.getItemId()), ro.accountName));
        }
    }

    private static String buildMessage(SavedOffer offer, String itemName, String accountName) {
        return String.format("Flipping Copilot: %s likely %s @ %s%s",
                itemName,
                offer.getOfferStatus() == OfferStatus.SELL ? "sold" : "bought",
                UIUtilities.quantityToRSDecimalStack(offer.getPrice(), false),
                accountName == null || accountName.isEmpty() ? "" : " (" + accountName + ")");
    }
}
