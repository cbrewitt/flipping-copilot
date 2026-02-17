package com.flippingcopilot.controller;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.manager.CopilotLoginManager;
import com.flippingcopilot.model.*;
import com.flippingcopilot.ui.*;
import com.flippingcopilot.ui.flipsdialog.FlipsDialogController;
import com.flippingcopilot.ui.graph.model.Data;
import com.flippingcopilot.ui.graph.model.PriceLine;
import com.google.gson.Gson;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.SoundEffectID;
import net.runelite.api.VarClientInt;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;


@Slf4j
@Getter
@Setter
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SuggestionController {

    // dependencies
    private final PausedManager pausedManager;
    private final Client client;
    private final Gson gson;
    private final OsrsLoginManager osrsLoginManager;
    private final HighlightController highlightController;
    private final GrandExchange grandExchange;
    private final ScheduledExecutorService executorService;
    private final ApiRequestHandler apiRequestHandler;
    private final WebHookController webHookController;

    private final Notifier notifier;
    private final OfferManager offerManager;
    private final CopilotLoginManager copilotLoginManager;
    private final ClientThread clientThread;
    private final FlippingCopilotConfig config;
    private final SuggestionManager suggestionManager;
    private final AccountStatusManager accountStatusManager;
    private final GrandExchangeUncollectedManager uncollectedManager;
    private final FlipsDialogController flipDialogController;
    private final GePreviousSearch gePreviousSearch;

    private MainPanel mainPanel;
    private LoginPanel loginPanel;
	private CopilotPanel copilotPanel;
	private SuggestionPanel suggestionPanel;
	private boolean lastCollectNeeded = false;

    public void togglePause() {
        if (pausedManager.isPaused()) {
            pausedManager.setPaused(false);
            suggestionManager.setSuggestionNeeded(true);
            suggestionPanel.refresh();
        } else {
            pausedManager.setPaused(true);
            highlightController.removeAll();
            suggestionPanel.refresh();
        }
    }

    void onGameTick() {
        if(suggestionManager.isSuggestionRequestInProgress() || suggestionManager.isGraphDataReadingInProgress()) {
            return;
        }
        // There is a race condition when the collect button is hit at the same time as offers fill.
        // In such a case we can end up with the uncollectedManager falsely thinking there is items to collect.
        // We identify if this has happened here by checking if the collect button is actually visible.
        if(isUncollectedOutOfSync()) {
            log.warn("uncollected is out of sync, it thinks there are items to collect but the GE is open and the Collect button not visible");
            uncollectedManager.clearAllUncollected(osrsLoginManager.getAccountHash());
            suggestionManager.setSuggestionNeeded(true);
        }
        // on initial login the state of the GE offers isn't correct we need to wait a couple ticks before requesting a suggestion
        if (osrsLoginManager.hasJustLoggedIn()) {
            return;
        }
        if (shouldFetchNewSuggestion()) {
            getSuggestionAsync();
        }
    }

    private boolean shouldFetchNewSuggestion() {
        if (client.getTickCount() < suggestionManager.suggestionsDelayedUntil) {
            return false;
        }
        Suggestion p = suggestionManager.getSuggestion();
        if(grandExchange.isSlotOpen() && !suggestionActionedOrVeryOutOfDate(p)) {
            return false;
        }

        return suggestionManager.isSuggestionNeeded() || suggestionManager.suggestionOutOfDate();
    }

    private boolean suggestionActionedOrVeryOutOfDate(Suggestion p) {
        if (p == null || p.getType().equals("wait")) {
            return true;
        }
        if (p.actionedTick != -1 && p.actionedTick < client.getTickCount()) {
            return true;
        }
        return suggestionManager.suggestionVeryOutOfDate();
    }

    private boolean isUncollectedOutOfSync() {
        if (client.getTickCount() <= uncollectedManager.getLastUncollectedAddedTick() + 2) {
            return false;
        }
        if(!grandExchange.isHomeScreenOpen() || grandExchange.isCollectButtonVisible()) {
            return false;
        }
        if(uncollectedManager.HasUncollected(osrsLoginManager.getAccountHash())) {
            return true;
        }
        if(suggestionPanel.isCollectItemsSuggested()) {
            return true;
        }
        return false;
    }

    public void getSuggestionAsync() {
        suggestionManager.setSuggestionNeeded(false);
        if (!copilotLoginManager.isLoggedIn() || !osrsLoginManager.isValidLoginState()) {
            return;
        }
        if (suggestionManager.isSuggestionRequestInProgress()) {
            return;
        }
        AccountStatus accountStatus = accountStatusManager.getAccountStatus();
        if (accountStatus == null) {
            return;
        }
        Suggestion oldSuggestion = suggestionManager.getSuggestion();
        if (oldSuggestion != null && oldSuggestion.isRecentUnActionedDumpAlert()) {
            return;
        }
        suggestionManager.setSuggestionRequestInProgress(true);
        boolean skipGraphData = config.lowDataMode();
        suggestionManager.setGraphDataReadingInProgress(!skipGraphData);
        Consumer<Suggestion> suggestionConsumer = (newSuggestion) -> handleSuggestionReceived(oldSuggestion, newSuggestion, accountStatus);
        Consumer<Data> graphDataConsumer = (d) -> {
            SwingUtilities.invokeLater(() -> flipDialogController.priceGraphPanel.setSuggestionPriceData(d));
            suggestionManager.setGraphDataReadingInProgress(false);
        };
        Consumer<HttpResponseException> onFailure = (e) -> {
            suggestionManager.setSuggestion(null);
            suggestionManager.setSuggestionError(e);
            suggestionManager.setSuggestionRequestInProgress(false);
            suggestionManager.setGraphDataReadingInProgress(false);
            if (e.getResponseCode() == 401) {
                copilotLoginManager.reset();
                mainPanel.refresh();
                loginPanel.showLoginErrorMessage("Login timed out. Please log in again");
            } else {
                suggestionPanel.refresh();
            }
        };
        suggestionPanel.refresh();
        log.debug("tick {} getting suggestion", client.getTickCount());
        apiRequestHandler.getSuggestionAsync(accountStatus.toJson(gson, grandExchange.isOpen(), config.priceGraphWebsite() == FlippingCopilotConfig.PriceGraphWebsite.FLIPPING_COPILOT), suggestionConsumer, graphDataConsumer, onFailure, skipGraphData);
    }

    void handleDumpSuggestion(Suggestion suggestion) {
        AccountStatus accountStatus = accountStatusManager.getAccountStatus();
        if (accountStatus == null) {
            log.info("discarding dump suggestion as account status null");
            return;
        }
        Suggestion s = suggestionManager.getSuggestion();
        if(s != null && s.isDumpAlert && s.actionedTick == -1) {
            log.info("discarding dump suggestion as already processing dump suggestion");
            return;
        }
        if (accountStatus.emptySlotExists()) {
            handleSuggestionReceived(suggestionManager.getSuggestion(), suggestion, accountStatus);
        } else {
            log.info("discarding dump suggestion as no free slot");
        }
    }

    private synchronized void handleSuggestionReceived(Suggestion oldSuggestion, Suggestion newSuggestion, AccountStatus accountStatus) {
        if (!newSuggestion.isDumpAlert && !suggestionManager.isSuggestionRequestInProgress()) {
            // this is the edge case when a dump suggestion is received whilst a standard request is in progress
            log.info("discarding suggestion as not dump alert and no request in progress {}", newSuggestion);
            return;
        }
        if (newSuggestion.isDumpAlert && config.dumpAlertSound()) {
            client.playSoundEffect(SoundEffectID.GE_ADD_OFFER_DINGALING);
        }
        boolean shouldNotify = shouldNotify(newSuggestion, oldSuggestion);

        if (newSuggestion.isDumpAlert && shouldNotify) {
            // Send short-format webhook for dump alert: Item, Price
            try {
                webHookController.sendDumpAlert(newSuggestion);
            } catch (Exception e) {
                log.debug("Error sending dump webhook", e);
            }
        }

        suggestionManager.setSuggestion(newSuggestion);
        suggestionManager.setSuggestionError(null);
        suggestionManager.setSuggestionRequestInProgress(false);
        log.debug("Received suggestion: {}", newSuggestion.toString());
        accountStatusManager.resetSkipSuggestion();
        offerManager.setOfferJustPlaced(false);
        suggestionPanel.refresh();

        // Collect webhook alert (edge-triggered to avoid spam)
        try {
            boolean collectNeeded = accountStatus.isCollectNeeded(newSuggestion, grandExchange.isSetupOfferOpen());
            if (collectNeeded && !lastCollectNeeded) {
                webHookController.sendCollectAlert();
            }
            lastCollectNeeded = collectNeeded;
        } catch (Exception e) {
            log.debug("Error sending collect webhook", e);
        }

        showNotifications(oldSuggestion, newSuggestion, accountStatus);
        // Send webhook alerts for buy/sell suggestions in short format
        String type = newSuggestion.getType();
        if (shouldNotify && "buy".equals(type)) {
            try {
                webHookController.sendBuyAlert(newSuggestion);
            } catch (Exception e) { log.debug("Error sending buy webhook", e); }
        } else if (shouldNotify && "sell".equals(type)) {
            try {
                webHookController.sendSellAlert(newSuggestion);
            } catch (Exception e) { log.debug("Error sending sell webhook", e); }
        } else if (shouldNotify && "abort".equals(type)) {
            try {
                webHookController.sendAbortAlert(newSuggestion);
            } catch (Exception e) { log.debug("Error sending abort webhook", e); }
        }

        if (!newSuggestion.getType().equals("wait")) {
            SwingUtilities.invokeLater(() -> flipDialogController.priceGraphPanel.newSuggestedItemId(
                    newSuggestion.getItemId(),
                    buildPriceLine(newSuggestion)
            ));
        } else {
            SwingUtilities.invokeLater(() -> flipDialogController.priceGraphPanel.suggestedPriceLine = null);
        }
        if (client.getVarcIntValue(VarClientInt.INPUT_TYPE) == 14) {
            clientThread.invokeLater(gePreviousSearch::showSuggestedItemInSearch);
        }
    }

    private PriceLine buildPriceLine(Suggestion suggestion) {
        String type = suggestion.getType();
        if ("buy".equals(type)) {
            return new PriceLine(
                    suggestion.getPrice(),
                    "Suggested buy price",
                    false
            );
        }
        if ("sell".equals(type)) {
            return new PriceLine(
                    suggestion.getPrice(),
                    "Suggested sell price",
                    true
            );
        }
        return null;
    }

	void showNotifications(Suggestion oldSuggestion, Suggestion newSuggestion, AccountStatus accountStatus) {
		if (shouldNotify(newSuggestion, oldSuggestion)) {
			String msg = newSuggestion.toMessage();
			if (config.enableTrayNotifications()) {
				notifier.notify(msg);
			}
			if (!copilotPanel.isShowing() && config.enableChatNotifications()) {
				showChatNotifications(newSuggestion, accountStatus);
			}
		}
	}

    static boolean shouldNotify(Suggestion newSuggestion, Suggestion oldSuggestion) {
        if (newSuggestion.getType().equals("wait")) {
            return false;
        }
        if (oldSuggestion != null && newSuggestion.equals(oldSuggestion)) {
            return false;
        }
        return true;
    }

    private void showChatNotifications(Suggestion newSuggestion, AccountStatus accountStatus) {
        if (accountStatus.isCollectNeeded(newSuggestion, grandExchange.isSetupOfferOpen())) {
            clientThread.invokeLater(() -> showChatNotification("Flipping Copilot: Collect items"));
        }
        clientThread.invokeLater(() -> showChatNotification(newSuggestion.toMessage()));
    }

    private void showChatNotification(String message) {
        String chatMessage = new ChatMessageBuilder()
                .append(config.chatTextColor(), message)
                .build();
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", chatMessage, "");
    }
}
