package com.flippingcopilot.config;

import com.flippingcopilot.ui.UIUtilities;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
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

    @ConfigSection(
            name = "Offer Setup Assistance",
            description = "Configure suggestion highlights and offer setup features",
            position = 1
    )
    String offerSetupSection = "offerSetupSection";

    @ConfigItem(
            keyName = "suggestionHighlights",
            name = "Highlight suggested actions",
            description = "Show highlight overlays on the GE interface for suggested actions.",
            section = offerSetupSection,
            position = 1
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
            description = "Require right click to confirm when price/quantity set incorrectly",
            section = offerSetupSection,
            position = 2
    )
    default boolean disableLeftClickConfirm()
    {
        return false;
    }

    @ConfigItem(
            keyName = "quickSetKeybind",
            name = "Price/quantity set keybind",
            description = "Keybind to quickly set the price or quantity of a GE offer to the suggested value",
            section = offerSetupSection,
            position = 3
    )
    default Keybind quickSetKeybind()
    {
        return new Keybind(KeyEvent.VK_E, 0);
    }

    @ConfigSection(
            name = "Appearance",
            description = "Configure visual appearance and colors",
            position = 2
    )
    String appearanceSection = "appearanceSection";

    @ConfigItem(
            keyName = "priceGraphMenuOptionEnabled",
            name = "Enable price graph menu option",
            description = "Adds a menu option to open copilot price graph on applicable right clicks.",
            section = appearanceSection,
            position = 1
    )
    default boolean priceGraphMenuOptionEnabled()
    {
        return true;
    }

    @ConfigItem(
            keyName = "priceGraphButton",
            name = "Graph button",
            description = "The page to open when the graph button is clicked.",
            section = appearanceSection,
            position = 2
    )
    default PriceGraphWebsite priceGraphWebsite()
    {
        return PriceGraphWebsite.FLIPPING_COPILOT;
    }

    @ConfigItem(
            keyName = "profitAmountColor",
            name = "Flip tracker profit color",
            description = "The color of the profit amount text in the flip tracker",
            section = appearanceSection,
            position = 3
    )
    default Color profitAmountColor() {
        return ColorScheme.GRAND_EXCHANGE_PRICE;
    }

    @ConfigItem(
            keyName = "lossAmountColor",
            name = "Flip tracker loss color",
            description = "The color of the loss amount text in the flip tracker",
            section = appearanceSection,
            position = 4
    )
    default Color lossAmountColor() {
        return UIUtilities.OUTDATED_COLOR;
    }

    @ConfigSection(
            name = "Slot Price Coloring",
            description = "Configure GE slot price colors based on profitability",
            position = 3
    )
    String slotPriceColorSection = "slotPriceColorSection";

    @ConfigItem(
            keyName = "slotPriceColorEnabled",
            name = "Enable slot price coloring",
            description = "Color GE slot prices based on profitability",
            section = slotPriceColorSection,
            position = 1
    )
    default boolean slotPriceColorEnabled()
    {
        return false;
    }

    @ConfigItem(
            keyName = "slotPriceProfitableColor",
            name = "Buy/Profitable color",
            description = "The color for buy offers at the suggested price, and profitable sell offers (flips)",
            section = slotPriceColorSection,
            position = 2
    )
    default Color slotPriceProfitableColor()
    {
        return new Color(0xAFDCFF);
    }

    @ConfigItem(
            keyName = "slotPriceUnprofitableColor",
            name = "Unprofitable color",
            description = "The color for sell offers (flips) that will result in a loss",
            section = slotPriceColorSection,
            position = 3
    )
    default Color slotPriceUnprofitableColor()
    {
        return new Color(0xFF5E5E);
    }

    @ConfigSection(
            name = "Notifications",
            description = "Configure notification settings for flipping actions",
            position = 4
    )
    String notificationsSection = "notificationsSection";

    @ConfigItem(
            keyName = "dumpAlertSound",
            name = "Dump alert sound",
            description = "Play the GE offer completed sound when a dump alert arrives.",
            section = notificationsSection,
            position = 1
    )
    default boolean dumpAlertSound()
    {
        return true;
    }

    @ConfigItem(
            keyName = "enableChatNotifications",
            name = "Enable chat notifications",
            description = "Show chat notifications for suggested action when the side panel is closed.",
            section = notificationsSection,
            position = 2
    )
    default boolean enableChatNotifications()
    {
        return true;
    }

    @ConfigItem(
            keyName = "enableTrayNotifications",
            name = "Enable tray notifications",
            description = "Show tray notifications for suggested action when runelite is out of focus.",
            section = notificationsSection,
            position = 3
    )
    default boolean enableTrayNotifications()
    {
        return true;
    }

    @ConfigItem(
            keyName = "chatTextColor",
            name = "Chat text color",
            description = "The color of the text for copilot messages in the chat.",
            section = notificationsSection,
            position = 4
    )
    default Color chatTextColor() {
        return new Color(0x0040FF);
    }

    @ConfigSection(
            name = "Webhook",
            description = "Configure webhook notifications",
            position = 5
    )
    String webhookSection = "webhookSection";

    @ConfigItem(
            keyName = "webhook",
            name = "Webhook URL",
            description = "The Discord Webhook URL.",
            section = webhookSection,
            position = 1
    )
    String webhook();

    @ConfigItem(
            keyName = "webhookMentionText",
            name = "Mention text",
            description = "Text to prepend to alerts, e.g. @YourName or <@1234567890>.",
            section = webhookSection,
            position = 2
    )
    default String webhookMentionText() { return ""; }

    @ConfigItem(
            keyName = "enableWebhookBuyAlerts",
            name = "Buy alerts",
            description = "Notify on buy alerts.",
            section = webhookSection,
            position = 3
    )
    default boolean enableWebhookBuyAlerts() { return false; }

    @ConfigItem(
            keyName = "enableWebhookSellAlerts",
            name = "Sell alerts",
            description = "Notify on sell alerts.",
            section = webhookSection,
            position = 4
    )
    default boolean enableWebhookSellAlerts() { return false; }

    @ConfigItem(
            keyName = "enableWebhookDumpAlerts",
            name = "Dump alerts",
            description = "Notify on dump alerts.",
            section = webhookSection,
            position = 5
    )
    default boolean enableWebhookDumpAlerts() { return true; }

    @ConfigItem(
            keyName = "enableWebhookAbortCollectAlerts",
            name = "Abort/Collect alerts",
            description = "Notify when you should abort an offer or collect items.",
            section = webhookSection,
            position = 6
    )
    default boolean enableWebhookAbortCollectAlerts() { return false; }

    @ConfigItem(
            keyName = "webhookAlertsOnlyWhenOutOfFocus",
            name = "Only alert when unfocused",
            description = "Only send Buy/Sell/Dump webhooks when RuneLite is out of focus. Session stats always send.",
            section = webhookSection,
            position = 7
    )
    default boolean webhookAlertsOnlyWhenOutOfFocus() { return false; }



}
