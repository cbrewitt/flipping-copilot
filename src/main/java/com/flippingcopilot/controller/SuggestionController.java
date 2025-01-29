package com.flippingcopilot.controller;

import com.flippingcopilot.model.*;
import com.flippingcopilot.ui.*;
import com.google.gson.Gson;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;

import javax.inject.Inject;
import javax.inject.Singleton;
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
    private final Notifier notifier;
    private final OfferManager offerManager;
    private final LoginResponseManager loginResponseManager;
    private final ClientThread clientThread;
    private final FlippingCopilotConfig config;
    private final SuggestionManager suggestionManager;
    private final AccountStatusManager accountStatusManager;
    private final GrandExchangeUncollectedManager uncollectedManager;
    private MainPanel mainPanel;
    private LoginPanel loginPanel;
    private CopilotPanel copilotPanel;
    private SuggestionPanel suggestionPanel;

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
        if(suggestionManager.isSuggestionRequestInProgress()) {
            return;
        }
        // There is a race condition when the collect button is hit at the same time as offers fill.
        // In such a case we can end up with the uncollectedManager falsely thinking there is items to collect.
        // We identify if this has happened here by checking if the collect button is actually visible.
        if(isUncollectedOutOfSync()) {
            log.warn("uncollectedManager manager is out of sync, it thinks there are items to collect but the GE is open and the Collect button not visible");
            uncollectedManager.clearAllUncollected(osrsLoginManager.getAccountHash());
            suggestionManager.setSuggestionNeeded(true);
        }
        // on initial login the state of the GE offers isn't correct we need to wait a couple ticks before requesting a suggestion
        if (osrsLoginManager.hasJustLoggedIn()) {
            return;
        }
        if ((suggestionManager.isSuggestionNeeded() || suggestionManager.suggestionOutOfDate()) && !(grandExchange.isSlotOpen() && !accountStatusManager.isSuggestionSkipped())) {
            getSuggestionAsync();
        }
    }

    private boolean isUncollectedOutOfSync() {
        if (client.getTickCount() <= uncollectedManager.getLastUncollectedAddedTick() + 2) {
            return false;
        }
        if(!grandExchange.isOpen() || grandExchange.isCollectButtonVisible()) {
            return false;
        }
        if(uncollectedManager.HasUncollected(osrsLoginManager.getAccountHash())) {
            return true;
        }
        if(suggestionPanel.isCollectItemsSuggested()) {
            log.debug("inner suggestion text is collect items");
            return true;
        }
        return false;
    }

    public void getSuggestionAsync() {
        suggestionManager.setSuggestionNeeded(false);
        if (!loginResponseManager.isLoggedIn() || !osrsLoginManager.isValidLoginState()) {
            return;
        }
        if (suggestionManager.isSuggestionRequestInProgress()) {
            return;
        }
        AccountStatus accountStatus = accountStatusManager.getAccountStatus();
        if (accountStatus == null) {
            return;
        }
        suggestionManager.setSuggestionRequestInProgress(true);
        Suggestion oldSuggestion = suggestionManager.getSuggestion();
        Consumer<Suggestion> onSuccess = (newSuggestion) -> {
            suggestionManager.setSuggestion(newSuggestion);
            suggestionManager.setSuggestionError(null);
            suggestionManager.setSuggestionRequestInProgress(false);
            log.debug("Received suggestion: {}", newSuggestion.toString());
            accountStatusManager.resetSkipSuggestion();
            offerManager.setOfferJustPlaced(false);
            suggestionPanel.refresh();
            showNotifications(oldSuggestion, newSuggestion, accountStatus);
        };
        Consumer<HttpResponseException> onFailure = (e) -> {
            suggestionManager.setSuggestionError(e);
            suggestionManager.setSuggestionRequestInProgress(false);
            if (e.getResponseCode() == 401) {
                loginResponseManager.reset();
                mainPanel.refresh();
                loginPanel.showLoginErrorMessage("Login timed out. Please log in again");
            } else {
                suggestionPanel.refresh();
            }
        };
        suggestionPanel.refresh();
        log.debug("tick {} getting suggestion", client.getTickCount());
        apiRequestHandler.getSuggestionAsync(accountStatus.toJson(gson, grandExchange.isOpen()), onSuccess, onFailure);
    }


    void showNotifications(Suggestion oldSuggestion, Suggestion newSuggestion, AccountStatus accountStatus) {
        if (shouldNotify(newSuggestion, oldSuggestion)) {
            if (config.enableTrayNotifications()) {
                notifier.notify(newSuggestion.toMessage());
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
        if (accountStatus.isCollectNeeded(newSuggestion)) {
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
