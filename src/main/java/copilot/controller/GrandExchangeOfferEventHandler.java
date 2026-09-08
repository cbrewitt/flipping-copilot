package copilot.controller;
import copilot.model.*;
import copilot.ui.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.inject.*;

import lombok.*;
import lombok.extern.slf4j.*;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.ui.overlay.*;

import static copilot.model.PlayerLogin.GE_LOGIN_BURST_WINDOW;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class GrandExchangeOfferEventHandler {

    // dependencies
    private final Client client;
    private final Offers offerPersistence;
    private final GrandExchange grandExchange;
    private final Transactions transactionManager;
    private final PlayerLogin osrsLoginManager;
    private final OverlayManager overlayManager;
    private final Uncollected uncollected;
    private final Offers offerManager;
    private final Suggestions suggestions;

    // state
    private final Queue<Transaction> transactionsToProcess = new ConcurrentLinkedQueue<>();

    public void onGameTick() {
        if(!transactionsToProcess.isEmpty()) { processTransactions(); }
    }

    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged offerEvent) {
        final int slot = offerEvent.getSlot();
        final var offer = offerEvent.getOffer();
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

        var o = SavedOffer.fromGrandExchangeOffer(offer);

        var prev = offerPersistence.loadOffer(accountHash, slot);

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
        suggestions.setSuggestionNeeded(true);
    }

    private boolean wasCopilotPriceUsed(SavedOffer o, SavedOffer prev) {
        if(isNewOffer(prev, o)){
            return o.itemId == offerManager.getLastViewedSlotItemId() && o.price == offerManager.getLastViewedSlotItemPrice() && Instant.now().minusSeconds(30).getEpochSecond() < offerManager.getLastViewedSlotPriceTime();
        } else {
            return prev.copilotPriceUsed;
        }
    }

    private boolean wasCopilotSuggestion(SavedOffer o, SavedOffer prev) {
        if(isNewOffer(prev, o)){
            return o.itemId == suggestions.getSuggestionItemIdOnOfferSubmitted() && o.getOfferStatus().equals(suggestions.getSuggestionOfferStatusOnOfferSubmitted());
        } else {
            return prev.wasCopilotSuggestion;
        }
    }

    private void updateUncollected(Long accountHash, int slot, SavedOffer o, SavedOffer prev, boolean consistent) {
        if (!consistent) { return; }
        long uncollectedGp = 0;
        int uncollectedItems = 0;
        switch (o.state) {
            case BUYING:
            case BOUGHT: uncollectedItems = isNewOffer(prev, o) ? o.quantitySold : o.quantitySold - prev.quantitySold; break;
            case SOLD:
            case SELLING: uncollectedGp = (isNewOffer(prev, o) ? o.quantitySold : o.quantitySold - prev.quantitySold) * o.price; break;
            case CANCELLED_BUY: uncollectedGp = (o.totalQuantity - o.quantitySold) * o.price; break;
            case CANCELLED_SELL: uncollectedItems = o.totalQuantity - o.quantitySold; break;
            case EMPTY:
                // if the slot is empty we want to ensure that the un collected manager doesn't think there is something to collect
                // this can happen due to race conditions between the collection and offer fills timing
                uncollected.ensureSlotClear(accountHash, slot);
                suggestions.setSuggestionNeeded(true);
                return;
        }
        uncollected.addUncollected(accountHash, slot, o.itemId, uncollectedItems, uncollectedGp);

    }

    private void processTransactions() {
        if (osrsLoginManager.isUnsupportedWorldType()) { return; }
        String displayName = osrsLoginManager.getPlayerDisplayName();
        if(displayName != null) {
            Transaction transaction;
            while ((transaction = transactionsToProcess.poll()) != null) {
                long profit = transactionManager.addTransaction(transaction, displayName);
                if (grandExchange.isHomeScreenOpen() && profit != 0) {
                    new GpDropOverlay(overlayManager, client, profit, transaction.boxId);
                }
            }
        }
    }

    public Transaction inferTransaction(int slot, SavedOffer offer, SavedOffer prev, boolean consistent) {
        boolean login = client.getTickCount() <= osrsLoginManager.getLastLoginTick() + GE_LOGIN_BURST_WINDOW;
        boolean isNewOffer = isNewOffer(prev, offer);
        int quantityDiff = isNewOffer ? offer.quantitySold : offer.quantitySold - prev.quantitySold;
        long amountSpentDiff = isNewOffer ? offer.spent : offer.spent - prev.spent;
        if (quantityDiff > 0 && amountSpentDiff > 0) {
            var t = new Transaction();
            t.setId(UUID.randomUUID());
            t.setType(offer.getOfferStatus());
            t.setItemId(offer.itemId);
            t.setPrice(offer.price);
            t.setQuantity(quantityDiff);
            t.setBoxId(slot);
            t.setAmountSpent(amountSpentDiff);
            t.setTimestamp(Instant.now());
            t.setCopilotPriceUsed(offer.copilotPriceUsed);
            t.setWasCopilotSuggestion(offer.wasCopilotSuggestion);
            t.setLogin(login);
            t.setConsistent(consistent);
            return t;
        }
        return null;
    }

    private boolean isConsistent(SavedOffer prev, SavedOffer updated) {
        if (prev == null) { return false; }
        if (updated.state == GrandExchangeOfferState.EMPTY) { return true; }
        if(prev.state == GrandExchangeOfferState.EMPTY && !(updated.state == GrandExchangeOfferState.CANCELLED_BUY || updated.state == GrandExchangeOfferState.CANCELLED_SELL)) {
            return true;
        }
        return prev.getOfferStatus() == updated.getOfferStatus() ||
                prev.itemId == updated.itemId
                || prev.price == updated.price
                || prev.totalQuantity == updated.totalQuantity;
    }

    private boolean isNewOffer(SavedOffer prev, SavedOffer updated) {
        if (prev == null) { return true; }
        return prev.getOfferStatus() != updated.getOfferStatus() ||
                prev.itemId != updated.itemId
                || prev.price != updated.price
                || prev.totalQuantity != updated.totalQuantity
                || prev.quantitySold > updated.quantitySold
                || prev.spent > updated.spent;
    }
}
