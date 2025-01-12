package com.flippingcopilot.controller;
import com.flippingcopilot.model.*;
import com.flippingcopilot.ui.GpDropOverlay;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.time.Instant;
import java.util.*;
import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.OSType;
import net.runelite.http.api.worlds.WorldType;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class GrandExchangeOfferEventHandler {
    private static final int GE_LOGIN_BURST_WINDOW = 2; // ticks

    // dependencies
    private final Client client;
    private final OfferManager offerPersistence;
    private final GrandExchange grandExchange;
    private final TransactionManger transactionManager;
    private final OsrsLoginManager osrsLoginManager;
    private final OverlayManager overlayManager;
    private final GrandExchangeUncollectedManager grandExchangeUncollectedManager;
    private final OfferManager offerManager;
    private final SuggestionManager suggestionManager;

    // state
    private final List<Transaction> transactionsToProcess = new ArrayList<>();
    private int lastLoginTick;
    private String machineUuid;
    private long lastAccountHash;


    public void onGameTick() {
        if(!transactionsToProcess.isEmpty()) {
            processTransactions();
        }
    }

    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged offerEvent) {
        final int slot = offerEvent.getSlot();
        final GrandExchangeOffer offer = offerEvent.getOffer();
        Long accountHash = client.getAccountHash();

        if (offer.getState() == GrandExchangeOfferState.EMPTY && client.getGameState() != GameState.LOGGED_IN) {
            // Trades are cleared by the client during LOGIN_SCREEN/HOPPING/LOGGING_IN, ignore those
            return;
        }

        log.debug("tick {} GE offer updated: state: {}, slot: {}, item: {}, qty: {}, lastLoginTick: {}", client.getTickCount(), offer.getState(), slot, offer.getItemId(), offer.getQuantitySold(), lastLoginTick);

        SavedOffer o = SavedOffer.fromGrandExchangeOffer(offer);

        SavedOffer prev = offerPersistence.loadOffer(accountHash, slot);

        if(Objects.equals(o, prev)) {
            log.debug("skipping duplicate offer event");
            return;
        }

        o.setCopilotPriceUsed(wasCopilotPriceUsed(o, prev));

        boolean consistent = isConsistent(prev, o);
        if(!consistent) {
            log.warn("offer on slot {} is inconsistent with previous saved offer", slot);
        }

        if(hasSlotBecomeFree(o, prev, consistent)) {
            suggestionManager.setSuggestionNeeded(true);
        }

        Transaction t = inferTransaction( slot, o, prev, consistent);
        if(t != null) {
            transactionsToProcess.add(t);
            processTransactions();
            suggestionManager.setSuggestionNeeded(true);
            log.debug("inferred transaction {}", t);
        }
        updatedUncollected(accountHash, slot, o, prev, consistent);
        offerPersistence.saveOffer(accountHash, slot, o);
    }

    private boolean hasSlotBecomeFree(SavedOffer offer, SavedOffer prev, boolean consistent) {
        return offer.isFreeSlot() && (!consistent || !prev.isFreeSlot());
    }

    private boolean wasCopilotPriceUsed(SavedOffer o, SavedOffer prev) {
        if(isNewOffer(o, prev)){
            return o.getItemId() == offerManager.getLastViewedSlotItemId() && o.getPrice() == offerManager.getLastViewedSlotItemPrice() && Instant.now().minusSeconds(30).getEpochSecond() < offerManager.getLastViewedSlotPriceTime();
        } else {
            return prev.isCopilotPriceUsed();
        }
    }

    private void updatedUncollected(Long accountHash, int slot, SavedOffer o, SavedOffer prev, boolean consistent) {
        if(!consistent) {
            return;
        }
        int uncollectedGp = 0;
        int uncollectedItems = 0;
        switch (o.getState()) {
            case BUYING:
            case BOUGHT:
                uncollectedItems = isNewOffer(prev, o) ? o.getQuantitySold() : o.getQuantitySold() - prev.getQuantitySold();
                break;
            case SOLD:
            case SELLING:
                uncollectedGp = (isNewOffer(prev, o) ? o.getQuantitySold() : o.getQuantitySold() - prev.getQuantitySold()) * o.getPrice();
                break;
            case CANCELLED_BUY:
                uncollectedGp = (o.getTotalQuantity() - o.getQuantitySold()) * o.getPrice();
                break;
            case CANCELLED_SELL:
                uncollectedItems = o.getTotalQuantity() - o.getQuantitySold();
                break;
            case EMPTY:
                // if the slot is empty we want to ensure that the un collected manager doesn't think there is something to collect
                // this can happen due to race conditions between the collection and offer fills timing
                grandExchangeUncollectedManager.ensureSlotClear(accountHash, slot);
                suggestionManager.setSuggestionNeeded(true);
                return;
        }
        grandExchangeUncollectedManager.addUncollected(accountHash, slot, o.getItemId(), uncollectedItems, uncollectedGp);

    }

    private void processTransactions() {
        String displayName = osrsLoginManager.getPlayerDisplayName();
        if(displayName != null) {
            for (Transaction transaction: transactionsToProcess) {
                long profit = transactionManager.addTransaction(transaction, displayName);
                if (grandExchange.isHomeScreenOpen() && profit != 0) {
                    new GpDropOverlay(overlayManager, client, profit, transaction.getBoxId());
                }
            }
            transactionsToProcess.clear();
        }
    }

    public Transaction inferTransaction(int slot, SavedOffer offer, SavedOffer prev, boolean consistent) {
        boolean login = client.getTickCount() <= lastLoginTick + GE_LOGIN_BURST_WINDOW;
        boolean isNewOffer = isNewOffer(prev, offer);
        int quantityDiff = isNewOffer ? offer.getQuantitySold() : offer.getQuantitySold() - prev.getQuantitySold();
        int amountSpentDiff = isNewOffer ? offer.getSpent() : offer.getSpent() - prev.getSpent();
        if (quantityDiff > 0 && amountSpentDiff > 0) {
            Transaction t = new Transaction();
            t.setId(UUID.randomUUID());
            t.setType(offer.getOfferStatus());
            t.setItemId(offer.getItemId());
            t.setPrice(offer.getPrice());
            t.setQuantity(quantityDiff);
            t.setBoxId(slot);
            t.setAmountSpent(amountSpentDiff);
            t.setTimestamp(Instant.now());
            t.setCopilotPriceUsed(true);
            t.setOfferTotalQuantity(offer.getTotalQuantity());
            t.setLogin(login);
            t.setMachineID("");
            t.setConsistent(consistent);
            return t;
        }
        return null;
    }

    private boolean isConsistent(SavedOffer prev, SavedOffer updated) {
        if(prev == null) {
            return false;
        }
        if(updated.getState() == GrandExchangeOfferState.EMPTY) {
            return true;
        }
        if(prev.getState() == GrandExchangeOfferState.EMPTY && !(updated.getState() == GrandExchangeOfferState.CANCELLED_BUY || updated.getState() == GrandExchangeOfferState.CANCELLED_SELL)) {
            return true;
        }
        return prev.getOfferStatus() == updated.getOfferStatus() ||
                prev.getItemId() == updated.getItemId()
                || prev.getPrice() == updated.getPrice()
                || prev.getTotalQuantity() == updated.getTotalQuantity();
    }

    private boolean isNewOffer(SavedOffer prev, SavedOffer updated) {
        if (prev == null) {
            return true;
        }
        return prev.getOfferStatus() != updated.getOfferStatus() ||
                prev.getItemId() != updated.getItemId()
                || prev.getPrice() != updated.getPrice()
                || prev.getTotalQuantity() != updated.getTotalQuantity()
                || prev.getQuantitySold() > updated.getQuantitySold()
                || prev.getSpent() > updated.getSpent();
    }

    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        switch (gameStateChanged.getGameState())
        {
            case LOGIN_SCREEN:
                break;
            case LOGGING_IN:
            case HOPPING:
            case CONNECTION_LOST:
                lastLoginTick = client.getTickCount();
                break;
            case LOGGED_IN:
        }
    }
}
