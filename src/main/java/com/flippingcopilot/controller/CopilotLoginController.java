package com.flippingcopilot.controller;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.flippingcopilot.manager.CopilotLoginManager;
import com.flippingcopilot.manager.FlipsStorageManager;
import com.flippingcopilot.model.*;
import com.flippingcopilot.ui.LoginPanel;
import com.flippingcopilot.ui.MainPanel;
import com.google.common.collect.Sets;
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
        flipManager.setCopilotUserId(copilotLoginManager.getCopilotUserId());
        executorService.schedule(() ->loadCopilotAccounts(0), 0, TimeUnit.SECONDS);
        executorService.schedule(() -> syncFlips(copilotLoginManager.getCopilotUserId(), new HashMap<>(), 0), 5, TimeUnit.SECONDS);
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
                long backOffSeconds = Math.min(15, (long) Math.exp(previousFailures));
                log.info("failed to load copilot accounts ({}) retrying in {}s", errorMessage, backOffSeconds);
                executorService.schedule(() -> loadCopilotAccounts(previousFailures + 1), backOffSeconds, TimeUnit.SECONDS);
            }
        };
        apiRequestHandler.asyncLoadAccounts(onSuccess, onFailure);
    }

    private void syncFlips(int userId, Map<Integer, Integer> accountIdTime, int previousFailures) {
        // this will continuously sync the flips from the copilot server

        if(copilotLoginManager.getCopilotUserId() != userId) {
            log.info("user={}, no longer logged in, stopping syncFlips.", userId);
            return;
        }
        Set<Integer> accountIds = copilotLoginManager.accountIds();
        if(accountIds.isEmpty()) {
            long backOffSeconds = Math.min(15, (long) 1+previousFailures);
            log.info("user={}, no accounts loaded - re-scheduling runSyncFlips in {}s", userId, backOffSeconds);
            executorService.schedule(() -> syncFlips(userId, accountIdTime, previousFailures+1), backOffSeconds, TimeUnit.SECONDS);
            return;
        }
        try {
            accountIdTime.putAll(flipsStorageManager.loadSavedFlips(Sets.difference(accountIds,  accountIdTime.keySet()), flips -> flipManager.mergeFlips(flips, userId)));
        } catch (IOException e) {
            long backOffSeconds = Math.min(15, (long) Math.exp(previousFailures));
            log.error("user={}, loading flips from disk - re-scheduling runSyncFlips in {}s", userId, backOffSeconds, e);
            executorService.schedule(() -> syncFlips(userId, accountIdTime, 0), 5, TimeUnit.SECONDS);
            return;
        }
        long s = System.nanoTime();
        BiConsumer<Integer, FlipsDeltaResult> onSuccess = (Integer copilotUserId, FlipsDeltaResult r) -> {
            Map<Integer, List<FlipV2>> flipsByAccountId = r.flips.stream().collect(Collectors.groupingBy(FlipV2::getAccountId));
            for(Map.Entry<Integer, List<FlipV2>> entry : flipsByAccountId.entrySet()) {
                try {
                    flipsStorageManager.mergeFlips(entry.getValue());
                } catch (IOException e) {
                    long backOffSeconds = Math.min(15, (long) Math.exp(previousFailures));
                    log.error("user={}, merging updated flips to disk - re-scheduling syncFlips  in {}s", userId, backOffSeconds, e);
                    executorService.schedule(() -> syncFlips(userId, accountIdTime, 0), 5, TimeUnit.SECONDS);
                }
                if(!flipManager.mergeFlips(r.flips, userId)) {
                    log.info("user={}, no longer logged in, stopping syncFlips.", userId);
                    return;
                }
            }
            log.info("user={}, loading {} updated flips - took {}ms", userId, r.flips.size(), (System.nanoTime() - s) / 1000_000);
            accountIds.forEach((a) -> accountIdTime.put(a, r.time));
            executorService.schedule(() -> syncFlips(userId, accountIdTime, 0), 5, TimeUnit.SECONDS);
        };
        Consumer<String> onFailure = (errorMessage) -> {
            long backOffSeconds = Math.min(15, (long) Math.exp(previousFailures));
            log.info("user={},  failed to load updated flips ({}) retrying in {}s", userId, errorMessage, backOffSeconds);
            executorService.schedule(() -> syncFlips(userId, accountIdTime, previousFailures + 1), backOffSeconds, TimeUnit.SECONDS);
        };
        apiRequestHandler.asyncLoadFlips(accountIdTime, onSuccess, onFailure);
    }

    public void onLoginPressed(ActionEvent event) {
        Consumer<LoginResponse> onSuccess = (LoginResponse loginResponse) -> {
            copilotLoginManager.setLoginResponse(loginResponse);
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
