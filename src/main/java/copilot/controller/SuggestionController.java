package copilot.controller;

import copilot.ui.graph.model.Data;
import copilot.rs.*;
import copilot.config.*;
import copilot.model.*;
import copilot.ui.*;
import copilot.ui.flipsdialog.*;
import copilot.ui.graph.model.*;
import lombok.*;
import lombok.extern.slf4j.*;
import net.runelite.api.*;
import net.runelite.client.*;
import net.runelite.client.audio.*;
import net.runelite.client.callback.*;
import net.runelite.client.chat.*;

import javax.inject.*;
import javax.swing.*;
import java.util.function.*;

@Slf4j
@Getter
@Setter
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SuggestionController {

    private static final String DUMP_ALERT_SOUND = "/alert-sound.wav";

    // dependencies
    private final PausedManager pausedManager;
    private final Client client;
    private final AudioPlayer audioPlayer;
    private final PlayerLogin osrsLoginManager;
    private final HighlightController highlights;
    private final GrandExchange grandExchange;
    private final ApiClient apiRequestHandler;
    private final Notifier notifier;
    private final Offers offerManager;
    private final CopilotLogin copilotLogin;
    private final ClientThread clientThread;
    private final CopilotConfig config;
    private final Suggestions suggestions;
    private final AccountStatusManager accounts;
    private final Uncollected uncollectedManager;
    private final PortfolioStateRS portfolio;
    private final FlipsDialogController flipDialogController;
    private final GePreviousSearch gePreviousSearch;

    private MainPanel mainPanel;
    private LoginPanel loginPanel;
    private CopilotPanel copilotPanel;
    private SuggestionPanel suggestionPanel;

    public void skipSuggestion() {
        if (accounts.skipCurrentSuggestion()) {
            if (suggestionPanel != null) { suggestionPanel.refresh(); }
        }
    }

    public void togglePause() {
        if (pausedManager.isPaused()) {
            pausedManager.setPaused(false);
            suggestions.setSuggestionNeeded(true);
            suggestionPanel.refresh();
        } else {
            pausedManager.setPaused(true);
            highlights.removeAll();
            suggestionPanel.refresh();
        }
    }

    public void onGameTick() {
        if(suggestions.isSuggestionRequestInProgress() || suggestions.isGraphDataReadingInProgress()) { return; }
        // There is a race condition when the collect button is hit at the same time as offers fill.
        // In such a case we can end up with the uncollectedManager falsely thinking there is items to collect.
        // We identify if this has happened here by checking if the collect button is actually visible.
        if(isUncollectedOutOfSync()) {
            log.warn("uncollected is out of sync, it thinks there are items to collect but the GE is open and the Collect button not visible");
            uncollectedManager.clearAllUncollected(osrsLoginManager.getAccountHash());
            suggestions.setSuggestionNeeded(true);
        }
        // on initial login the state of the GE offers isn't correct we need to wait a couple ticks before requesting a suggestion
        if (osrsLoginManager.hasJustLoggedIn()) { return; }
        if (shouldFetchNewSuggestion()) { getSuggestionAsync(); }
    }

    private boolean shouldFetchNewSuggestion() {
        if (client.getTickCount() < suggestions.suggestionsDelayedUntil) { return false; }
        Suggestion p = suggestions.getSuggestion();
        if (grandExchange.isSlotOpen() && !suggestionActionedOrVeryOutOfDate(p)) { return false; }

        return suggestions.isSuggestionNeeded() || suggestions.suggestionOutOfDate();
    }

    private boolean suggestionActionedOrVeryOutOfDate(Suggestion p) {
        if (p == null || p.isWaitSuggestion()) { return true; }
        if (p.actionedTick != -1 && p.actionedTick < client.getTickCount()) { return true; }
        return suggestions.suggestionVeryOutOfDate();
    }

    private boolean isUncollectedOutOfSync() {
        if (client.getTickCount() <= uncollectedManager.getLastUncollectedAddedTick() + 2) { return false; }
        if (!grandExchange.isHomeScreenOpen() || grandExchange.isCollectButtonVisible()) { return false; }
        if (uncollectedManager.HasUncollected(osrsLoginManager.getAccountHash())) { return true; }
        if (suggestionPanel.isCollectItemsSuggested()) { return true; }
        return false;
    }

    public void getSuggestionAsync() {
        suggestions.setSuggestionNeeded(false);
        if (!copilotLogin.get().isLoggedIn() || !osrsLoginManager.isValidLoginState()) {
            suggestions.setSuggestionRefreshPending(false);
            return;
        }
        if (suggestions.isSuggestionRequestInProgress()) { return; }
        var accountStatus = accounts.getAccountStatus();
        if (accountStatus == null) {
            suggestions.setSuggestionRefreshPending(false);
            return;
        }
        Suggestion oldSuggestion = suggestions.getSuggestion();
        if (oldSuggestion != null && oldSuggestion.isRecentUnActionedDumpAlert()) {
            suggestions.setSuggestionRefreshPending(false);
            return;
        }
        suggestions.setSuggestionRequestInProgress(true);
        suggestions.setSuggestionRefreshPending(false);
        boolean skipGraphData = config.lowDataMode();
        suggestions.setGraphDataReadingInProgress(!skipGraphData);
        Consumer<Suggestion> suggestionConsumer = (newSuggestion) -> handleSuggestionReceived(oldSuggestion, newSuggestion, accountStatus);
        Consumer<Data> graphDataConsumer = (d) -> {
            SwingUtilities.invokeLater(() -> {
                if (flipDialogController.priceGraphPanel != null) {
                    flipDialogController.priceGraphPanel.setSuggestionPriceData(d);
                }
            });
            suggestions.setGraphDataReadingInProgress(false);
        };
        Consumer<HttpResponseException> onFailure = (e) -> {
            suggestions.setSuggestion(null);
            suggestions.setSuggestionError(e);
            suggestions.setSuggestionRequestInProgress(false);
            suggestions.setGraphDataReadingInProgress(false);
            if (e.responseCode == 401) {
                copilotLogin.clear();
                mainPanel.refresh();
                loginPanel.showLoginErrorMessage("Login timed out. Please log in again");
            } else {
                suggestionPanel.refresh();
            }
        };
        suggestionPanel.refresh();
        log.debug("tick {} getting suggestion", client.getTickCount());
        boolean sendGraphData = config.priceGraphWebsite() == CopilotConfig.PriceGraphWebsite.FLIPPING_COPILOT && !config.lowDataMode();
        boolean geOpen = grandExchange.isOpen();
        apiRequestHandler.getSuggestionAsync(accountStatus.encodeProto(geOpen, sendGraphData), suggestionConsumer, graphDataConsumer, onFailure);
    }

    void handleDumpSuggestion(Suggestion suggestion) {
        var accountStatus = accounts.getAccountStatus();
        if (accountStatus == null) {
            log.info("discarding dump suggestion as account status null");
            return;
        }
        Suggestion s = suggestions.getSuggestion();
        if(s != null && s.isDumpAlert && s.actionedTick == -1) {
            log.info("discarding dump suggestion as already processing dump suggestion");
            return;
        }
        if (accountStatus.emptySlotExists()) {
            handleSuggestionReceived(suggestions.getSuggestion(), suggestion, accountStatus);
        } else {
            log.info("discarding dump suggestion as no free slot");
        }
    }

    private synchronized void handleSuggestionReceived(Suggestion oldSuggestion, Suggestion newSuggestion, AccountStatus accountStatus) {
        if (!newSuggestion.isDumpAlert && !suggestions.isSuggestionRequestInProgress()) {
            // this is the edge case when a dump suggestion is received whilst a standard request is in progress
            log.info("discarding suggestion as not dump alert and no request in progress {}", newSuggestion);
            return;
        }
        if (newSuggestion.isBuyDumpSuggestion() && config.dumpAlertSound()) { playDumpAlertSound(); }
        suggestions.setSuggestion(newSuggestion);
        portfolio.updatePortfolioState(
                newSuggestion.bankItems,
                newSuggestion.portfolioItems,
                accountStatus.getOffers(),
                accountStatus.getUncollected(),
                newSuggestion.timeIssued
        );
        suggestions.setSuggestionError(null);
        suggestions.setSuggestionRequestInProgress(false);
        log.debug("Received suggestion: {}", newSuggestion.toString());
        accounts.resetSkipSuggestion();
        offerManager.setOfferJustPlaced(false);
        suggestionPanel.refresh();
        showNotifications(oldSuggestion, newSuggestion, accountStatus);
        if (!newSuggestion.isWaitSuggestion()) {
            SwingUtilities.invokeLater(() -> {
                if (flipDialogController.priceGraphPanel != null) {
                    flipDialogController.priceGraphPanel.newSuggestedItemId(
                            newSuggestion.itemId,
                            buildPriceLine(newSuggestion)
                    );
                }
            });
        } else {
            SwingUtilities.invokeLater(() -> {
                if (flipDialogController.priceGraphPanel != null) {
                    flipDialogController.priceGraphPanel.suggestedPriceLine = null;
                }
            });
        }
        if (client.getVarcIntValue(VarClientInt.INPUT_TYPE) == 14) {
            clientThread.invokeLater(gePreviousSearch::showSuggestedItemInSearch);
        }
    }

    private void playDumpAlertSound() {
        try {
            audioPlayer.play(SuggestionController.class, DUMP_ALERT_SOUND, 0);
        } catch (Exception e) {
            log.warn("failed to play dump alert sound", e);
        }
    }

    private PriceLine buildPriceLine(Suggestion suggestion) {
        if (suggestion.isBuySuggestion()) { return new PriceLine(suggestion.price, "Suggested buy price", false ); }
        if (suggestion.isSellSuggestion()) { return new PriceLine(suggestion.price, "Suggested sell price", true ); }
        return null;
    }

    void showNotifications(Suggestion oldSuggestion, Suggestion newSuggestion, AccountStatus accountStatus) {
        if (shouldNotify(newSuggestion, oldSuggestion)) {
            String msg = newSuggestion.toMessage();
            if (config.enableTrayNotifications()) { notifier.notify(msg); }
            if (!copilotPanel.isShowing() && config.enableChatNotifications()) {
                showChatNotifications(newSuggestion, accountStatus);
            }
        }
    }

    static boolean shouldNotify(Suggestion newSuggestion, Suggestion oldSuggestion) {
        if (newSuggestion.isWaitSuggestion()) { return false; }
        if (oldSuggestion != null && newSuggestion.equals(oldSuggestion)) { return false; }
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
