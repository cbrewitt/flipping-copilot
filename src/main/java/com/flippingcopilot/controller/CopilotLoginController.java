package com.flippingcopilot.controller;

import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.flippingcopilot.model.*;
import com.flippingcopilot.rs.CopilotLoginRS;
import com.flippingcopilot.ui.LoginPanel;
import com.flippingcopilot.ui.MainPanel;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.flippingcopilot.util.FormatUtil.formatDurationSince;

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
    private final OsrsLoginManager osrsLoginManager;
    private final SessionManager sessionManager;
    private final TransactionManager transactionManager;
    private final ScheduledExecutorService executorService;
    private final CopilotLoginRS copilotLoginRS;


    @Inject
    public CopilotLoginController(ApiRequestHandler apiRequestHandler,
                                  FlipManager flipManager,
                                  HighlightController highlightController,
                                  SuggestionManager suggestionManager,
                                  OsrsLoginManager osrsLoginManager,
                                  SessionManager sessionManager,
                                  TransactionManager transactionManager,
                                  ScheduledExecutorService executorService,
                                  CopilotLoginRS copilotLoginRS) {
        this.apiRequestHandler = apiRequestHandler;
        this.flipManager = flipManager;
        this.osrsLoginManager = osrsLoginManager;
        this.sessionManager = sessionManager;
        this.transactionManager = transactionManager;
        this.executorService = executorService;
        this.copilotLoginRS = copilotLoginRS;
        flipManager.setCopilotUserId(copilotLoginRS.get().getUserId());
        for (Market market : Market.values()) loadCopilotAccounts(market, 0);
        copilotLoginRS.registerListener((s) -> {
            if(s.loginResponse == null) {
                flipManager.reset();
                suggestionManager.reset();
                highlightController.removeAll();
                mainPanel.refresh();
            }
        });
    }

    private void loadCopilotAccounts(Market market, int previousFailures) {
        int userId = copilotLoginRS.get().getUserId();
        if(userId == -1) {
            return;
        }
        long s = System.nanoTime();
        Consumer<Map<String, Integer>> onSuccess = (displayNameToAccountId) -> {
            if (copilotLoginRS.get().getUserId() != userId) return;
            displayNameToAccountId.forEach((key, value) -> copilotLoginRS.addAccountIfMissing(value, key, userId, market));
            log.info("loading {} copilot accounts succeeded - took {}", displayNameToAccountId.size(), formatDurationSince(s));
            syncFlips(userId, market, new HashMap<>(), 0);
        };
        Consumer<String> onFailure = (errorMessage) -> {
            if (copilotLoginRS.get().getUserId() == userId) {
                long backOffSeconds = Math.min(15, (long) Math.exp(previousFailures));
                log.info("failed to load copilot accounts ({}) retrying in {}s", errorMessage, backOffSeconds);
                executorService.schedule(() -> loadCopilotAccounts(market, previousFailures + 1), backOffSeconds, TimeUnit.SECONDS);
            }
        };
        apiRequestHandler.asyncLoadAccounts(market, onSuccess, onFailure);
    }

    private void syncFlips(int userId, Market market, Map<Integer, Integer> accountIdTime, int previousFailures) {
        // Continuously sync's the delta of new or updated flips from the server with back off on failure
        if(copilotLoginRS.get().getUserId() != userId) {
            log.info("user={}, no longer logged in, stopping syncFlips.", userId);
            return;
        }
        Set<Integer> accountIds = new HashSet<>(copilotLoginRS.get().historyAccounts.getOrDefault(market, Collections.emptySet()));
        if(accountIds.isEmpty()) {
            long backOffSeconds = Math.min(45, (long) 1+previousFailures);
            log.info("user={}, no accounts loaded - re-scheduling runSyncFlips in {}s", userId, backOffSeconds);
            executorService.schedule(() -> syncFlips(userId, market, accountIdTime, previousFailures+1), backOffSeconds, TimeUnit.SECONDS);
            return;
        }
        accountIds.forEach(a -> accountIdTime.computeIfAbsent(a, i -> 0));
        long s = System.nanoTime();
        BiConsumer<Integer, FlipsDeltaResult> onSuccess = (Integer copilotUserId, FlipsDeltaResult r) -> {
            if(!flipManager.mergeFlips(r.flips, userId)) {
                log.info("user={}, no longer logged in, stopping syncFlips.", userId);
                return;
            }
            log.debug("user={}, loading {} updated flips - took {}", userId, r.flips.size(), formatDurationSince(s));
            accountIds.forEach((a) -> accountIdTime.put(a, r.time));
            executorService.schedule(() -> syncFlips(userId, market, accountIdTime, 0), 5, TimeUnit.SECONDS);
        };
        Consumer<String> onFailure = (errorMessage) -> {
            long backOffSeconds = Math.min(45, (long) Math.exp(previousFailures));
            log.info("user={}, failed to load updated flips ({}) retrying in {}s", userId, errorMessage, backOffSeconds);
            executorService.schedule(() -> syncFlips(userId, market, accountIdTime, previousFailures + 1), backOffSeconds, TimeUnit.SECONDS);
        };
        apiRequestHandler.asyncLoadFlips(market, accountIdTime, onSuccess, onFailure);
    }

    public void onLoginPressed(String email, String password) {
        Consumer<LoginResponse> onSuccess = (LoginResponse loginResponse) -> {
            onLoginResponse(loginResponse);
            loginPanel.endLoading();
        };
        Consumer<String> onFailure = (String errorMessage) -> {
            onLoginFailure(errorMessage);
            loginPanel.endLoading();
        };
        if (email == null || password == null) {
            return;
        }
        loginPanel.startLoading();
        apiRequestHandler.authenticate(email, password, onSuccess, onFailure);
    }

    public void onLoginResponse(LoginResponse loginResponse) {
        copilotLoginRS.update((s) -> {
            s.loginResponse = loginResponse;
            return s;
        });
        mainPanel.refresh();
        String displayName = osrsLoginManager.getPlayerDisplayName();
        if(displayName != null) {
            flipManager.setIntervalAccount(null);
            flipManager.setIntervalStartTime(sessionManager.getCachedSessionData().startTime);
            transactionManager.scheduleSyncIn(0, displayName);
        }
        flipManager.setCopilotUserId(loginResponse.getUserId());
        for (Market market : Market.values()) loadCopilotAccounts(market, 0);
    }

    public void onLoginFailure(String errorMessage) {
        copilotLoginRS.set(new CopilotLoginState());
        loginPanel.showLoginErrorMessage(errorMessage);
    }

    public Integer getActiveAccountId() {
        String displayName = osrsLoginManager.getPlayerDisplayName();
        if (displayName == null) {
            return null;
        }
        Integer accountId = copilotLoginRS.get().getAccountId(displayName);
        if (accountId == null || accountId == -1) {
            return null;
        }
        return accountId;
    }
}
