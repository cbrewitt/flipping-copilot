package com.flippingcopilot.model;

import net.runelite.api.WorldType;
import java.util.Set;

public enum Market {
    OSRS("osrs", "", 0.02), HC("osrs-hc", "osrs_hc_", 0);

    public final String id, filePrefix;
    public final double taxRate;
    Market(String id, String filePrefix, double taxRate) {
        this.id = id; this.filePrefix = filePrefix; this.taxRate = taxRate;
    }
    public static Market fromWorlds(Set<WorldType> worlds) {
        return worlds != null && worlds.contains(WorldType.HARDCORE_MAIN) ? HC : OSRS;
    }
    public String path(String path) {
        return this == OSRS ? path : path.substring(0, 3) + "/osrs-hc" + path.substring(3);
    }
    public String storageKey(String account) { return this == OSRS ? account : id + ":" + account; }
}
