package com.flippingcopilot.controller;

import com.flippingcopilot.ui.UIUtilities;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.ui.ColorScheme;

import java.awt.*;


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
            keyName = "profitAmountColor",
            name = "Profit amount color",
            description = "The color of the profit amount text in the flip tracker"
    )
    default Color profitAmountColor() {
        return ColorScheme.GRAND_EXCHANGE_PRICE;
    }
    @ConfigItem(
            keyName = "lossAmountColor",
            name = "Loss amount color",
            description = "The color of the loss amount text in the flip tracker"
    )
    default Color lossAmountColor() {
        return UIUtilities.OUTDATED_COLOR;
    }
    @ConfigItem(
            keyName = "webhook",
            name = "Webhook URL",
            description = "The Discord Webhook URL for sending display name and profit."
    )
    String webhook();
}