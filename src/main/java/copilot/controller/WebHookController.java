package copilot.controller;

import static copilot.ui.UIUtilities.*;
import copilot.config.*;
import copilot.model.*;
import joptsimple.internal.*;
import lombok.*;
import lombok.extern.slf4j.*;
import okhttp3.*;

import javax.inject.*;
import java.io.*;
import java.time.*;

import static copilot.util.DateUtil.formatEpoch;
import static net.runelite.http.api.RuneLiteAPI.GSON;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class WebHookController {

    private final CopilotConfig config;
    private final OkHttpClient okHttpClient;

    private void sendWebHook(DiscordWebhookBody discordWebhookBody) {
        String configURL = config.webhook();
        if (Strings.isNullOrEmpty(configURL)) {return; }

        var url = HttpUrl.parse(configURL);
        if (url == null) {
            log.warn("bad discord webhook url {}", configURL);
            return;
        }
        var requestBodyBuilder = new MultipartBody.Builder()
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
            public void onFailure(Call call, IOException e) { log.debug("Error on webhook", e); }

            @Override
            public void onResponse(Call call, Response response) { response.close(); }
        });
    }

    public void sendMessage(Stats stats, SessionData sd, String displayName, boolean sessionIsFinished) {
        if (stats.profit != 0 && displayName != null) {

            long seconds = sd.durationMillis / 1000;
            String beganAtText = formatEpoch(sd.startTime);
            String endedAtText = sessionIsFinished ? formatEpoch(Instant.now().getEpochSecond()) : "n/a";
            String durationText = String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
            String profitText = formatProfit(stats.profit), taxText = formatProfit(stats.taxPaid);
            String roiText = String.format("%.3f%%", stats.calculateRoi() * 100);
            String cashStackText = quantityToRSDecimalStack(Math.abs(sd.averageCash), false) + " gp";

            String template = "%s, your session stats are:\n" +
                    "```" +
                    "Session began at:      %s\n" +
                    "Session ended at:      %s\n" +
                    "Active session time:   %s\n" +
                    "Flips made:            %d\n" +
                    "Profit:                %s\n" +
                    "Tax paid:              %s\n" +
                    "Roi:                   %s\n" +
                    "Avg wealth:            %s\n" +
                    "```";

            String discordMessage = String.format(template, displayName, beganAtText, endedAtText, durationText, stats.flipsMade, profitText, taxText, roiText, cashStackText);
            var discordWebhookBody = new DiscordWebhookBody();
            discordWebhookBody.setContent(discordMessage);
            sendWebHook(discordWebhookBody);
        }
    }
}
