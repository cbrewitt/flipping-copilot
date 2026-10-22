package com.flippingcopilot.controller;

import com.flippingcopilot.model.CopilotLoginState;
import com.flippingcopilot.model.OsrsLoginManager;
import com.flippingcopilot.model.OsrsLoginState;
import com.flippingcopilot.model.SessionManager;
import com.flippingcopilot.rs.CopilotLoginRS;
import com.flippingcopilot.rs.OsrsLoginRS;
import com.flippingcopilot.ui.StatsPanelV2;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

// Re-reads session files written by other clients so 'All accounts' includes their accounts.
@Slf4j
@Singleton
public class AllAccountsRefresher {

    private static final long REFRESH_PERIOD_SECONDS = 5;

    private final CopilotLoginRS copilotLoginRS;
    private final OsrsLoginRS osrsLoginRS;
    private final OsrsLoginManager osrsLoginManager;
    private final SessionManager sessionManager;
    private final ScheduledExecutorService executorService;

    private ScheduledFuture<?> task;

    @Inject
    public AllAccountsRefresher(CopilotLoginRS copilotLoginRS,
                                OsrsLoginRS osrsLoginRS,
                                OsrsLoginManager osrsLoginManager,
                                SessionManager sessionManager,
                                @Named("copilotExecutor") ScheduledExecutorService executorService) {
        this.copilotLoginRS = copilotLoginRS;
        this.osrsLoginRS = osrsLoginRS;
        this.osrsLoginManager = osrsLoginManager;
        this.sessionManager = sessionManager;
        this.executorService = executorService;
    }

    public synchronized void start(StatsPanelV2 statsPanel) {
        stop();
        task = executorService.scheduleWithFixedDelay(() -> refresh(statsPanel), 2, REFRESH_PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
    }

    private void refresh(StatsPanelV2 statsPanel) {
        try {
            CopilotLoginState login = copilotLoginRS.get();
            if (!login.isLoggedIn()) {
                return;
            }
            Map<String, Integer> accounts = new HashMap<>(login.displayNameToAccountId);
            OsrsLoginState osrsLogin = osrsLoginRS.get();
            String liveDisplayName = osrsLogin != null && osrsLogin.loggedIn ? osrsLogin.displayName : null;
            boolean sessionsChanged = sessionManager.refreshOtherAccountSessions(accounts, liveDisplayName);
            if (liveDisplayName == null) {
                return;
            }
            SwingUtilities.invokeLater(() -> {
                statsPanel.applySessionInterval();
                if (sessionsChanged) {
                    statsPanel.refresh(false, copilotLoginRS.get().isLoggedIn() && osrsLoginManager.isValidLoginState());
                }
            });
        } catch (Exception e) {
            log.warn("error refreshing other accounts' sessions", e);
        }
    }
}
