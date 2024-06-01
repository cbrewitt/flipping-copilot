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
    public enum PriceGraphWebsite
    {
        OSRS_WIKI("OSRS Wiki"),
        GE_TRACKER("GE Tracker"),
        PLATINUM_TOKENS("PlatinumTokens"),
        GE_DATABASE("GE Database"),
        OSRS_CLOUD("Osrs.cloud");

        private final String name;
        PriceGraphWebsite(String name)
        {
            this.name = name;
        }

        @Override
        public String toString()
        {
            return name;
        }

        public String getUrl(String itemName, int itemId)
        {
            String formattedItemName = itemName.toLowerCase().replace(" ", "-");
            switch (this)
            {
                case OSRS_WIKI:
                    return "https://prices.runescape.wiki/osrs/item/" + itemId;
                case GE_TRACKER:
                    return "https://www.ge-tracker.com/item/" + formattedItemName;
                case PLATINUM_TOKENS:
                    return "https://platinumtokens.com/item/" + formattedItemName;
                case GE_DATABASE:
                    return "https://secure.runescape.com/m=itemdb_oldschool/viewitem?obj=" + itemId;
                case OSRS_CLOUD:
                    return "https://prices.osrs.cloud/item/" + itemId;
                default:
                    return "";
            }
        }
    }


    @ConfigItem(
            keyName = "enableChatNotifications",
            name = "Enable chat notifications",
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
            name = "Flip tracker profit color",
            description = "The color of the profit amount text in the flip tracker"
    )
    default Color profitAmountColor() {
        return ColorScheme.GRAND_EXCHANGE_PRICE;
    }
    @ConfigItem(
            keyName = "lossAmountColor",
            name = "Flip tracker loss color",
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
    @ConfigItem(
            keyName = "priceGraphWebsite",
            name = "Price graph site",
            description = "The website to open for price graphs."
    )
    default PriceGraphWebsite priceGraphWebsite()
    {
        return PriceGraphWebsite.OSRS_WIKI;
    }
}