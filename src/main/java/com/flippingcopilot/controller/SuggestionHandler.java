package com.flippingcopilot.controller;

import com.flippingcopilot.model.HttpResponseException;
import com.flippingcopilot.model.Suggestion;
import com.flippingcopilot.ui.SuggestionPanel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatMessageBuilder;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

@Slf4j
@Getter
@Setter
public class SuggestionHandler {
    private Timer timer;
    private boolean suggestionNeeded;
    private Suggestion currentSuggestion;
    private FlippingCopilotPlugin plugin;
    private SuggestionPanel suggestionPanel;

    public SuggestionHandler(FlippingCopilotPlugin plugin) {
        this.plugin = plugin;
        this.suggestionPanel = plugin.mainPanel.copilotPanel.suggestionPanel;
        suggestionNeeded = false;
        resetTimer();
    }

    public void resetTimer() {
        if (timer != null) {
            timer.cancel();
        }
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                suggestionNeeded = true;
            }
        }, 10000);
    }

    public void skipCurrentSuggestion() {
        if (currentSuggestion != null) {
            plugin.accountStatus.setSkipSuggestion(currentSuggestion.getId());
            suggestionNeeded = true;
        }
    }

    void onGameTick() {
        if (!plugin.grandExchange.isSlotOpen() && suggestionNeeded) {
            getSuggestionAsync();
        }
    }

    private void getSuggestionAsync() {
        suggestionNeeded = false;
        plugin.executorService.execute(this::getSuggestion);
    }

    private void getSuggestion() {
        if (!plugin.osrsLoginHandler.isLoggedIn() || !plugin.copilotLoginController.isLoggedIn()) {
            return;
        }
        suggestionPanel.showLoading();
        try {
            log.info("Getting suggestion");
            Suggestion oldSuggestion = currentSuggestion;
            currentSuggestion = plugin.apiRequestHandler.getSuggestion(plugin.accountStatus);
            log.info("Received suggestion: " + currentSuggestion.toString());
            plugin.accountStatus.resetSkipSuggestion();
            displaySuggestion();
            showNotifications(oldSuggestion);
        } catch (HttpResponseException e) {
            handleHttpException(e);
        } catch (IOException e) {
            log.error("Error occurred while getting suggestion: ", e);
            suggestionPanel.setMessage(e.getMessage());
        } finally {
            resetTimer();
            suggestionPanel.hideLoading();
        }
    }

    void displaySuggestion() {
        if (plugin.osrsLoginHandler.isInvalidState()) {
            return;
        }
        suggestionPanel.setServerMessage(currentSuggestion.getMessage());
        if (plugin.accountStatus.isCollectNeeded(currentSuggestion)) {
            suggestionPanel.suggestCollect();
        } else if (currentSuggestion.getType().equals("wait") && plugin.accountStatus.moreGpNeeded()) {
            suggestionPanel.suggestAddGp();
        } else {
            suggestionPanel.updateSuggestion(currentSuggestion);
        }
    }

    void showNotifications(Suggestion oldSuggestion) {
        if (shouldNotify(currentSuggestion, oldSuggestion)) {
            plugin.notifier.notify(currentSuggestion.toMessage());
            if (!plugin.mainPanel.copilotPanel.isShowing()) {
                showChatNotifications();
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

    private void showChatNotifications() {
        if (plugin.accountStatus.isCollectNeeded(currentSuggestion)) {
            plugin.clientThread.invokeLater(() -> showChatNotification("Flipping Copilot: Collect items"));
        }
        plugin.clientThread.invokeLater(() -> showChatNotification(currentSuggestion.toMessage()));
    }

    private void showChatNotification(String message) {
        String chatMessage = new ChatMessageBuilder()
                .append(new Color(0x0040FF), message)
                .build();
        plugin.client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", chatMessage, "");
    }

    private void handleHttpException(HttpResponseException e) {
        if (e.getResponseCode() == 401) {
            plugin.copilotLoginController.onLogout();
            plugin.mainPanel.renderLoggedOutView();
            plugin.mainPanel.loginPanel.showLoginErrorMessage("Login timed out. Please log in again");
        } else {
            log.error("Error occurred while getting suggestion: ", e);
            suggestionPanel.setMessage("Error: " + e.getMessage());
        }
    }
}
