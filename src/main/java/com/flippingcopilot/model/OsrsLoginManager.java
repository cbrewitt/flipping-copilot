package com.flippingcopilot.model;

import net.runelite.api.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.EnumSet;

@Singleton
public class OsrsLoginManager {

    public static String LOGIN_TO_GET_SUGGESTION_MESSAGE = "Log in to the game<br>to get a flip suggestion";
    private static final WorldType[] COPILOT_UNSUPPORTED_WORLDS = {WorldType.BETA_WORLD,
            WorldType.DEADMAN,
            WorldType.FRESH_START_WORLD,
            WorldType.NOSAVE_MODE,
            WorldType.PVP_ARENA,
            WorldType.SEASONAL,
            WorldType.QUEST_SPEEDRUNNING,
            WorldType.TOURNAMENT_WORLD};

    @Inject
    private Client client;

    private String cachedDisplayName;
    private long lastAccountHash;

    private boolean lastIsIronman = false;

    public boolean isMembersWorld() {
        return client.getWorldType().contains(WorldType.MEMBERS);
    }

    public boolean isValidLoginState() {
        return getInvalidStateDisplayMessage() == null;
    }

    public String getInvalidStateDisplayMessage() {
        if (client.getGameState() != GameState.LOGGED_IN || getPlayerDisplayName() == null) {
            return LOGIN_TO_GET_SUGGESTION_MESSAGE;
        }

        EnumSet<WorldType> worldTypes  =  client.getWorldType();

        for (WorldType worldType : COPILOT_UNSUPPORTED_WORLDS) {
            if (worldTypes.contains(worldType)) {
                return worldType + " worlds<br>are not supported";
            }
        }
        if(client.isClientThread()) {
            lastIsIronman = client.getVarbitValue(Varbits.ACCOUNT_TYPE) != 0;
        }
        if (lastIsIronman) {
            return "Ironman accounts<br>are not supported";
        }
        return null;
    }

    public Long getAccountHash() {
        long accountHash = client.getAccountHash();
        if(accountHash != -1) {
            lastAccountHash = accountHash;
        }
        return accountHash;
    }

    public String getPlayerDisplayName() {
        if(lastAccountHash == getAccountHash() && cachedDisplayName != null) {
            return cachedDisplayName;
        }
        final Player player = client.getLocalPlayer();
        if (player != null) {
            final String name = player.getName();
            if (name != null && !name.isEmpty()) {
                cachedDisplayName = name;
                return name;
            }
        }
        return null;
    }

    public String getLastDisplayName() {
        return getPlayerDisplayName();
    }

    public void reset() {

    }
}
