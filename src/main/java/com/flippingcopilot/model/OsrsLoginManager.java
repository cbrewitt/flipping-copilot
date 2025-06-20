package com.flippingcopilot.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.EnumSet;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class OsrsLoginManager {

    public static final int GE_LOGIN_BURST_WINDOW = 2; // ticks

    public static String LOGIN_TO_GET_SUGGESTION_MESSAGE = "Log in to the game<br>to get a flip suggestion";
    private static final WorldType[] COPILOT_UNSUPPORTED_WORLDS = {WorldType.BETA_WORLD,
            WorldType.DEADMAN,
            WorldType.FRESH_START_WORLD,
            WorldType.NOSAVE_MODE,
            WorldType.PVP_ARENA,
            WorldType.SEASONAL,
            WorldType.QUEST_SPEEDRUNNING,
            WorldType.TOURNAMENT_WORLD};

    private final Client client;

    private String cachedDisplayName;
    private long lastAccountHash;

    @Getter
    @Setter
    private int lastLoginTick;

    private boolean lastIsIronman = false;

    public boolean isMembersWorld() {
        return client.getWorldType().contains(WorldType.MEMBERS);
    }

    public boolean isAccountMember()
    {
        int daysLeft = client.getVarpValue(VarPlayer.MEMBERSHIP_DAYS);
        return daysLeft > 0;
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

    // todo: inline this method
    public Long getAccountHash() {
        return client.getAccountHash();
    }

    public String getPlayerDisplayName() {
        long accountHash = client.getAccountHash();
        if(lastAccountHash == accountHash && cachedDisplayName != null) {
            return cachedDisplayName;
        }
        final Player player = client.getLocalPlayer();
        if (player != null) {
            final String name = player.getName();
            if (name != null && !name.isEmpty()) {
                lastAccountHash = accountHash;
                cachedDisplayName = name;
                return name;
            }
        }
        return null;
    }

    public String getLastDisplayName() {
        return cachedDisplayName != null ? cachedDisplayName : getPlayerDisplayName();
    }

    public void reset() {

    }

    public boolean hasJustLoggedIn() {
        return client.getTickCount() <= lastLoginTick + GE_LOGIN_BURST_WINDOW;
    }
}
