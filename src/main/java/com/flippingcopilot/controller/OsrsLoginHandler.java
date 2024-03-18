package com.flippingcopilot.controller;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameStateChanged;

@Slf4j
public class OsrsLoginHandler {
    private boolean previouslyLoggedIn;
    @Getter
    private String currentDisplayName;
    @Getter
    private String previousDisplayName;
    private final FlippingCopilotPlugin plugin;
    private final WorldType[] unSupportedWorlds = {WorldType.BETA_WORLD,
                                                    WorldType.DEADMAN,
                                                    WorldType.FRESH_START_WORLD,
                                                    WorldType.NOSAVE_MODE,
                                                    WorldType.PVP_ARENA,
                                                    WorldType.SEASONAL,
                                                    WorldType.QUEST_SPEEDRUNNING,
                                                    WorldType.TOURNAMENT_WORLD};

    @Getter
    private boolean invalidState = true;

    OsrsLoginHandler(FlippingCopilotPlugin plugin) {
        this.plugin = plugin;
        previouslyLoggedIn = false;
        currentDisplayName = null;
    }

    boolean isLoggedIn() {
        return currentDisplayName != null;
    }


    void init() {
        if (plugin.client.getGameState() == GameState.LOGGED_IN) {
            onLoggedInGameState();
            plugin.offerEventFilter.setToLoggedIn();
            plugin.accountStatus.setOffers(plugin.client.getGrandExchangeOffers());
        }
    }

    void handleGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGED_IN) {
            onLoggedInGameState();
        } else if (event.getGameState() == GameState.LOGIN_SCREEN && previouslyLoggedIn) {
            //this randomly fired at night hours after i had logged off...so i'm adding this guard here.
            if (currentDisplayName != null && plugin.client.getGameState() != GameState.LOGGED_IN) {
                handleLogout();
            }
        }
    }

    private void onLoggedInGameState() {
        //keep scheduling this task until it returns true (when we have access to a display name)
        plugin.clientThread.invokeLater(() ->
        {
            //we return true in this case as something went wrong and somehow the state isn't logged in, so we don't
            //want to keep scheduling this task.
            if (plugin.client.getGameState() != GameState.LOGGED_IN) {
                return true;
            }

            final Player player = plugin.client.getLocalPlayer();

            //player is null, so we can't get the display name so, return false, which will schedule
            //the task on the client thread again.
            if (player == null) {
                return false;
            }

            final String name = player.getName();

            if (name == null) {
                return false;
            }

            if (name.equals("")) {
                return false;
            }
            previouslyLoggedIn = true;

            handleLogin(name);
            return true;
        });
    }

    public void handleLogin(String displayName) {
        for (WorldType worldType : unSupportedWorlds) {
            if (plugin.client.getWorldType().contains(worldType)) {
                log.info("World is a {}", worldType);
                plugin.mainPanel.copilotPanel.suggestionPanel.setMessage(worldType + " worlds<br>are not supported");
                invalidState = true;
                return;
            }
        }

        if (plugin.client.getAccountType().isIronman()) {
            log.info("account is an ironman");
            plugin.mainPanel.copilotPanel.suggestionPanel.setMessage("Ironman accounts<br>are not supported");
            invalidState = true;
            return;
        }

        plugin.accountStatus.setMember(plugin.client.getWorldType().contains(WorldType.MEMBERS));
        plugin.accountStatus.setDisplayName(displayName);
        currentDisplayName = displayName;
        if (previousDisplayName != null && !previousDisplayName.equals(displayName)) {
            plugin.flipTracker = new FlipTracker();
            plugin.mainPanel.copilotPanel.statsPanel.updateFlips(plugin.flipTracker, plugin.client);
        }
        previousDisplayName = displayName;
        invalidState = false;
    }
    public void handleLogout() {
        log.info("{} is logging out", currentDisplayName);
        currentDisplayName = null;
        plugin.offerEventFilter.onLogout();
        plugin.mainPanel.copilotPanel.suggestionPanel.suggestLogin();
    }

}
