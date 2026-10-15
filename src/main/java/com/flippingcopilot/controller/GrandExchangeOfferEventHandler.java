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
import net.runelite.api.*;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.gameval.VarPlayerID;
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
    private final ItemSinkTracker itemSinkTracker;

    // a sale to the GE tax item sink is drawn from these per-slot varps, never from the slot's offer
    private static final int[] ITEM_SINK_OBJ = {
        VarPlayerID.GE_ITEMSINK_OBJ_0, VarPlayerID.GE_ITEMSINK_OBJ_1, VarPlayerID.GE_ITEMSINK_OBJ_2, VarPlayerID.GE_ITEMSINK_OBJ_3,
        VarPlayerID.GE_ITEMSINK_OBJ_4, VarPlayerID.GE_ITEMSINK_OBJ_5, VarPlayerID.GE_ITEMSINK_OBJ_6, VarPlayerID.GE_ITEMSINK_OBJ_7,
    };
    private static final int[] ITEM_SINK_PRICE = {
        VarPlayerID.GE_ITEMSINK_PRICE_LONG_0, VarPlayerID.GE_ITEMSINK_PRICE_LONG_1, VarPlayerID.GE_ITEMSINK_PRICE_LONG_2, VarPlayerID.GE_ITEMSINK_PRICE_LONG_3,
        VarPlayerID.GE_ITEMSINK_PRICE_LONG_4, VarPlayerID.GE_ITEMSINK_PRICE_LONG_5, VarPlayerID.GE_ITEMSINK_PRICE_LONG_6, VarPlayerID.GE_ITEMSINK_PRICE_LONG_7,
    };

    // state
    private final Queue<Transaction> transactionsToProcess = new ConcurrentLinkedQueue<>();

    public void onGameTick() {
        checkItemSink();
        if(!transactionsToProcess.isEmpty()) {
            processTransactions();
        }
    }

    /**
     * The GE tax item sink buys one item the moment a sell offer is posted. The slot's offer stays EMPTY and the
     * game shows the sale from the ge_itemsink varps instead, so no GrandExchangeOfferChanged event ever fires
     * for it: watch the varps and book the sale here.
     */
    private void checkItemSink() {
        if (client.getGameState() != GameState.LOGGED_IN || osrsLoginManager.isUnsupportedWorldType()) {
            return;
        }
        GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
        boolean loginBurst = osrsLoginManager.hasJustLoggedIn();
        for (int slot = 0; slot < ITEM_SINK_OBJ.length; slot++) {
            int itemId = client.getVarpValue(ITEM_SINK_OBJ[slot]);
            switch (itemSinkTracker.observe(slot, itemId, loginBurst)) {
                case SOLD:
                    if (offers != null && offers[slot] != null && offers[slot].getState() != GrandExchangeOfferState.EMPTY) {
                        // the slot holds a real offer, whose events already account for it
                        break;
                    }
                    Transaction t = inferItemSinkSale(slot, itemId, varpLong(ITEM_SINK_PRICE[slot]));
                    log.debug("inferred item sink sale {}", t);
                    transactionsToProcess.add(t);
                    grandExchangeUncollectedManager.addUncollected(client.getAccountHash(), slot, itemId, 0, t.getAmountSpent());
                    suggestionManager.setSuggestionNeeded(true);
                    break;
                case CLEARED:
                    grandExchangeUncollectedManager.ensureSlotClear(client.getAccountHash(), slot);
                    suggestionManager.setSuggestionNeeded(true);
                    break;
                default:
                    break;
            }
        }
    }

    private Transaction inferItemSinkSale(int slot, int itemId, long price) {
        Transaction t = new Transaction();
        t.setId(UUID.randomUUID());
        t.setType(OfferStatus.SELL);
        t.setItemId(itemId);
        t.setPrice(price);
        t.setQuantity(1);
        t.setBoxId(slot);
        t.setAmountSpent(price);
        t.setTimestamp(Instant.now());
        t.setCopilotPriceUsed(itemId == offerManager.getLastViewedSlotItemId() && price == offerManager.getLastViewedSlotItemPrice() && Instant.now().minusSeconds(30).getEpochSecond() < offerManager.getLastViewedSlotPriceTime());
        t.setWasCopilotSuggestion(itemId == suggestionManager.getSuggestionItemIdOnOfferSubmitted() && OfferStatus.SELL.equals(suggestionManager.getSuggestionOfferStatusOnOfferSubmitted()));
        t.setLogin(false);
        t.setConsistent(true);
        return t;
    }

    // RuneLite 1.13.1 types some of the *_LONG varps as int varps, and getVarpLongValue then throws
    private long varpLong(int varp) {
        try {
            return client.getVarpLongValue(varp);
        } catch (IllegalArgumentException e) {
            return client.getVarpValue(varp);
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
        if (osrsLoginManager.isUnsupportedWorldType()) {
            log.debug("ignoring GE offer update on unsupported world type(s): {}", client.getWorldType());
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
        long uncollectedGp = 0;
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
        if (osrsLoginManager.isUnsupportedWorldType()) {
            return;
        }
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
        long amountSpentDiff = isNewOffer ? offer.getSpent() : offer.getSpent() - prev.getSpent();
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
