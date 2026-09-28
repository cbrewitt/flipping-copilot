package com.flippingcopilot.util;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.banktags.BankTagsPlugin;

public final class BankTagsUtil {
    private BankTagsUtil() {
    }

    public static boolean isActive(PluginManager pluginManager) {
        // RuneLite 1.13 exports Bank Tags services, but not the plugin instance.
        for (Plugin plugin : pluginManager.getPlugins()) {
            if (plugin instanceof BankTagsPlugin) {
                return pluginManager.isPluginActive(plugin);
            }
        }
        return false;
    }
}
