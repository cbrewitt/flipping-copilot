package com.flippingcopilot.controller;

import java.awt.event.ActionEvent;

import com.flippingcopilot.model.*;
import com.flippingcopilot.ui.LoginPanel;
import com.flippingcopilot.ui.MainPanel;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class CopilotLoginController {

    // dependencies
    @Setter
    private LoginPanel loginPanel;
    @Setter
    private MainPanel mainPanel;
    private final ApiRequestHandler apiRequestHandler;
    private final FlipManager flipManager;
    private final HighlightController highlightController;
    private final LoginResponseManager loginResponseManager;
    private final SuggestionManager suggestionManager;
    private final OsrsLoginManager osrsLoginManager;
    private final SessionManager sessionManager;
    private final TransactionManger transactionManger;

    // state
    private String email;
    private String password;

    public void onLoginPressed(ActionEvent event) {
        Runnable loginCallback = () -> {
            if (loginResponseManager.isLoggedIn()) {
                flipManager.loadFlipsAsync();
                mainPanel.refresh();
                String displayName = osrsLoginManager.getPlayerDisplayName();
                if(displayName != null) {
                    flipManager.setIntervalDisplayName(displayName);
                    flipManager.setIntervalStartTime(sessionManager.getCachedSessionData().startTime);
                    transactionManger.scheduleSyncIn(0, displayName);
                }
            } else {
                LoginResponse loginResponse = loginResponseManager.getLoginResponse();
                String message = loginResponse != null ? loginResponse.message : "Login failed";
                loginPanel.showLoginErrorMessage(message);
            };
            loginPanel.endLoading();
        };

        if (this.email == null || this.password == null) {
            return;
        }
        loginPanel.startLoading();
        apiRequestHandler.authenticate(this.email, this.password, loginCallback);
    }

    public void onLogout() {
        flipManager.reset();
        loginResponseManager.reset();
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
