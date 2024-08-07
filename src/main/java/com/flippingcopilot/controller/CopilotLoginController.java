package com.flippingcopilot.controller;

import java.io.IOException;
import java.util.function.Consumer;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import com.flippingcopilot.ui.LoginPanel;
import com.flippingcopilot.model.LoginResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.swing.JOptionPane;

@Slf4j
public class CopilotLoginController {
    @Getter
    LoginPanel panel;
    private String email;
    private String password;
    private final Runnable changeToLoggedInViewRunnable;
    private final ApiRequestHandler apiRequestHandler;
    private final FlippingCopilotPlugin plugin;
    @Setter
    @Getter
    private boolean loggedIn = false;


    public CopilotLoginController(Runnable changeToLoggedInViewRunnable, FlippingCopilotPlugin plugin) {
        this.changeToLoggedInViewRunnable = changeToLoggedInViewRunnable;
        Consumer<String> onEmailTextChangedListener = this::onEmailTextChanged;
        Consumer<String> onPasswordTextChangedListener = this::onPasswordTextChanged;
        ActionListener onLoginPressedListener = this::onLoginPressed;
        this.panel = new LoginPanel(
                onEmailTextChangedListener,
                onPasswordTextChangedListener,
                onLoginPressedListener
        );
        this.plugin = plugin;
        this.apiRequestHandler = plugin.apiRequestHandler;
    }

    public void onLoginPressed(ActionEvent event) {
        Consumer<LoginResponse> loginCallback = loginResponse -> {
            if (loginResponse != null && !loginResponse.error) {
                changeToLoggedInViewRunnable.run();
                loggedIn = true;
                if (plugin.osrsLoginHandler.isLoggedIn()) {
                    plugin.suggestionHandler.setSuggestionNeeded(true);
                }
            } else {
                String message = "Login failed";
                if(loginResponse != null) {
                    message = loginResponse.message;
                }
                panel.showLoginErrorMessage(message);
            }
            try {
                Persistance.saveLoginResponse(loginResponse);
            } catch (IOException e) {
                log.error("Failed to save login response");
            }
            panel.endLoading();
        };

        if (this.email == null || this.password == null) {
            return;
        }
        panel.startLoading();
        apiRequestHandler.authenticate(this.email, this.password, loginCallback);
    }

    public void onLogout() {
        loggedIn = false;
        apiRequestHandler.onLogout();
        Persistance.deleteLoginResponse();
        plugin.suggestionHandler.setCurrentSuggestion(null);
        plugin.highlightController.removeAll();
    }

    public void onEmailTextChanged(String newEmail) {
        this.email = newEmail;
    }

    public void onPasswordTextChanged(String newPassword) {
        this.password = newPassword;
    }
}
