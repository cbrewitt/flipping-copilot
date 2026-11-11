package com.flippingcopilot.model;

import net.runelite.api.WorldType;
import java.util.Set;

public enum Market {
    OSRS("osrs", "OSRS", ""), HC("osrs-hc", "OSRS HC", "osrs_hc_");

    public final String id, label, filePrefix;
    Market(String id, String label, String filePrefix) {
        this.id = id; this.label = label; this.filePrefix = filePrefix;
    }
    public static Market fromWorlds(Set<WorldType> worlds) {
        return worlds != null && worlds.contains(WorldType.HARDCORE_MAIN) ? HC : OSRS;
    }
    public String path(String path) {
        return this == OSRS ? path : path.substring(0, 3) + "/osrs-hc" + path.substring(3);
    }
    public String storageKey(String account) { return this == OSRS ? account : id + ":" + account; }
    @Override public String toString() { return label; }
}
