package com.flippingcopilot.controller;

import com.flippingcopilot.model.*;
import com.flippingcopilot.msgpacklite.MsgpackDeserializer;
import com.flippingcopilot.ui.graph.model.Data;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import okhttp3.*;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
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

    public void getSuggestionAsync(JsonObject status,
                                   Consumer<Suggestion> suggestionConsumer,
                                   Consumer<Data> graphDataConsumer,
                                   Consumer<HttpResponseException>  onFailure) {
        log.debug("sending status {}", status.toString());
        Request request = new Request.Builder()
                .url(serverUrl + "/suggestion")
                .addHeader("Authorization", "Bearer " + loginResponseManager.getJwtToken())
                .addHeader("Accept", "application/x-msgpack")
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
                    handleSuggestionResponse(response, suggestionConsumer, graphDataConsumer);
                } catch (Exception e) {
                    log.warn("error reading/parsing suggestion response body", e);
                    clientThread.invoke(() -> onFailure.accept(new HttpResponseException(-1, "Unknown Error")));
                }
            }
        });
    }

    private void handleSuggestionResponse(Response response, Consumer<Suggestion> suggestionConsumer, Consumer<Data> graphDataConsumer) throws IOException {
        if (response.body() == null) {
            throw new IOException("empty suggestion request response");
        }
        String contentType = response.header("Content-Type");
        if (contentType != null && contentType.contains("application/x-msgpack")) {
            int contentLength = resolveContentLength(response);
            int suggestionContentLength = resolveSuggestionContentLength(response);
            int graphDataContentLength = contentLength - suggestionContentLength;
            log.info("msgpack suggestion response size is: {}, suggestion size is {}", contentLength, suggestionContentLength);

            Data d = new Data();
            try(InputStream is = response.body().byteStream()) {
                byte[] suggestionBytes = new byte[suggestionContentLength];
                int bytesRead = is.readNBytes(suggestionBytes, 0, suggestionContentLength);
                if (bytesRead != suggestionContentLength) {
                    throw new IOException("failed to read complete suggestion content: " + bytesRead + " of " + suggestionContentLength + " bytes");
                }
                Suggestion s = MsgpackDeserializer.deserialize(suggestionBytes, Suggestion.class);
                log.info("suggestion received");
                clientThread.invoke(() -> suggestionConsumer.accept(s));

                if (graphDataContentLength == 0) {
                    d.loadingErrorMessage = "No graph data loaded for this item.";
                } else {
                    try {
                        byte[] remainingBytes = is.readAllBytes();
                        if (graphDataContentLength != remainingBytes.length) {
                            log.error("the graph data bytes read {} doesn't match the expected bytes {}", bytesRead, graphDataContentLength);
                            d.loadingErrorMessage = "There was an issue loading the graph data for this item.";
                        } else {
                            try {
                                d = MsgpackDeserializer.deserialize(remainingBytes, Data.class);
                                log.info("graph data received");
                            } catch (Exception e) {
                                log.error("error deserializing graph data", e);
                                d.loadingErrorMessage = "There was an issue loading the graph data for this item.";
                            }
                        }
                    } catch (IOException e) {
                        log.error("error on reading graph data bytes from the suggestion response", e);
                        d.loadingErrorMessage = "There was an issue loading the graph data for this item.";
                    }
                }
            }
            Data finalD = d;
            clientThread.invoke(() -> graphDataConsumer.accept(finalD));
        } else {
            String body = response.body().string();
            log.info("json suggestion response size is: {}", body.getBytes().length);
            Suggestion s = gson.fromJson(body, Suggestion.class);
            clientThread.invoke(() -> suggestionConsumer.accept(s));
            Data d = new Data();
            d.loadingErrorMessage = "No graph data loaded for this item.";
            clientThread.invoke(() -> graphDataConsumer.accept(d));
        }
    }

    private int resolveContentLength(Response resp) throws IOException {
        try {
            String cl = resp.header("Content-Length");
            return Integer.parseInt(cl != null ? cl : "missing Content-Length header");
        } catch (NumberFormatException  e) {
            throw new IOException("Failed to parse response Content-Length", e);
        }
    }

    private int resolveSuggestionContentLength(Response resp) throws IOException {
        try {
            String cl = resp.header("X-Suggestion-Content-Length");
            return Integer.parseInt(cl != null ? cl : "missing Content-Length header");
        } catch (NumberFormatException  e) {
            throw new IOException("Failed to parse response Content-Length", e);
        }
    }

    private <T> T deserialize(Response response, Class<T> classOfT) throws IOException {
        if (response.body() == null) {
            return null;
        }
        String contentType = response.header("Content-Type");
        if (contentType != null && contentType.contains("application/x-msgpack")) {
            byte[] bytes = response.body().bytes();
            log.info("suggestion response size is: {}", bytes.length);
            return MsgpackDeserializer.deserialize(bytes, classOfT);
        } else {
            String body = response.body().string();
            log.info("suggestion response size is: {}", body.getBytes().length);
            return gson.fromJson(body, classOfT);
        }
    }

    public void sendTransactionsAsync(List<Transaction> transactions, String displayName, Consumer<List<FlipV2>> onSuccess, Consumer<HttpResponseException> onFailure) {
        log.debug("sending {} transactions for display name {}", transactions.size(), displayName);
        JsonArray body = new JsonArray();
        for (Transaction transaction : transactions) {
            body.add(transaction.toJsonObject());
        }
        String encodedDisplayName = URLEncoder.encode(displayName, StandardCharsets.UTF_8);
        Request request = new Request.Builder()
                .url(serverUrl + "/profit-tracking/client-transactions?display_name=" + encodedDisplayName)
                .addHeader("Authorization", "Bearer " + loginResponseManager.getJwtToken())
                .post(RequestBody.create(MediaType.get("application/json; charset=utf-8"), body.toString()))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("call to sync transactions failed", e);
                onFailure.accept(new HttpResponseException(-1, "Unknown Error"));
            }
            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful()) {
                        String errorMessage = extractErrorMessage(response);
                        log.warn("call to sync transactions failed status code {}, error message {}", response.code(), errorMessage);
                        onFailure.accept(new HttpResponseException(response.code(), errorMessage));
                        return;
                    }
                    String body = response.body() == null ? "" : response.body().string();
                    List<FlipV2> changedFlips = gson.fromJson(body, new TypeToken<List<FlipV2>>(){}.getType());
                    onSuccess.accept(changedFlips);
                } catch (IOException | JsonParseException e) {
                    log.warn("error reading/parsing sync transactions response body", e);
                    onFailure.accept(new HttpResponseException(-1, "Unknown Error"));
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
