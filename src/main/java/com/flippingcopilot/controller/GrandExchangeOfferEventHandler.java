package com.flippingcopilot.controller;
import com.flippingcopilot.model.*;
import com.flippingcopilot.ui.GpDropOverlay;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.client.ui.overlay.OverlayManager;

import static com.flippingcopilot.model.OsrsLoginManager.GE_LOGIN_BURST_WINDOW;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class GrandExchangeOfferEventHandler {

    private static final int GE_SLOTS = 8;
    // dependencies
    private final Client client;
    private final OfferManager offerPersistence;
    private final GrandExchange grandExchange;
    private final TransactionManager transactionManager;
    private final OsrsLoginManager osrsLoginManager;
    private final OverlayManager overlayManager;
    private final GrandExchangeUncollectedManager grandExchangeUncollectedManager;
    private final OfferManager offerManager;
    private final SuggestionManager suggestionManager;

    // state
    private final Queue<Transaction> transactionsToProcess = new ConcurrentLinkedQueue<>();

    public void onGameTick() {
        if(!transactionsToProcess.isEmpty()) {
            processTransactions();
        }
    }

    public void onScriptPreFired(ScriptPreFired e) {
        if (e.getScriptId() == 797) {
            int slot = (int) e.getScriptEvent().getArguments()[10]; // 9th argument
            if (slot < 0 || slot >= GE_SLOTS) {
                log.error("Invalid GE slot {}", slot);
                return;
            }
            GrandExchangeOffer[] grandExchangeOffers = client.getGrandExchangeOffers();
            GrandExchangeOffer offer = grandExchangeOffers[slot];
            if (offer.getState() == GrandExchangeOfferState.EMPTY) {
                GrandExchangeOffer sinkOffer = getSinkOffer(slot);
                // This might go from sink offer -> no offer, in which case we want to still record that
                // (and there will be no GrandExchangeOfferChanged event)
                if (sinkOffer != null) {
                    offer = sinkOffer;
                }
            }
            GrandExchangeOfferChanged o = new GrandExchangeOfferChanged();
            o.setSlot(slot);
            o.setOffer(offer);
            onGrandExchangeOfferChanged(o);
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

        log.debug("tick {} GE offer updated: state: {}, slot: {}, item: {}, qty: {}, lastLoginTick: {}", client.getTickCount(), offer.getState(), slot, offer.getItemId(), offer.getQuantitySold(), osrsLoginManager.getLastLoginTick());

        SavedOffer o = SavedOffer.fromGrandExchangeOffer(offer);

        SavedOffer prev = offerPersistence.loadOffer(accountHash, slot);

        if(Objects.equals(o, prev)) {
            log.debug("skipping duplicate offer event {}", o);
            return;
        }

        o.setCopilotPriceUsed(wasCopilotPriceUsed(o, prev));
        o.setWasCopilotSuggestion(wasCopilotSuggestion(o, prev));

        boolean consistent = isConsistent(prev, o);
        if(!consistent) {
            log.warn("offer on slot {} is inconsistent with previous saved offer", slot);
        }

        Transaction t = inferTransaction(slot, o, prev, consistent);
        if(t != null) {
            transactionsToProcess.add(t);
            processTransactions();
            if (offerEvent.getOffer() instanceof SinkGrandExchangeOffer) {
                log.info("inferred sink transaction {}", t);
            } else {
                log.debug("inferred transaction {}", t);

            }
        }
        updateUncollected(accountHash, slot, o, prev, consistent);
        offerPersistence.saveOffer(accountHash, slot, o);

        // Always fetch suggestion to ensure fast response for better UX
        suggestionManager.setSuggestionNeeded(true);
    }

    private boolean wasCopilotPriceUsed(SavedOffer o, SavedOffer prev) {
        if(isNewOffer(prev, o)){
            return o.getItemId() == offerManager.getLastViewedSlotItemId() && o.getPrice() == offerManager.getLastViewedSlotItemPrice() && Instant.now().minusSeconds(30).getEpochSecond() < offerManager.getLastViewedSlotPriceTime();
        } else {
            return prev.isCopilotPriceUsed();
        }
    }

    private boolean wasCopilotSuggestion(SavedOffer o, SavedOffer prev) {
        if(isNewOffer(prev, o)){
            return o.getItemId() == suggestionManager.getSuggestionItemIdOnOfferSubmitted() && o.getOfferStatus().equals(suggestionManager.getSuggestionOfferStatusOnOfferSubmitted());
        } else {
            return prev.isWasCopilotSuggestion();
        }
    }

    private void updateUncollected(Long accountHash, int slot, SavedOffer o, SavedOffer prev, boolean consistent) {
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
            Transaction transaction;
            while ((transaction = transactionsToProcess.poll()) != null) {
                long profit = transactionManager.addTransaction(transaction, displayName);
                if (grandExchange.isHomeScreenOpen() && profit != 0) {
                    new GpDropOverlay(overlayManager, client, profit, transaction.getBoxId());
                }
            }
        }
    }

    public Transaction inferTransaction(int slot, SavedOffer offer, SavedOffer prev, boolean consistent) {
        boolean login = client.getTickCount() <= osrsLoginManager.getLastLoginTick() + GE_LOGIN_BURST_WINDOW;
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
            t.setCopilotPriceUsed(offer.isCopilotPriceUsed());
            t.setWasCopilotSuggestion(offer.isWasCopilotSuggestion());
            t.setOfferTotalQuantity(offer.getTotalQuantity());
            t.setLogin(login);
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

    private GrandExchangeOffer getSinkOffer(int slot)
    {
        int vid, vprice;
        switch (slot)
        {
            case 0:
                vid = 3204; // GE_SINK_SLOT0_ITEM
                vprice = 3205; // GE_SINK_SLOT0_PRICE
                break;
            case 1:
                vid = 3206; // GE_SINK_SLOT1_ITEM
                vprice = 3207; // GE_SINK_SLOT1_PRICE
                break;
            case 2:
                vid = 3208; // GE_SINK_SLOT2_ITEM
                vprice = 3209; // GE_SINK_SLOT2_PRICE
                break;
            case 3:
                vid = 3210; // GE_SINK_SLOT3_ITEM
                vprice = 3211; // GE_SINK_SLOT3_PRICE
                break;
            case 4:
                vid = 3212; // GE_SINK_SLOT4_ITEM
                vprice = 3213; // GE_SINK_SLOT4_PRICE
                break;
            case 5:
                vid = 3214; // GE_SINK_SLOT5_ITEM
                vprice = 3215; // GE_SINK_SLOT5_PRICE
                break;
            case 6:
                vid = 3216; // GE_SINK_SLOT6_ITEM
                vprice = 3217; // GE_SINK_SLOT6_PRICE
                break;
            case 7:
                vid = 3218; // GE_SINK_SLOT7_ITEM
                vprice = 3219; // GE_SINK_SLOT7_PRICE
                break;
            default:
                throw new IllegalArgumentException();
        }
        int id = client.getVar(vid);
        int price = client.getVar(vprice);
        return id == -1 ? null : new SinkGrandExchangeOffer(id, Math.max(0, price));
    }

}
