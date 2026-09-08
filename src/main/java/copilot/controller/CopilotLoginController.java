package copilot.controller;

import copilot.ui.*;
import java.util.function.*;
import java.util.concurrent.*;
import java.util.*;

import copilot.model.*;
import copilot.rs.*;
import lombok.*;
import lombok.extern.slf4j.*;

import javax.inject.*;

@Slf4j
@Singleton
public class CopilotLoginController {

    // dependencies
    @Setter
    private LoginPanel loginPanel;
    @Setter
    private MainPanel mainPanel;
    private final ApiClient api;
    private final FlipManager flipManager;
    private final PlayerLogin login;
    private final SessionManager sessionManager;
    private final Transactions transactionManager;
    private final ScheduledExecutorService executor;
    private final CopilotLogin copilotLogin;

    @Inject
    public CopilotLoginController(ApiClient api,
                                  FlipManager flipManager,
                                  HighlightController highlightController,
                                  Suggestions suggestionManager,
                                  PlayerLogin login,
                                  SessionManager sessionManager,
                                  Transactions transactionManager,
                                  ScheduledExecutorService executor,
                                  CopilotLogin copilotLogin) {
        this.api = api; this.flipManager = flipManager; this.login = login; this.sessionManager = sessionManager;
        this.transactionManager = transactionManager; this.executor = executor; this.copilotLogin = copilotLogin;
        flipManager.setCopilotUserId(copilotLogin.get().getUserId());
        loadCopilotAccounts(0);
        copilotLogin.registerListener((s) -> {
            if(s.loginResponse == null) {
                flipManager.reset();
                suggestionManager.reset();
                highlightController.removeAll();
                mainPanel.refresh();
            }
        });
    }

    private void loadCopilotAccounts(int previousFailures) {
        int userId = copilotLogin.get().getUserId();
        if (userId == -1) { return; }
        long s = System.nanoTime();
        Consumer<Map<String, Integer>> onSuccess = (displayNameToAccountId) -> {
            displayNameToAccountId.forEach((key, value) -> copilotLogin.addAccountIfMissing(value, key, userId));
            log.info("loading {} copilot accounts succeeded - took {}ms", displayNameToAccountId.size(), (System.nanoTime() - s) / 1000_000);
            syncFlips(copilotLogin.get().getUserId(), new HashMap<>(), 0);
        };
        Consumer<String> onFailure = (errorMessage) -> {
            if (copilotLogin.get().isLoggedIn()) {
                long backOffSeconds = Math.min(15, (long) Math.exp(previousFailures));
                log.info("failed to load copilot accounts ({}) retrying in {}s", errorMessage, backOffSeconds);
                executor.schedule(() -> loadCopilotAccounts(previousFailures + 1), backOffSeconds, TimeUnit.SECONDS);
            }
        };
        api.asyncLoadAccounts(onSuccess, onFailure);
    }

    private void syncFlips(int userId, Map<Integer, Integer> accountIdTime, int previousFailures) {
        // Continuously sync's the delta of new or updated flips from the server with back off on failure
        if(copilotLogin.get().getUserId() != userId) {
            log.info("user={}, no longer logged in, stopping syncFlips.", userId);
            return;
        }
        var accountIds = copilotLogin.get().accountIds();
        if(accountIds.isEmpty()) {
            long backOffSeconds = Math.min(45, (long) 1+previousFailures);
            log.info("user={}, no accounts loaded - re-scheduling runSyncFlips in {}s", userId, backOffSeconds);
            executor.schedule(() -> syncFlips(userId, accountIdTime, previousFailures+1), backOffSeconds, TimeUnit.SECONDS);
            return;
        }
        accountIds.forEach(a -> accountIdTime.computeIfAbsent(a, i -> 0));
        long s = System.nanoTime();
        BiConsumer<Integer, FlipsDelta> onSuccess = (Integer copilotUserId, FlipsDelta r) -> {
            if(!flipManager.mergeFlips(r.flips, userId)) {
                log.info("user={}, no longer logged in, stopping syncFlips.", userId);
                return;
            }
            log.debug("user={}, loading {} updated flips - took {}ms", userId, r.flips.size(), (System.nanoTime() - s) / 1000_000);
            accountIds.forEach((a) -> accountIdTime.put(a, r.time));
            executor.schedule(() -> syncFlips(userId, accountIdTime, 0), 5, TimeUnit.SECONDS);
        };
        Consumer<String> onFailure = (errorMessage) -> {
            long backOffSeconds = Math.min(45, (long) Math.exp(previousFailures));
            log.info("user={}, failed to load updated flips ({}) retrying in {}s", userId, errorMessage, backOffSeconds);
            executor.schedule(() -> syncFlips(userId, accountIdTime, previousFailures + 1), backOffSeconds, TimeUnit.SECONDS);
        };
        api.asyncLoadFlips(accountIdTime, onSuccess, onFailure);
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
        if (email == null || password == null) { return; }
        loginPanel.startLoading();
        api.authenticate(email, password, onSuccess, onFailure);
    }

    public void onLoginResponse(LoginResponse loginResponse) {
        copilotLogin.update((s) -> {
            s.loginResponse = loginResponse;
            return s;
        });
        mainPanel.refresh();
        String displayName = login.getPlayerDisplayName();
        if(displayName != null) {
            flipManager.setIntervalAccount(null);
            flipManager.setIntervalStartTime(sessionManager.getCachedSessionData().startTime);
            transactionManager.scheduleSyncIn(0, displayName);
        }
        flipManager.setCopilotUserId(loginResponse.userId);
        loadCopilotAccounts(0);
    }

    public void onLoginFailure(String errorMessage) {
        copilotLogin.set(new CopilotLoginState());
        loginPanel.showLoginErrorMessage(errorMessage);
    }

    public Integer getActiveAccountId() {
        String displayName = login.getPlayerDisplayName();
        if (displayName == null) { return null; }
        Integer accountId = copilotLogin.get().getAccountId(displayName);
        if (accountId == null || accountId == -1) { return null; }
        return accountId;
    }
}
