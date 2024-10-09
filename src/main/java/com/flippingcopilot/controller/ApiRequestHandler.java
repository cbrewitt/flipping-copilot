package com.flippingcopilot.controller;

import com.flippingcopilot.model.*;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Singleton
public class ApiRequestHandler {

    private static final String serverUrl = System.getenv("FLIPPING_COPILOT_HOST") != null ? System.getenv("FLIPPING_COPILOT_HOST")  : "https://api.flippingcopilot.com";

    // dependencies
    private final OkHttpClient client;
    private final Gson gson;

    // state todo: would be cleaner if this class was stateless
    private String jwtToken = null;
    private LoginResponse loginResponse = null;

    @Inject
    public ApiRequestHandler(Gson gson, OkHttpClient client) {
        this.gson = gson;
        this.client = client;
    }

    public void onLogout() {
        jwtToken = null;
        loginResponse = null;
    }

    public void setLoginResponse(LoginResponse loginResponse) {
        this.loginResponse = loginResponse;
        this.jwtToken = loginResponse.jwt;
    }

    public void authenticate(String username, String password, Consumer<LoginResponse> callback) {
        Request request = new Request.Builder()
                .url(serverUrl + "/login")
                .addHeader("Authorization", Credentials.basic(username, password))
                .post(RequestBody.create(MediaType.get("application/json; charset=utf-8"), ""))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.accept(null);
            }
            @Override
            public void onResponse(Call call, Response response) {
                // todo: this response can be a non 200 in which case we deserialize a null jwt
                handleLoginResponse(response, callback);
            }
        });
    }

    private void handleLoginResponse(Response response, Consumer<LoginResponse> callback) {
        try {
            JsonObject responseJson = gson.fromJson(response.body().string(), JsonObject.class);
            loginResponse = gson.fromJson(responseJson, LoginResponse.class);
            if (response.isSuccessful()) {
                jwtToken = responseJson.get("jwt").getAsString();
            }
            callback.accept(loginResponse);
        } catch (IOException e) {
            callback.accept(null);
        }
        catch (JsonParseException e) {
            loginResponse = new LoginResponse(true, response.message(), null, -1);
            callback.accept(loginResponse);
        }
    }

    public Suggestion getSuggestion(AccountStatus accountStatus) throws IOException {
        JsonObject status = accountStatus.toJson(gson);
        JsonObject suggestionJson = doHttpRequest("POST", status, "/suggestion", JsonObject.class);
        return Suggestion.fromJson(suggestionJson, gson);
    }

    public ItemPrice getItemPrice(int itemId, String displayName) {
        JsonObject respObj = null;
        try {
            JsonObject body = new JsonObject();
            body.add("item_id", new JsonPrimitive(itemId));
            body.add("display_name", new JsonPrimitive(displayName));
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
                String errorMessage = "Unknown error";
                if (response.body() != null) {
                    String bodyStr = response.body().string();
                    try {
                        JsonObject errorJson = gson.fromJson(bodyStr, JsonObject.class);
                        if (errorJson.has("message")) {
                            errorMessage = errorJson.get("message").getAsString();
                        }
                    } catch (JsonSyntaxException e) {
                        log.warn("Unable to parse http {} response body ({}): {}", response.code(), e.getMessage(), bodyStr);
                    }
                }
                throw new HttpResponseException(response.code(), errorMessage);
            }
        } catch (JsonSyntaxException | IOException e) {
            throw new HttpResponseException(-1, "Unknown server error (possible system update)", e);
        }
    }
}
