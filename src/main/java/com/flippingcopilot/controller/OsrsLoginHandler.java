package com.flippingcopilot.controller;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameStateChanged;

import static com.flippingcopilot.util.AtomicReferenceUtils.ifPresent;

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
    @Getter
    private int validStateGameTick = 0;

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
            onLoggedInGameState(false);
            plugin.offerEventFilter.setToLoggedIn();
        }
    }

    void handleGameStateChanged(GameStateChanged event) {
        log.debug("handling logged in game state {}", plugin.client.getGameState());
        if (event.getGameState() == GameState.HOPPING) {
            log.debug("setting expect empty offers due to hopping world");
            plugin.offerEventFilter.setExpectEmptyOffers();
        } else if (event.getGameState() == GameState.LOGGED_IN) {
            onLoggedInGameState(true);
        }  else if (event.getGameState() == GameState.LOGIN_SCREEN && previouslyLoggedIn) {
            //this randomly fired at night hours after i had logged off...so i'm adding this guard here.
            if (currentDisplayName != null && plugin.client.getGameState() != GameState.LOGGED_IN) {
                handleLogout();
            }
        }
    }

    private String loadPlayerDisplayName() {
        final Player player = plugin.client.getLocalPlayer();
        if (player != null) {
            final String name = player.getName();
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }
        return null;
    }

    private void onLoggedInGameState(boolean resetExpectEmptyOffers) {
        // note: for some reason when the LOGGED_IN event is received the local player may not be set yet
        //keep scheduling this task until it returns true (when we have access to a display name)
        plugin.clientThread.invokeLater(() ->
        {
            if (plugin.client.getGameState() != GameState.LOGGED_IN) {
                return true;
            }
            final String name = loadPlayerDisplayName();
            if(name == null) {
                return false;
            }
            previouslyLoggedIn = true;
            handleLogin(name, resetExpectEmptyOffers);
            return true;
        });
    }

    public void handleLogin(String displayName, boolean resetExpectEmptyOffers) {
        log.debug("{} logging in", displayName);
        SessionManager sm = new SessionManager(displayName, () -> plugin.mainPanel.copilotPanel.statsPanel.updateStatsAndFlips(false));
        plugin.sessionManager.set(sm);
        ifPresent(plugin.flipManager, i -> {
            i.setIntervalDisplayName(sm.getDisplayName());
            i.setIntervalStartTime(sm.getData().startTime);
        });
        ifPresent(plugin.transactionManager, TransactionManger::cancelOngoingSync);
        TransactionManger tm = new TransactionManger(plugin.flipManager, plugin.executorService, plugin.apiRequestHandler, displayName);
        plugin.transactionManager.set(tm);

        plugin.mainPanel.copilotPanel.statsPanel.isLoggedOut = false;
        plugin.mainPanel.copilotPanel.statsPanel.resetIntervalDropdownToSession();
        plugin.mainPanel.copilotPanel.statsPanel.resetRsAccountDropdown();
        plugin.mainPanel.copilotPanel.statsPanel.updateStatsAndFlips(true);

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
        if (!currentDisplayName.equals(previousDisplayName)) {
            plugin.accountStatus.loadPreviousOffers(currentDisplayName);
        }
        previousDisplayName = displayName;
        log.debug("setting expect empty offers due to osrs login");
        if(resetExpectEmptyOffers) {
            plugin.offerEventFilter.setExpectEmptyOffers();
        } else {
            plugin.accountStatus.setOffers(plugin.client.getGrandExchangeOffers());
        }
        plugin.mainPanel.copilotPanel.suggestionPanel.pauseButton.setPausedState(Persistance.loadIsPaused(displayName));

        validStateGameTick = plugin.client.getTickCount();
        invalidState = false;
        plugin.processQueuedOfferEvents();
    }

    public void handleLogout() {
        log.debug("{} is logging out", currentDisplayName);
        plugin.mainPanel.copilotPanel.statsPanel.isLoggedOut = true;
        log.debug("setting expect empty offers due to osrs logout");
        plugin.offerEventFilter.setExpectEmptyOffers();
        plugin.accountStatus.onLogout(currentDisplayName);
        currentDisplayName = null;
        plugin.mainPanel.copilotPanel.statsPanel.updateStatsAndFlips(true);
        plugin.mainPanel.copilotPanel.suggestionPanel.suggestLogin();
        invalidState = true;
    }
}
