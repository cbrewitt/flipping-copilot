package com.flippingcopilot.controller;

import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.flippingcopilot.manager.CopilotLoginManager;
import com.flippingcopilot.model.*;
import com.flippingcopilot.ui.LoginPanel;
import com.flippingcopilot.ui.MainPanel;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class CopilotLoginController {

    // dependencies
    @Setter
    private LoginPanel loginPanel;
    @Setter
    private MainPanel mainPanel;
    private final ApiRequestHandler apiRequestHandler;
    private final FlipManager flipManager;
    private final HighlightController highlightController;
    private final CopilotLoginManager copilotLoginManager;
    private final SuggestionManager suggestionManager;
    private final OsrsLoginManager osrsLoginManager;
    private final SessionManager sessionManager;
    private final TransactionManager transactionManager;
    private final ScheduledExecutorService executorService;


    @Inject
    public CopilotLoginController(ApiRequestHandler apiRequestHandler,
                                  FlipManager flipManager,
                                  HighlightController highlightController,
                                  CopilotLoginManager copilotLoginManager,
                                  SuggestionManager suggestionManager,
                                  OsrsLoginManager osrsLoginManager,
                                  SessionManager sessionManager,
                                  TransactionManager transactionManager,
                                  ScheduledExecutorService executorService) {
        this.apiRequestHandler = apiRequestHandler;
        this.flipManager = flipManager;
        this.highlightController = highlightController;
        this.copilotLoginManager = copilotLoginManager;
        this.suggestionManager = suggestionManager;
        this.osrsLoginManager = osrsLoginManager;
        this.sessionManager = sessionManager;
        this.transactionManager = transactionManager;
        this.executorService = executorService;
        flipManager.setCopilotUserId(copilotLoginManager.getCopilotUserId());
        loadCopilotAccounts(0);
    }

    private void loadCopilotAccounts(int previousFailures) {
        int userId = copilotLoginManager.getCopilotUserId();
        if(userId == -1) {
            return;
        }
        long s = System.nanoTime();
        Consumer<Map<String, Integer>> onSuccess = (displayNameToAccountId) -> {
            displayNameToAccountId.forEach((key, value) ->
                    copilotLoginManager.addAccountIfMissing(value, key, userId));
            log.info("loading {} copilot accounts succeeded - took {}ms", displayNameToAccountId.size(), (System.nanoTime() - s) / 1000_000);
            syncFlips(copilotLoginManager.getCopilotUserId(), new HashMap<>(), 0);
        };
        Consumer<String> onFailure = (errorMessage) -> {
            if (copilotLoginManager.isLoggedIn()) {
                long backOffSeconds = Math.min(15, (long) Math.exp(previousFailures));
                log.info("failed to load copilot accounts ({}) retrying in {}s", errorMessage, backOffSeconds);
                executorService.schedule(() -> loadCopilotAccounts(previousFailures + 1), backOffSeconds, TimeUnit.SECONDS);
            }
        };
        apiRequestHandler.asyncLoadAccounts(onSuccess, onFailure);
    }

    private void syncFlips(int userId, Map<Integer, Integer> accountIdTime, int previousFailures) {
        // Continuously sync's the delta of new or updated flips from the server with back off on failure
        if(copilotLoginManager.getCopilotUserId() != userId) {
            log.info("user={}, no longer logged in, stopping syncFlips.", userId);
            return;
        }
        Set<Integer> accountIds = copilotLoginManager.accountIds();
        if(accountIds.isEmpty()) {
            long backOffSeconds = Math.min(45, (long) 1+previousFailures);
            log.info("user={}, no accounts loaded - re-scheduling runSyncFlips in {}s", userId, backOffSeconds);
            executorService.schedule(() -> syncFlips(userId, accountIdTime, previousFailures+1), backOffSeconds, TimeUnit.SECONDS);
            return;
        }
        accountIds.forEach(a -> accountIdTime.computeIfAbsent(a, i -> 0));
        long s = System.nanoTime();
        BiConsumer<Integer, FlipsDeltaResult> onSuccess = (Integer copilotUserId, FlipsDeltaResult r) -> {
            if(!flipManager.mergeFlips(r.flips, userId)) {
                log.info("user={}, no longer logged in, stopping syncFlips.", userId);
                return;
            }
            log.debug("user={}, loading {} updated flips - took {}ms", userId, r.flips.size(), (System.nanoTime() - s) / 1000_000);
            accountIds.forEach((a) -> accountIdTime.put(a, r.time));
            executorService.schedule(() -> syncFlips(userId, accountIdTime, 0), 5, TimeUnit.SECONDS);
        };
        Consumer<String> onFailure = (errorMessage) -> {
            long backOffSeconds = Math.min(45, (long) Math.exp(previousFailures));
            log.info("user={}, failed to load updated flips ({}) retrying in {}s", userId, errorMessage, backOffSeconds);
            executorService.schedule(() -> syncFlips(userId, accountIdTime, previousFailures + 1), backOffSeconds, TimeUnit.SECONDS);
        };
        apiRequestHandler.asyncLoadFlips(accountIdTime, onSuccess, onFailure);
    }

    public void onLoginPressed(String email, String password) {
        Consumer<LoginResponse> onSuccess = (LoginResponse loginResponse) -> {
            copilotLoginManager.setLoginResponse(loginResponse);
            mainPanel.refresh();
            String displayName = osrsLoginManager.getPlayerDisplayName();
            if(displayName != null) {
                flipManager.setIntervalAccount(null);
                flipManager.setIntervalStartTime(sessionManager.getCachedSessionData().startTime);
                transactionManager.scheduleSyncIn(0, displayName);
            }
            flipManager.setCopilotUserId(loginResponse.getUserId());
            loadCopilotAccounts(0);
            loginPanel.endLoading();
        };
        Consumer<String> onFailure = (String errorMessage) -> {
            copilotLoginManager.reset();
            loginPanel.showLoginErrorMessage(errorMessage);
            loginPanel.endLoading();
        };
        if (email == null || password == null) {
            return;
        }
        loginPanel.startLoading();
        apiRequestHandler.authenticate(email, password, onSuccess, onFailure);
    }

    public void onLogout() {
        flipManager.reset();
        copilotLoginManager.reset();
        suggestionManager.reset();
        highlightController.removeAll();
        mainPanel.refresh();
    }
}
