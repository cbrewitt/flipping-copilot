package com.flippingcopilot.controller;

import com.flippingcopilot.model.DiscordWebhookBody;
import com.flippingcopilot.ui.UIUtilities;
import joptsimple.internal.Strings;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;

import static net.runelite.http.api.RuneLiteAPI.GSON;

@Slf4j
public class WebHookController {

    private final FlippingCopilotPlugin plugin;

    public WebHookController(FlippingCopilotPlugin plugin)
    {
        this.plugin = plugin;
    }

    private void sendWebHook(DiscordWebhookBody discordWebhookBody)
    {
        String configURL = plugin.config.webhook();
        if (Strings.isNullOrEmpty(configURL)) {return; }

        HttpUrl url = HttpUrl.parse(configURL);
        MultipartBody.Builder requestBodyBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("payload_json", GSON.toJson(discordWebhookBody));
        buildRequestAndSend(url, requestBodyBuilder);
    }

    private void buildRequestAndSend(HttpUrl url, MultipartBody.Builder requestBodyBuilder)
    {
        RequestBody requestBody = requestBodyBuilder.build();
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();
        sendRequest(request);
    }

    private void sendRequest(Request request)
    {
        plugin.okHttpClient.newCall(request).enqueue(new Callback() {
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
    void sendMessage()
    {
        long profit = plugin.flipTracker.getProfit();
        String displayName = plugin.osrsLoginHandler.getPreviousDisplayName();
        if (profit != 0 && displayName != null) {
            String profitText = UIUtilities.formatProfit(profit);
            profitText = (displayName + ", your Session Profit is " + profitText);
            DiscordWebhookBody discordWebhookBody = new DiscordWebhookBody();
            discordWebhookBody.setContent(profitText);
            sendWebHook(discordWebhookBody);
        }
    }
}
