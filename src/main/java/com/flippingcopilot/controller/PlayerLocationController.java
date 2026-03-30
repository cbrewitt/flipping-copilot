package com.flippingcopilot.controller;

import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PlayerLocationController {
    private static final WorldPoint GRAND_EXCHANGE_CENTER = new WorldPoint(3165, 3484, 0);
    private static final int NEAR_GE_DISTANCE_TILES = 20;

    private final Client client;

    public boolean isNearGE() {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) {
            return false;
        }

        WorldPoint playerLocation = localPlayer.getWorldLocation();
        if (playerLocation == null || playerLocation.getPlane() != GRAND_EXCHANGE_CENTER.getPlane()) {
            return false;
        }

        return playerLocation.distanceTo2D(GRAND_EXCHANGE_CENTER) <= NEAR_GE_DISTANCE_TILES;
    }
}
