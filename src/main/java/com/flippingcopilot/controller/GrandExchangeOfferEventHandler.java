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
import net.runelite.client.ui.overlay.OverlayManager;

import static com.flippingcopilot.model.OsrsLoginManager.GE_LOGIN_BURST_WINDOW;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class GrandExchangeOfferEventHandler {

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
            log.debug("inferred transaction {}", t);
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
}
