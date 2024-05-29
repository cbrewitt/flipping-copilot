package com.flippingcopilot.controller;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;


@ConfigGroup("flippingcopilot")
public interface FlippingCopilotConfig extends Config
{
    @ConfigItem(
            keyName = "enableChatNotifications",
            name = "Enable chat Notifications",
            description = "Show chat notifications for suggested action when the side panel is closed."
    )
    default boolean enableChatNotifications()
    {
        return true;
    }
    @ConfigItem(
            keyName = "enableTrayNotifications",
            name = "Enable tray notifications",
            description = "Show tray notifications for suggested action when runelite is out of focus."
    )
    default boolean enableTrayNotifications()
    {
        return true;
    }
    @ConfigItem(
            keyName = "webhook",
            name = "Webhook URL",
            description = "The Discord Webhook URL for sending display name and profit."
    )
    String webhook();
}