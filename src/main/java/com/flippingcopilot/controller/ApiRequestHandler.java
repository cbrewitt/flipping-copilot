package com.flippingcopilot.controller;

import com.flippingcopilot.model.*;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import okhttp3.*;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @javax.inject.Inject)
public class ApiRequestHandler {

    private static final String serverUrl = System.getenv("FLIPPING_COPILOT_HOST") != null ? System.getenv("FLIPPING_COPILOT_HOST")  : "https://api.flippingcopilot.com";

    // dependencies
    private final OkHttpClient client;
    private final Gson gson;
    private final LoginResponseManager loginResponseManager;
    private final SuggestionPreferencesManager preferencesManager;
    private final ClientThread clientThread;

    // state
    private Instant lastDebugMessageSent = Instant.now();


    public void authenticate(String username, String password, Runnable callback) {
        Request request = new Request.Builder()
                .url(serverUrl + "/login")
                .addHeader("Authorization", Credentials.basic(username, password))
                .post(RequestBody.create(MediaType.get("application/json; charset=utf-8"), ""))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.run();
            }
            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful()) {
                        log.warn("login failed with http status code {}", response.code());
                    }
                    String body = response.body() == null ? "" : response.body().string();
                    LoginResponse loginResponse = gson.fromJson(body, LoginResponse.class);
                    loginResponseManager.setLoginResponse(loginResponse);
                } catch (IOException e) {
                    log.warn("error reading/decoding login response body", e);
                } catch (JsonParseException e) {
                    loginResponseManager.setLoginResponse(new LoginResponse(true, response.message(), null, -1));
                } finally {
                    callback.run();
                }
            }
        });
    }

    public void getSuggestionAsync(JsonObject status, Consumer<Suggestion> onSuccess, Consumer<HttpResponseException>  onFailure) {
        log.debug("sending status {}", status.toString());
        Request request = new Request.Builder()
            .url(serverUrl + "/suggestion")
            .addHeader("Authorization", "Bearer " + loginResponseManager.getJwtToken())
            .post(RequestBody.create(MediaType.get("application/json; charset=utf-8"), status.toString()))
            .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("call to get suggestion failed", e);
                clientThread.invoke(() -> onFailure.accept(new HttpResponseException(-1, "Unknown Error")));
            }
            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful()) {
                        log.warn("get suggestion failed with http status code {}", response.code());
                        clientThread.invoke(() -> onFailure.accept(new HttpResponseException(response.code(), extractErrorMessage(response))));
                        return;
                    }
                    String body = response.body() == null ? "" : response.body().string();
                    Suggestion suggestion = gson.fromJson(body, Suggestion.class);
                    clientThread.invoke(() -> onSuccess.accept(suggestion));
                } catch (IOException | JsonParseException e) {
                    log.warn("error reading/parsing suggestion response body", e);
                    clientThread.invoke(() -> onFailure.accept(new HttpResponseException(-1, "Unknown Error")));
                }
            }
        });
    }

    private String extractErrorMessage(Response response) {
        if (response.body() != null) {
            try {
                String bodyStr = response.body().string();
                JsonObject errorJson = gson.fromJson(bodyStr, JsonObject.class);
                if (errorJson.has("message")) {
                    return errorJson.get("message").getAsString();
                }
            } catch (JsonSyntaxException | IOException e) {
                log.warn("failed reading/parsing error message from http {} response body", response.code(), e);
            }
        }
        return "Unknown Error";
    }

    public ItemPrice getItemPrice(int itemId, String displayName) {
        JsonObject respObj = null;
        try {
            JsonObject body = new JsonObject();
            body.add("item_id", new JsonPrimitive(itemId));
            body.add("display_name", new JsonPrimitive(displayName));
            body.addProperty("f2p_only", preferencesManager.getPreferences().isF2pOnlyMode());
            return doHttpRequest("POST", body, "/prices", ItemPrice.class);
        } catch (HttpResponseException e) {
            log.error("error fetching copilot price for item {}, resp code {}", itemId, e.getResponseCode(), e);
            return new ItemPrice(0, 0, "Unable to fetch price copilot price (possible server update)");
        }
    }

    public List<FlipV2> SendTransactions(List<Transaction> transactions, String displayName) throws HttpResponseException {
        JsonArray body = new JsonArray();
        for (Transaction transaction : transactions) {
            body.add(transaction.toJsonObject());
        }
        String encodedDisplayName = URLEncoder.encode(displayName, StandardCharsets.UTF_8);
        Type respType = new TypeToken<List<FlipV2>>(){}.getType();
        return doHttpRequest("POST", body, "/profit-tracking/client-transactions?display_name=" + encodedDisplayName, respType);
    }

    public Map<String, Integer> loadUserDisplayNames() throws HttpResponseException {
        Type respType = new TypeToken<Map<String, Integer>>(){}.getType();
        Map<String, Integer> names = doHttpRequest("GET", null, "/profit-tracking/rs-account-names", respType);
        return names == null ? new HashMap<>() : names;
    }

    public List<FlipV2> LoadFlips() throws HttpResponseException {
        Type respType = new TypeToken<List<FlipV2>>(){}.getType();
        List<FlipV2> flips = doHttpRequest("GET", null, "/profit-tracking/client-flips", respType);
        return flips == null ? new ArrayList<>() : flips;
    }

    public <T> T doHttpRequest(String method, JsonElement bodyJson, String route, Type responseType) throws HttpResponseException {
        String jwtToken = loginResponseManager.getJwtToken();
        if (jwtToken == null) {
            throw new IllegalStateException("Not authenticated");
        }

        RequestBody body = bodyJson == null ? null : RequestBody.create(MediaType.get("application/json; charset=utf-8"), bodyJson.toString());
        Request request = new Request.Builder()
                .url(serverUrl + route)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .method(method, body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                if (responseType == Void.class || response.body() == null) {
                    return null;
                }
                String responseBody = response.body().string();
                return gson.fromJson(responseBody, responseType);
            } else {
                throw new HttpResponseException(response.code(), extractErrorMessage(response));
            }
        } catch (JsonSyntaxException | IOException e) {
            throw new HttpResponseException(-1, "Unknown server error (possible system update)", e);
        }
    }

    public void sendDebugData(JsonObject bodyJson) {
        String jwtToken = loginResponseManager.getJwtToken();
        Instant now = Instant.now();
        if (now.minusSeconds(5).isBefore(lastDebugMessageSent)){
            // we don't want to spam
            return;
        }
        RequestBody body = RequestBody.create(MediaType.get("application/json; charset=utf-8"), bodyJson.toString());
        Request request = new Request.Builder()
                .url(serverUrl + "/debug-data")
                .addHeader("Authorization", "Bearer " + jwtToken)
                .method("POST", body)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
               log.debug("failed to send debug data", e);
            }
            @Override
            public void onResponse(Call call, Response response) {}
        });
        lastDebugMessageSent = Instant.now();
    }
}
