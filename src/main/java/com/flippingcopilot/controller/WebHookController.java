package com.flippingcopilot.controller;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.model.DiscordWebhookBody;
import com.flippingcopilot.model.SessionData;
import com.flippingcopilot.model.Suggestion;
import com.flippingcopilot.model.Stats;
import com.flippingcopilot.ui.UIUtilities;
import com.flippingcopilot.util.ProfitCalculator;
import joptsimple.internal.Strings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Collections;

import static net.runelite.http.api.RuneLiteAPI.GSON;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class WebHookController {

    private static final int COLOR_BUY = 0x43B581;
    private static final int COLOR_SELL = 0xF04747;
    private static final int COLOR_DUMP = 0xFFA500;
    private static final int COLOR_ABORT = 0xF04747;
    private static final int COLOR_COLLECT = 0xFAA61A;
    private static final int COLOR_SUMMARY = 0x7289DA;

    private final FlippingCopilotConfig config;
    private final OkHttpClient okHttpClient;
    private final ProfitCalculator profitCalculator;

    private volatile boolean clientHasFocus = true;

    private final Object alertCacheLock = new Object();
    private final Deque<String> lastAlertKeys = new ArrayDeque<>(4);

    public void setClientHasFocus(boolean focused) {
        this.clientHasFocus = focused;
    }

    private void sendWebHook(DiscordWebhookBody discordWebhookBody) {
        String configURL = config.webhook();
        if (Strings.isNullOrEmpty(configURL)) { return; }

        HttpUrl url = HttpUrl.parse(configURL);
        if (url == null) {
            log.warn("bad discord webhook url {}", configURL);
            return;
        }

        // Prepend mention to all messages if provided
        if (discordWebhookBody.getContent() != null) {
            String mention = config.webhookMentionText();
            if (mention != null && !mention.trim().isEmpty()) {
                discordWebhookBody.setContent(mention + " " + discordWebhookBody.getContent());
            }
        } else {
            String mention = config.webhookMentionText();
            if (mention != null && !mention.trim().isEmpty()) {
                discordWebhookBody.setContent(mention);
            }
        }

        MultipartBody.Builder requestBodyBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("payload_json", GSON.toJson(discordWebhookBody));
        buildRequestAndSend(url, requestBodyBuilder);
    }

    private void buildRequestAndSend(HttpUrl url, MultipartBody.Builder requestBodyBuilder) {
        RequestBody requestBody = requestBodyBuilder.build();
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();
        sendRequest(request);
    }

    private void sendRequest(Request request) {
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.debug("Error on webhook", e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                response.close();
            }
        });
    }

    public void sendMessage(Stats stats, SessionData sd, String displayName, boolean sessionIsFinished) {
        if (stats.profit != 0 && displayName != null) {
            DiscordWebhookBody discordWebhookBody = new DiscordWebhookBody();
            discordWebhookBody.setContent(null);
            discordWebhookBody.setTts(false);

            String desc = formatStatsSummary(stats, sd);
            discordWebhookBody.setEmbeds(Collections.singletonList(buildEmbedWithAuthor(
                    "Session Summary",
                    desc,
                    COLOR_SUMMARY,
                    "STATS"
            )));
            sendWebHook(discordWebhookBody);
        }
    }

    public void sendDumpAlert(String itemName, int price) {
        if (!config.enableWebhookDumpAlerts()) { return; }
        if (config.webhookAlertsOnlyWhenOutOfFocus() && clientHasFocus) { return; }
        if (itemName == null || itemName.isBlank() || price <= 0) { return; }
        sendItemPriceEmbed("Dump", itemName, price, COLOR_DUMP);
    }

    public void sendDumpAlert(Suggestion suggestion) {
        if (suggestion == null) { return; }
        if (!config.enableWebhookDumpAlerts()) { return; }
        if (config.webhookAlertsOnlyWhenOutOfFocus() && clientHasFocus) { return; }
        if (suggestion.getName() == null || suggestion.getName().isBlank() || suggestion.getPrice() <= 0) { return; }
        if (isDuplicateAlertKey(buildAlertKey("dump", suggestion))) { return; }
        sendSuggestionEmbed("Dump", suggestion, COLOR_DUMP);
    }

    public void sendBuyAlert(String itemName, int price) {
        if (!config.enableWebhookBuyAlerts()) { return; }
        if (config.webhookAlertsOnlyWhenOutOfFocus() && clientHasFocus) { return; }
        if (itemName == null || itemName.isBlank() || price <= 0) { return; }
        sendItemPriceEmbed("Buy", itemName, price, COLOR_BUY);
    }

    public void sendBuyAlert(Suggestion suggestion) {
        if (suggestion == null) { return; }
        if (!config.enableWebhookBuyAlerts()) { return; }
        if (config.webhookAlertsOnlyWhenOutOfFocus() && clientHasFocus) { return; }
        if (suggestion.getName() == null || suggestion.getName().isBlank() || suggestion.getPrice() <= 0) { return; }
        if (isDuplicateAlertKey(buildAlertKey("buy", suggestion))) { return; }
        sendSuggestionEmbed("Buy", suggestion, COLOR_BUY);
    }

    public void sendSellAlert(String itemName, int price) {
        if (!config.enableWebhookSellAlerts()) { return; }
        if (config.webhookAlertsOnlyWhenOutOfFocus() && clientHasFocus) { return; }
        if (itemName == null || itemName.isBlank() || price <= 0) { return; }
        sendItemPriceEmbed("Sell", itemName, price, COLOR_SELL);
    }

    public void sendSellAlert(Suggestion suggestion) {
        if (suggestion == null) { return; }
        if (!config.enableWebhookSellAlerts()) { return; }
        if (config.webhookAlertsOnlyWhenOutOfFocus() && clientHasFocus) { return; }
        if (suggestion.getName() == null || suggestion.getName().isBlank() || suggestion.getPrice() <= 0) { return; }
        if (isDuplicateAlertKey(buildAlertKey("sell", suggestion))) { return; }
        sendSuggestionEmbed("Sell", suggestion, COLOR_SELL);
    }

    public void sendAbortAlert(Suggestion suggestion) {
        if (suggestion == null) { return; }
        if (!config.enableWebhookAbortCollectAlerts()) { return; }
        if (config.webhookAlertsOnlyWhenOutOfFocus() && clientHasFocus) { return; }
        if (suggestion.getName() == null || suggestion.getName().isBlank()) { return; }
        if (isDuplicateAlertKey(buildAlertKey("abort", suggestion))) { return; }

        String desc = "Abort offer\n\n```\n\uD83D\uDED1 Abort: " + suggestion.getName() + "\n```";
        sendGenericEmbed("Abort", suggestion.getName(), desc, COLOR_ABORT, buildItemThumbnailUrl(suggestion.getItemId()));
    }

    public void sendCollectAlert() {
        if (!config.enableWebhookAbortCollectAlerts()) { return; }
        if (config.webhookAlertsOnlyWhenOutOfFocus() && clientHasFocus) { return; }
        if (isDuplicateAlertKey("collect")) { return; }

        String desc = "Collect items\n\n```\n\uD83D\uDCE5 Collect items from the Grand Exchange\n```";
        sendGenericEmbed("Collect", "Collect items", desc, COLOR_COLLECT, null);
    }

    private void sendItemPriceEmbed(String action, String itemName, int price, int color) {
        String formattedPrice = formatGp(price);
        String desc = formatActionDescription(action, itemName, formattedPrice, price);
        DiscordWebhookBody body = new DiscordWebhookBody();
        body.setContent(null);
        body.setTts(false);
        body.setEmbeds(Collections.singletonList(buildEmbedWithAuthor(
                itemName,
                desc,
                color,
                action.toUpperCase(Locale.ENGLISH)
        )));
        sendWebHook(body);
    }

    private void sendSuggestionEmbed(String action, Suggestion suggestion, int color) {
        String formattedPrice = formatGp(suggestion.getPrice());

        Double expectedProfit = suggestion.getExpectedProfit();
        if (expectedProfit == null && "Sell".equalsIgnoreCase(action)) {
            try {
                Long estimated = profitCalculator.calculateSuggestionProfit(suggestion);
                if (estimated != null) {
                    expectedProfit = (double) estimated;
                }
            } catch (Exception ignored) {
                // keep webhook resilient; omit profit if cannot be estimated
            }
        }

        String desc = formatActionDescription(
                action,
                suggestion.getName(),
                formattedPrice,
                suggestion.getPrice(),
                suggestion.getQuantity(),
                expectedProfit,
                suggestion.getExpectedDuration()
        );

        String thumbnailUrl = buildItemThumbnailUrl(suggestion.getItemId());
        DiscordWebhookBody body = new DiscordWebhookBody();
        body.setContent(null);
        body.setTts(false);
        body.setEmbeds(Collections.singletonList(buildEmbedWithAuthor(
                suggestion.getName(),
                desc,
                color,
                action.toUpperCase(Locale.ENGLISH),
                thumbnailUrl
        )));
        sendWebHook(body);
    }

    private static DiscordWebhookBody.Embed buildEmbedWithAuthor(String title, String description, int color, String authorName) {
        return buildEmbedWithAuthor(title, description, color, authorName, null);
    }

    private static DiscordWebhookBody.Embed buildEmbedWithAuthor(String title, String description, int color, String authorName, String thumbnailUrl) {
        DiscordWebhookBody.Embed e = new DiscordWebhookBody.Embed();
        e.setTitle(title);
        e.setDescription(description);
        e.setColor(color);

        DiscordWebhookBody.Author a = new DiscordWebhookBody.Author();
        a.setName(authorName);
        e.setAuthor(a);

        if (thumbnailUrl != null && !thumbnailUrl.isBlank()) {
            DiscordWebhookBody.Thumbnail t = new DiscordWebhookBody.Thumbnail();
            t.setUrl(thumbnailUrl);
            e.setThumbnail(t);
        }
        return e;
    }

    private static String buildItemThumbnailUrl(int itemId) {
        if (itemId <= 0) {
            return null;
        }
        // OSRS plugin: use the Old School item database so IDs match.
        return "https://secure.runescape.com/m=itemdb_oldschool/obj_sprite.gif?id=" + itemId;
    }

    private void sendGenericEmbed(String author, String title, String description, int color, String thumbnailUrl) {
        DiscordWebhookBody body = new DiscordWebhookBody();
        body.setContent(null);
        body.setTts(false);
        body.setEmbeds(Collections.singletonList(buildEmbedWithAuthor(
                title,
                description,
                color,
                author.toUpperCase(Locale.ENGLISH),
                thumbnailUrl
        )));
        sendWebHook(body);
    }

    private static String formatGp(long gp) {
        return UIUtilities.quantityToRSDecimalStack(gp, false) + " gp";
    }

    private static String formatSignedGp(long gp) {
        return UIUtilities.quantityToRSDecimalStack(gp, true) + " gp";
    }

    private static String formatActionDescription(String action, String itemName, String formattedPrice, int price) {
        // Keep it lightweight but structured, similar to provided example.
        String header = String.format("%s for %s each", action, formattedPrice);

        StringBuilder sb = new StringBuilder();
        sb.append(header);
        sb.append("\n\n```");
        if ("Buy".equalsIgnoreCase(action)) {
            sb.append("\n⬇️ Insta-Buy:  ").append(formattedPrice);
        } else if ("Sell".equalsIgnoreCase(action)) {
            sb.append("\n⬆️ Insta-Sell: ").append(formattedPrice);
        } else {
            sb.append("\n⚠️ Dump Price: ").append(formattedPrice);
        }
        sb.append("\n```");
        return sb.toString();
    }

    private static String formatActionDescription(
            String action,
            String itemName,
            String formattedPrice,
            int price,
            int quantity,
            Double expectedProfit,
            Double expectedDurationSeconds
    ) {
        NumberFormat qtyFormat = NumberFormat.getNumberInstance(Locale.ENGLISH);
        String qty = quantity > 0 ? qtyFormat.format(quantity) : null;

        String header;
        if (qty != null) {
            header = String.format("%s %s for %s each", action, qty, formattedPrice);
        } else {
            header = String.format("%s for %s each", action, formattedPrice);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(header);
        sb.append("\n\n```");

        // Keep the block minimal: just qty + estimates
        if (qty != null) {
            sb.append("\nQty:        ").append(qty);
        }

        if (expectedProfit != null) {
            long profitGp = Math.round(expectedProfit);
            sb.append("\nEst profit:  ").append(formatSignedGp(profitGp));
        }

        if (expectedDurationSeconds != null) {
            sb.append("\nEst time:    ").append(formatDuration(expectedDurationSeconds));
        }

        sb.append("\n```");
        return sb.toString();
    }

    private static String formatDuration(double durationSeconds) {
        int totalMinutes = (int) Math.round(durationSeconds / 60.0);
        totalMinutes = Math.round(totalMinutes / 5.0f) * 5;
        totalMinutes = Math.max(totalMinutes, 5);

        if (totalMinutes < 60) {
            return totalMinutes + "min";
        }

        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        if (minutes == 0) {
            return hours + "h";
        }
        return hours + "h " + minutes + "m";
    }

    private static String buildAlertKey(String action, Suggestion suggestion) {
        String a = action == null ? "" : action;
        int itemId = suggestion == null ? -1 : suggestion.getItemId();
        int price = suggestion == null ? -1 : suggestion.getPrice();
        int qty = suggestion == null ? -1 : suggestion.getQuantity();
        return a + ":" + itemId + ":" + price + ":" + qty;
    }

    private boolean isDuplicateAlertKey(String key) {
        if (key == null || key.isBlank()) { return false; }
        synchronized (alertCacheLock) {
            if (lastAlertKeys.contains(key)) {
                return true;
            }
            lastAlertKeys.addFirst(key);
            while (lastAlertKeys.size() > 4) {
                lastAlertKeys.removeLast();
            }
            return false;
        }
    }

    private static String formatStatsSummary(Stats stats, SessionData sd) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm:ss a", Locale.ENGLISH);
        ZoneId zone = ZoneId.systemDefault();

        ZonedDateTime began = null;
        ZonedDateTime ended = null;
        String active = null;
        String avgWealth = null;

        if (sd != null) {
            began = Instant.ofEpochSecond(sd.startTime).atZone(zone);
            long durationSeconds = Math.max(0, sd.durationMillis / 1000);
            ended = Instant.ofEpochSecond(sd.startTime + (int) durationSeconds).atZone(zone);
            active = formatHhMmSs(durationSeconds);
            avgWealth = UIUtilities.quantityToRSDecimalStack(Math.abs(sd.averageCash), false) + " gp";
        }

        String roi = String.format(Locale.ENGLISH, "%.3f%%", stats.calculateRoi() * 100);

        StringBuilder sb = new StringBuilder();
        sb.append("```");
        if (began != null) {
            sb.append("\nSession began at:      ").append(dtf.format(began));
        }
        if (ended != null) {
            sb.append("\nSession ended at:      ").append(dtf.format(ended));
        }
        if (active != null) {
            sb.append("\nActive session time:   ").append(active);
        }
        sb.append("\nFlips made:            ").append(stats.flipsMade);
        sb.append("\nProfit:                ").append(UIUtilities.formatProfit(stats.profit));
        sb.append("\nTax paid:              ").append(UIUtilities.formatProfit(stats.taxPaid));
        sb.append("\nRoi:                   ").append(roi);
        if (avgWealth != null) {
            sb.append("\nAvg wealth:            ").append(avgWealth);
        }
        sb.append("\n```");
        return sb.toString();
    }

    private static String formatHhMmSs(long totalSeconds) {
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        return String.format(Locale.ENGLISH, "%02d:%02d:%02d", h, m, s);
    }
}
