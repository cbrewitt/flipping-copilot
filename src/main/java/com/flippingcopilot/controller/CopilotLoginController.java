package com.flippingcopilot.controller;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.flippingcopilot.manager.CopilotLoginManager;
import com.flippingcopilot.manager.FlipsStorageManager;
import com.flippingcopilot.model.*;
import com.flippingcopilot.ui.LoginPanel;
import com.flippingcopilot.ui.MainPanel;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.mutable.Mutable;

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
    private final FlipsStorageManager flipsStorageManager;

    // state
    private String email;
    private String password;


    @Inject
    public CopilotLoginController(ApiRequestHandler apiRequestHandler, FlipManager flipManager, HighlightController highlightController, CopilotLoginManager copilotLoginManager, SuggestionManager suggestionManager, OsrsLoginManager osrsLoginManager, SessionManager sessionManager, TransactionManager transactionManager, ScheduledExecutorService executorService, FlipsStorageManager flipsStorageManager) {
        this.apiRequestHandler = apiRequestHandler;
        this.flipManager = flipManager;
        this.highlightController = highlightController;
        this.copilotLoginManager = copilotLoginManager;
        this.suggestionManager = suggestionManager;
        this.osrsLoginManager = osrsLoginManager;
        this.sessionManager = sessionManager;
        this.transactionManager = transactionManager;
        this.executorService = executorService;
        this.flipsStorageManager = flipsStorageManager;
        loadCopilotAccounts(0);
        syncFlips(copilotLoginManager.getCopilotUserId(), new HashMap<>(),0);
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
            log.info("loading copilot accounts succeeded - took {}ms", (System.nanoTime() - s) / 1000_000);
        };
        Consumer<String> onFailure = (errorMessage) -> {
            if (copilotLoginManager.isLoggedIn()) {
                long backOffSeconds = Math.max(15, (long) Math.exp(previousFailures));
                log.info("failed to load copilot accounts ({}) retrying in {}s", errorMessage, backOffSeconds);
                executorService.schedule(() -> loadCopilotAccounts(previousFailures + 1), backOffSeconds, TimeUnit.SECONDS);
            }
        };
        apiRequestHandler.asyncLoadAccounts(onSuccess, onFailure);
    }

    private void syncFlips(int userId, int previousFailures) {
        if(copilotLoginManager.getCopilotUserId() != userId) {
            log.info("copilot user {} no longer logged in, stopping syncFlips.", userId);
            return;
        }
        Set<Integer> accountIds = copilotLoginManager.accountIds();
        try {
            flipsStorageManager.loadUninitialisedAccounts(accountIds, latestUpdatedTimes, flipManager::mergeFlips);
        } catch (IOException e) {
            long backOffSeconds = Math.max(15, (long) Math.exp(previousFailures));
            log.error("loading flips from disk for uninitialized accounts - re-scheduling runSyncFlips  in {}s", backOffSeconds, e);
            executorService.schedule(() -> syncFlips(userId, latestUpdatedTimes, 0), 5, TimeUnit.SECONDS);
        }
        long s = System.nanoTime();
        BiConsumer<Integer, List<FlipV2>> onSuccess = (Integer copilotUserId, List<FlipV2> flips) -> {
            if (copilotUserId != userId) {
                return;
            }
            if(!flips.isEmpty()) {
                try {
                    flipsStorageManager.mergeFlips(flips);
                } catch (IOException e) {
                    long backOffSeconds = Math.max(15, (long) Math.exp(previousFailures));
                    log.error("merging updated flips to disk - re-scheduling syncFlips  in {}s", backOffSeconds, e);
                    executorService.schedule(() -> syncFlips(userId, latestUpdatedTimes, 0), 5, TimeUnit.SECONDS);
                }
                flipManager.mergeFlips(flips);
            }
            log.info("loading {} updated flips - took {}ms", flips.size(), (System.nanoTime() - s) / 1000_000);
            executorService.schedule(() -> syncFlips(userId, latestUpdatedTimes, 0), 5, TimeUnit.SECONDS);
        };
        Consumer<String> onFailure = (errorMessage) -> {
            long backOffSeconds = Math.max(15, (long) Math.exp(previousFailures));
            log.info("failed to load updated flips ({}) retrying in {}s", errorMessage, backOffSeconds);
            executorService.schedule(() -> syncFlips(userId, latestUpdatedTimes, previousFailures + 1), backOffSeconds, TimeUnit.SECONDS);
        };
        apiRequestHandler.asyncLoadFlips(onSuccess, onFailure);
    }

    public void onLoginPressed(ActionEvent event) {
        Consumer<LoginResponse> onSuccess = (LoginResponse loginResponse) -> {
            copilotLoginManager.setLoginResponse(loginResponse);
            flipManager.loadFlipsAsync();
            mainPanel.refresh();
            String displayName = osrsLoginManager.getPlayerDisplayName();
            if(displayName != null) {
                flipManager.setIntervalAccount(null);
                flipManager.setIntervalStartTime(sessionManager.getCachedSessionData().startTime);
                transactionManager.scheduleSyncIn(0, displayName);
            }
            loadCopilotAccounts(0);
            syncFlips(copilotLoginManager.getCopilotUserId(), new HashMap<>(),0);
            loginPanel.endLoading();
        };
        Consumer<String> onFailure = (String errorMessage) -> {
            copilotLoginManager.reset();
            loginPanel.showLoginErrorMessage(errorMessage);
            loginPanel.endLoading();
        };
        if (this.email == null || this.password == null) {
            return;
        }
        loginPanel.startLoading();
        apiRequestHandler.authenticate(this.email, this.password, onSuccess, onFailure);
    }

    public void onLogout() {
        flipManager.reset();
        copilotLoginManager.reset();
        suggestionManager.reset();
        highlightController.removeAll();
    }

    public void onEmailTextChanged(String newEmail) {
        this.email = newEmail;
    }

    public void onPasswordTextChanged(String newPassword) {
        this.password = newPassword;
    }
}
