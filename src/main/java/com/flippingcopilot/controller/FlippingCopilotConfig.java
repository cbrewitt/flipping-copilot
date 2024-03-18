package com.flippingcopilot.controller;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;


@ConfigGroup("flippingcopilot")
public interface FlippingCopilotConfig extends Config
{
    @ConfigItem(
            keyName = "webhook",
            name = "Webhook URL",
            description = "The Discord Webhook URL for sending display name and profit."
    )
    String webhook();
}