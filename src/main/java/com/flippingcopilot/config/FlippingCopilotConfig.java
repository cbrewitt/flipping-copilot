package com.flippingcopilot.config;

import com.flippingcopilot.ui.UIUtilities;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;
import net.runelite.client.ui.ColorScheme;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;


@ConfigGroup("flippingcopilot")
public interface FlippingCopilotConfig extends Config
{
    public enum PriceGraphWebsite
    {
        FLIPPING_COPILOT("Flipping Copilot"),
        OSRS_WIKI("OSRS Wiki"),
        GE_TRACKER("GE Tracker"),
        PLATINUM_TOKENS("PlatinumTokens"),
        GE_DATABASE("GE Database"),
        OSRS_CLOUD("Osrs.cloud"),
        OSRS_EXCHANGE("OSRS Exchange"),
        FLIPPING_GG("Flipping.gg");


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
            switch (this)
            {
                case OSRS_WIKI:
                    return "https://prices.runescape.wiki/osrs/item/" + itemId;
                case GE_TRACKER:
                    return "https://www.ge-tracker.com/item/" + itemId;
                case PLATINUM_TOKENS:
                    String platinumTokensFormattedName = itemName
                            .toLowerCase()
                            .replace("'", "")
                            .replace("(", " ")
                            .replace(")", "")
                            .replace("+", " plus")
                            .replace("  ", " ")
                            .replace(" ", "-");
                    return "https://platinumtokens.com/item/" + platinumTokensFormattedName;
                case GE_DATABASE:
                    return "https://secure.runescape.com/m=itemdb_oldschool/viewitem?obj=" + itemId;
                case OSRS_EXCHANGE:
                    String osrsExchangeFormattedName = itemName.toLowerCase().replace(' ','-');
                    return "https://www.osrs.exchange/item/"
                            + URLEncoder.encode(osrsExchangeFormattedName, StandardCharsets.UTF_8);
                case OSRS_CLOUD:
                    return "https://prices.osrs.cloud/item/" + itemId;
                case FLIPPING_GG:
                    return "https://www.flipping.gg/items/" + itemId;
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
            keyName = "dumpAlertSound",
            name = "Dump alert sound",
            description = "Play the GE offer completed sound when a dump alert arrives."
    )
    default boolean dumpAlertSound()
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
            keyName = "chatTextColor",
            name = "Chat text color",
            description = "The color of the text for copilot messages in the chat."
    )
    default Color chatTextColor() {
        return new Color(0x0040FF);
    }
    @ConfigItem(
            keyName = "webhook",
            name = "Webhook URL",
            description = "The Discord Webhook URL for sending display name and profit."
    )
    String webhook();
    @ConfigItem(
            keyName = "priceGraphButton",
            name = "Graph button",
            description = "The page to open when the graph button is clicked."
    )
    default PriceGraphWebsite priceGraphWebsite()
    {
        return PriceGraphWebsite.FLIPPING_COPILOT;
    }
    @ConfigItem(
            keyName = "suggestionHighlights",
            name = "Highlight suggested actions",
            description = "Show highlight overlays on the GE interface for suggested actions."
    )
    default boolean suggestionHighlights()
    {
        return true;
    }
    @ConfigItem(
            keyName = "lowDataMode",
            name = "Low data mode",
            description = "When enabled, price graph data is only sent when opening the graph."
    )
    default boolean lowDataMode()
    {
        return false;
    }
    @ConfigItem(
            keyName = "misClickProtection",
            name = "Mis-click prevention",
            description = "Require right click to confirm when price/quantity set incorrectly"
    )
    default boolean disableLeftClickConfirm()
    {
        return false;
    }
    @ConfigItem(
            keyName = "quickSetKeybind",
            name = "Price/quantity set keybind",
            description = "Keybind to quickly set the price or quantity of a GE offer to the suggested value"
    )
    default Keybind quickSetKeybind()
    {
        return new Keybind(KeyEvent.VK_E, 0);
    }

    @ConfigItem(
            keyName = "enabledPriceGraphMenuOpton",
            name = "Enable price graph menu option",
            description = "Adds a menu option to open copilot price graph on applicable right clicks."
    )
    default boolean priceGraphMenuOptionEnabled()
    {
        return true;
    }
}
