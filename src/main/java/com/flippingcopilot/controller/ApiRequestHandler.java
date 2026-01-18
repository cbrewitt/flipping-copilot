package com.flippingcopilot.controller;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.manager.CopilotLoginManager;
import com.flippingcopilot.model.*;
import com.flippingcopilot.ui.graph.model.Data;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import okhttp3.*;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;


@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ApiRequestHandler {

    private static final String serverUrl = System.getenv("FLIPPING_COPILOT_HOST") != null ? System.getenv("FLIPPING_COPILOT_HOST")  : "https://api.flippingcopilot.com";
    public static final String DEFAULT_COPILOT_PRICE_ERROR_MESSAGE = "Unable to fetch price copilot price (possible server update)";
    public static final String DEFAULT_PREMIUM_INSTANCE_ERROR_MESSAGE = "Error loading premium instance data (possible server update)";
    public static final String UNKNOWN_ERROR = "Unknown error";
    public static final int UNAUTHORIZED_CODE = 401;
    // dependencies
    private final OkHttpClient client;
    private final Gson gson;
    private final CopilotLoginManager copilotLoginManager;
    private final FlippingCopilotConfig config;

    @Setter
    private CopilotLoginController copilotLoginController;
    private final SuggestionPreferencesManager preferencesManager;
    private final ClientThread clientThread;


    public void authenticate(String username, String password, Consumer<LoginResponse> successCallback, Consumer<String> failureCallback) {
        Request request = new Request.Builder()
                .url(serverUrl + "/login")
                .addHeader("Authorization", Credentials.basic(username, password))
                .post(RequestBody.create(MediaType.get("application/json; charset=utf-8"), ""))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                failureCallback.accept(UNKNOWN_ERROR);
            }
            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful()) {
                        if(response.code() == UNAUTHORIZED_CODE) {
                            copilotLoginController.onLogout();
                        }
                        log.warn("login failed with http status code {}", response.code());
                        String errorMessage = extractErrorMessage(response);
                        failureCallback.accept(errorMessage);
                        return;
                    }
                    String body = response.body() == null ? "" : response.body().string();
                    LoginResponse loginResponse = gson.fromJson(body, LoginResponse.class);
                    successCallback.accept(loginResponse);
                } catch (IOException | JsonParseException e) {
                    log.warn("error reading/decoding login response body", e);
                    failureCallback.accept(UNKNOWN_ERROR);
                }
            }
        });
    }

    public void getSuggestionAsync(JsonObject status,
                                   Consumer<Suggestion> suggestionConsumer,
                                   Consumer<Data> graphDataConsumer,
                                   Consumer<HttpResponseException>  onFailure) {
        log.debug("sending status {}", status.toString());
        Request.Builder rb = new Request.Builder()
                .url(serverUrl + "/suggestion")
                .addHeader("Authorization", "Bearer " + copilotLoginManager.getJwtToken())
                .addHeader("Accept", "application/x-msgpack")
                .addHeader("X-VERSION", "1")
                .post(RequestBody.create(MediaType.get("application/json; charset=utf-8"), status.toString()));

        if(config.lowDataMode()){
            rb.addHeader("X-SKIP-GD", "true");
        }

        Request request = rb.build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("call to get suggestion failed", e);
                clientThread.invoke(() -> onFailure.accept(new HttpResponseException(-1, UNKNOWN_ERROR)));
            }
            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful()) {
                        if(response.code() == UNAUTHORIZED_CODE) {
                            copilotLoginController.onLogout();
                        }
                        log.warn("get suggestion failed with http status code {}", response.code());
                        clientThread.invoke(() -> onFailure.accept(new HttpResponseException(response.code(), extractErrorMessage(response))));
                        return;
                    }
                    handleSuggestionResponse(response, suggestionConsumer, graphDataConsumer);
                } catch (Exception e) {
                    log.warn("error reading/parsing suggestion response body", e);
                    clientThread.invoke(() -> onFailure.accept(new HttpResponseException(-1, UNKNOWN_ERROR)));
                }
            }
        });
    }

    private void handleSuggestionResponse(Response response, Consumer<Suggestion> suggestionConsumer, Consumer<Data> graphDataConsumer) throws IOException {
        if (response.body() == null) {
            throw new IOException("empty suggestion request response");
        }
        String contentType = response.header("Content-Type");
        Suggestion s;
        if (contentType != null && contentType.contains("application/x-msgpack")) {
            int contentLength = resolveContentLength(response);
            int suggestionContentLength = resolveSuggestionContentLength(response);
            int graphDataContentLength = contentLength - suggestionContentLength;
            log.debug("msgpack suggestion response size is: {}, suggestion size is {}", contentLength, suggestionContentLength);

            Data d = new Data();
            try(InputStream is = response.body().byteStream()) {
                // This is some bespoke handling to make the user experience better. We basically pack two different
                // objects in the response body. The suggestion (first object) and the graph data (second
                // object). The graph data can be a few kb, and we want the suggestion to be displayed
                // immediately, without having to wait for the graph data to be loaded.

                byte[] suggestionBytes = new byte[suggestionContentLength];
                int bytesRead = is.readNBytes(suggestionBytes, 0, suggestionContentLength);
                if (bytesRead != suggestionContentLength) {
                    throw new IOException("failed to read complete suggestion content: " + bytesRead + " of " + suggestionContentLength + " bytes");
                }
                s = Suggestion.fromMsgPack(ByteBuffer.wrap(suggestionBytes));
                log.debug("suggestion received");
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
                                d = Data.fromMsgPack(ByteBuffer.wrap(remainingBytes));
                                log.debug("graph data received");
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
            if (s != null && "wait".equals(s.getType())){
                d.fromWaitSuggestion = true;
            }
            Data finalD = d;
            clientThread.invoke(() -> graphDataConsumer.accept(finalD));
        } else {
            String body = response.body().string();
            log.debug("json suggestion response size is: {}", body.getBytes().length);
            s = gson.fromJson(body, Suggestion.class);
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

    public void sendTransactionsAsync(List<Transaction> transactions, String displayName, BiConsumer<Integer, List<FlipV2>> onSuccess, Consumer<HttpResponseException> onFailure) {
        log.debug("sending {} transactions for display name {}", transactions.size(), displayName);
        JsonArray body = new JsonArray();
        for (Transaction transaction : transactions) {
            body.add(transaction.toJsonObject());
        }
        Integer userId = copilotLoginManager.getCopilotUserId();
        String encodedDisplayName = URLEncoder.encode(displayName, StandardCharsets.UTF_8);
        Request request = new Request.Builder()
                .url(serverUrl + "/profit-tracking/client-transactions?display_name=" + encodedDisplayName)
                .addHeader("Authorization", "Bearer " + copilotLoginManager.getJwtToken())
                .post(RequestBody.create(MediaType.get("application/json; charset=utf-8"), body.toString()))
                .header("Accept", "application/x-bytes")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("call to sync transactions failed", e);
                onFailure.accept(new HttpResponseException(-1, UNKNOWN_ERROR));
            }
            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful()) {
                        if(response.code() == UNAUTHORIZED_CODE) {
                            copilotLoginController.onLogout();
                        }
                        String errorMessage = extractErrorMessage(response);
                        log.warn("call to sync transactions failed status code {}, error message {}", response.code(), errorMessage);
                        onFailure.accept(new HttpResponseException(response.code(), errorMessage));
                        return;
                    }
                    List<FlipV2> changedFlips = FlipV2.listFromRaw(response.body().bytes());
                    onSuccess.accept(userId, changedFlips);
                } catch (Exception e) {
                    log.warn("error reading/parsing sync transactions response body", e);
                    onFailure.accept(new HttpResponseException(-1, UNKNOWN_ERROR));
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
            } catch (Exception e) {
                log.warn("failed reading/parsing error message from http {} response body", response.code(), e);
            }
        }
        return UNKNOWN_ERROR;
    }


    public void asyncGetVisualizeFlipData(UUID flipID, String displayName, Consumer<VisualizeFlipResponse> onSuccess, Consumer<String> onFailure) {
        JsonObject body = new JsonObject();
        body.add("flip_id", new JsonPrimitive(flipID.toString()));
        body.add("display_name", new JsonPrimitive(displayName));
        log.debug("requesting visualize data for flip {}", flipID);
        Request request = new Request.Builder()
                .url(serverUrl +"/profit-tracking/visualize-flip")
                .addHeader("Authorization", "Bearer " + copilotLoginManager.getJwtToken())
                .addHeader("Accept", "application/x-msgpack")
                .addHeader("X-VERSION", "1")
                .post(RequestBody.create(MediaType.get("application/json; charset=utf-8"), body.toString()))
                .build();

        client.newBuilder()
                .callTimeout(30, TimeUnit.SECONDS) // Overall timeout
                .build()
                .newCall(request)
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        onFailure.accept(e.toString());
                    }
                    @Override
                    public void onResponse(Call call, Response response) {
                        try {
                            if (!response.isSuccessful()) {
                                if(response.code() == UNAUTHORIZED_CODE) {
                                    copilotLoginController.onLogout();
                                }
                                log.error("get visualize data for flip {} failed with http status code {}", flipID, response.code());
                                onFailure.accept(UNKNOWN_ERROR);
                            } else {
                                byte[] d = response.body().bytes();
                                VisualizeFlipResponse rsp = VisualizeFlipResponse.fromMsgPack(ByteBuffer.wrap(d));
                                log.debug("visualize data received for flip {}", flipID);
                                onSuccess.accept(rsp);
                            }
                        } catch (Exception e) {
                            log.error("error visualize data received for flip {}", flipID, e);
                            onFailure.accept(UNKNOWN_ERROR);
                        }
                    }
                });
    }

    public void asyncGetItemPriceWithGraphData(int itemId, String displayName, Consumer<ItemPrice> consumer, boolean includeGraphData) {
        JsonObject body = new JsonObject();
        body.add("item_id", new JsonPrimitive(itemId));
        body.add("display_name", new JsonPrimitive(displayName));
        body.addProperty("f2p_only", preferencesManager.isF2pOnlyMode());
        body.addProperty("timeframe_minutes", preferencesManager.getTimeframe());
        body.addProperty("risk_level", preferencesManager.getRiskLevel().toApiValue());
        body.addProperty("include_graph_data", includeGraphData);
        log.debug("requesting price graph data for item {}", itemId);
        Request request = new Request.Builder()
                .url(serverUrl +"/prices")
                .addHeader("Authorization", "Bearer " + copilotLoginManager.getJwtToken())
                .addHeader("Accept", "application/x-msgpack")
                .addHeader("X-VERSION", "1")
                .post(RequestBody.create(MediaType.get("application/json; charset=utf-8"), body.toString()))
                .build();

        client.newBuilder()
                .callTimeout(30, TimeUnit.SECONDS) // Overall timeout
                .build()
                .newCall(request)
                .enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.error("error fetching copilot price for item {}", itemId, e);
                ItemPrice ip = new ItemPrice(0, 0, DEFAULT_COPILOT_PRICE_ERROR_MESSAGE, null);
                clientThread.invoke(() -> consumer.accept(ip));
            }
            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful()) {
                        if(response.code() == UNAUTHORIZED_CODE) {
                            copilotLoginController.onLogout();
                        }
                        log.error("get copilot price for item {} failed with http status code {}", itemId, response.code());
                        ItemPrice ip = new ItemPrice(0, 0, DEFAULT_COPILOT_PRICE_ERROR_MESSAGE, null);
                        clientThread.invoke(() -> consumer.accept(ip));
                    } else {
                        byte[] d = response.body().bytes();
                        ItemPrice ip = ItemPrice.fromMsgPack(ByteBuffer.wrap(d));
                        log.debug("price graph data received for item {}", itemId);
                        clientThread.invoke(() -> consumer.accept(ip));
                    }
                } catch (Exception e) {
                    log.error("error fetching copilot price for item {}", itemId, e);
                    ItemPrice ip = new ItemPrice(0, 0, DEFAULT_COPILOT_PRICE_ERROR_MESSAGE, null);
                    clientThread.invoke(() -> consumer.accept(ip));
                }
            }
        });
    }


    public void asyncUpdatePremiumInstances(Consumer<PremiumInstanceStatus> consumer, List<String> displayNames) {
        JsonObject payload = new JsonObject();
        JsonArray arr = new JsonArray();
        displayNames.forEach(arr::add);
        payload.add("premium_display_names", arr);

        Request request = new Request.Builder()
                .url(serverUrl +"/premium-instances/update-assignments")
                .addHeader("Authorization", "Bearer " + copilotLoginManager.getJwtToken())
                .post(RequestBody.create(MediaType.get("application/json; charset=utf-8"), payload.toString()))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.error("error updating premium instance assignments", e);
                clientThread.invoke(() -> consumer.accept(PremiumInstanceStatus.ErrorInstance(DEFAULT_PREMIUM_INSTANCE_ERROR_MESSAGE)));
            }
            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful()) {
                        if(response.code() == UNAUTHORIZED_CODE) {
                            copilotLoginController.onLogout();
                        }
                        log.error("update premium instances failed with http status code {}", response.code());
                        clientThread.invoke(() -> consumer.accept(PremiumInstanceStatus.ErrorInstance(DEFAULT_PREMIUM_INSTANCE_ERROR_MESSAGE)));
                    } else {
                        PremiumInstanceStatus ip = gson.fromJson(response.body().string(), PremiumInstanceStatus.class);
                        clientThread.invoke(() -> consumer.accept(ip));
                    }
                } catch (Exception e) {
                    log.error("error updating premium instance assignments", e);
                    clientThread.invoke(() -> consumer.accept(PremiumInstanceStatus.ErrorInstance(DEFAULT_PREMIUM_INSTANCE_ERROR_MESSAGE)));
                }
            }
        });
    }

    public void asyncGetPremiumInstanceStatus(Consumer<PremiumInstanceStatus> consumer) {
        Request request = new Request.Builder()
                .url(serverUrl +"/premium-instances/status")
                .addHeader("Authorization", "Bearer " + copilotLoginManager.getJwtToken())
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.error("error fetching premium instance status", e);
                clientThread.invoke(() -> consumer.accept(PremiumInstanceStatus.ErrorInstance(DEFAULT_PREMIUM_INSTANCE_ERROR_MESSAGE)));
            }
            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful()) {
                        if(response.code() == UNAUTHORIZED_CODE) {
                            copilotLoginController.onLogout();
                        }
                        log.error("get premium instance status failed with http status code {}", response.code());
                        clientThread.invoke(() -> consumer.accept(PremiumInstanceStatus.ErrorInstance(DEFAULT_PREMIUM_INSTANCE_ERROR_MESSAGE)));
                    } else {
                        PremiumInstanceStatus ip = gson.fromJson(response.body().string(), PremiumInstanceStatus.class);
                        clientThread.invoke(() -> consumer.accept(ip));
                    }
                } catch (Exception e) {
                    log.error("error fetching premium instance status", e);
                    clientThread.invoke(() -> consumer.accept(PremiumInstanceStatus.ErrorInstance(DEFAULT_PREMIUM_INSTANCE_ERROR_MESSAGE)));
                }
            }
        });

    }

    public void asyncDeleteFlip(FlipV2 flip, Consumer<FlipV2> onSuccess, Runnable onFailure) {
        JsonObject body = new JsonObject();
        body.addProperty("flip_id", flip.getId().toString());

        Request request = new Request.Builder()
                .url(serverUrl + "/profit-tracking/delete-flip")
                .addHeader("Authorization", "Bearer " + copilotLoginManager.getJwtToken())
                .header("Accept", "application/x-bytes")
                .post(RequestBody.create(MediaType.get("application/json; charset=utf-8"), body.toString()))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.error("deleting flip {}", flip.getId(), e);
                onFailure.run();
            }
            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful()) {
                        if(response.code() == UNAUTHORIZED_CODE) {
                            copilotLoginController.onLogout();
                        }
                        log.error("deleting flip {}, bad response code {}", flip.getId(), response.code());
                        onFailure.run();
                    } else {
                        FlipV2 flip = FlipV2.fromRaw(response.body().bytes());
                        onSuccess.accept(flip);
                    }
                } catch (Exception e) {
                    log.error("deleting flip {}", flip.getId(), e);
                    onFailure.run();
               }
            }
        });
    }

    public void asyncDeleteAccount(int accountId, Runnable onSuccess, Runnable onFailure) {
        JsonObject body = new JsonObject();
        body.addProperty("account_id", accountId);

        Request request = new Request.Builder()
                .url(serverUrl + "/profit-tracking/delete-account")
                .addHeader("Authorization", "Bearer " + copilotLoginManager.getJwtToken())
                .header("Accept", "application/x-bytes")
                .post(RequestBody.create(MediaType.get("application/json; charset=utf-8"), body.toString()))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.error("deleting account {}", accountId, e);
                onFailure.run();
            }
            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful()) {
                        if(response.code() == UNAUTHORIZED_CODE) {
                            copilotLoginController.onLogout();
                        }
                        log.error("deleting account {}, bad response code {}", accountId, response.code());
                        onFailure.run();
                    }
                    onSuccess.run();
                } catch (Exception e) {
                    log.error("deleting account {}", accountId, e);
                    onFailure.run();
                }
            }
        });
    }

    public void asyncLoadAccounts(Consumer<Map<String, Integer>> onSuccess, Consumer<String> onFailure) {
        Request request = new Request.Builder()
                .url(serverUrl + "/profit-tracking/rs-account-names")
                .addHeader("Authorization", "Bearer " + copilotLoginManager.getJwtToken())
                .method("GET", null)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.error("error loading user display names", e);
                onFailure.accept(UNKNOWN_ERROR);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful()) {
                        if(response.code() == UNAUTHORIZED_CODE) {
                            copilotLoginController.onLogout();
                        }
                        String errorMessage = extractErrorMessage(response);
                        log.error("load user display names failed with http status code {}, error message {}", response.code(), errorMessage);
                        onFailure.accept(errorMessage);
                        return;
                    }
                    String responseBody = response.body() != null ? response.body().string() : "{}";
                    Type respType = new TypeToken<Map<String, Integer>>(){}.getType();
                    Map<String, Integer> names = gson.fromJson(responseBody, respType);
                    Map<String, Integer> result = names != null ? names : new HashMap<>();
                    onSuccess.accept(result);
                } catch (Exception e) {
                    log.error("error reading/parsing user display names response body", e);
                    onFailure.accept(UNKNOWN_ERROR);
                }
            }
        });
    }

    public void asyncLoadFlips(Map<Integer, Integer> accountIdTime, BiConsumer<Integer, FlipsDeltaResult> onSuccess, Consumer<String> onFailure) {
        Integer userId = copilotLoginManager.getCopilotUserId();
        DataDeltaRequest body = new DataDeltaRequest(accountIdTime);
        String bodyStr = gson.toJson(body);

        Request request = new Request.Builder()
                .url(serverUrl + "/profit-tracking/client-flips-delta")
                .addHeader("Authorization", "Bearer " + copilotLoginManager.getJwtToken())
                .header("Accept", "application/x-bytes")
                .method("POST", RequestBody.create(MediaType.get("application/json; charset=utf-8"), bodyStr))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.error("error loading flips", e);
                onFailure.accept(UNKNOWN_ERROR);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful()) {
                        if(response.code() == UNAUTHORIZED_CODE) {
                            copilotLoginController.onLogout();
                        }
                        String errorMessage = extractErrorMessage(response);
                        log.error("load flips failed with http status code {}, error message {}", response.code(), errorMessage);
                        onFailure.accept(errorMessage);
                        return;
                    }
                    FlipsDeltaResult res = FlipsDeltaResult.fromRaw(response.body().bytes());
                    onSuccess.accept(userId, res);
                } catch (Exception e) {
                    log.error("error reading/parsing flips response body", e);
                    onFailure.accept(UNKNOWN_ERROR);
                }
            }
        });
    }

    public void asyncLoadTransactionsData(Consumer<byte[]> onSuccess, Consumer<String> onFailure) {

        Request request = new Request.Builder()
                .url(serverUrl + "/profit-tracking/client-transactions")
                .addHeader("Authorization", "Bearer " + copilotLoginManager.getJwtToken())
                .header("Accept", "application/x-bytes")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.error("error loading transactions", e);
                onFailure.accept(UNKNOWN_ERROR);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful()) {
                        if(response.code() == UNAUTHORIZED_CODE) {
                            copilotLoginController.onLogout();
                        }
                        String errorMessage = extractErrorMessage(response);
                        log.error("load transactions failed with http status code {}, error message {}", response.code(), errorMessage);
                        onFailure.accept(errorMessage);
                        return;
                    }
                    byte[] data = response.body().bytes();
                    onSuccess.accept(Arrays.copyOfRange(data, 4, data.length-4));
                } catch (Exception e) {
                    log.error("error reading/parsing transactions response body", e);
                    onFailure.accept(UNKNOWN_ERROR);
                }
            }
        });
    }

    public Call asyncConsumeDumpAlerts(String displayName, Consumer<Response> onSuccess, Consumer<HttpResponseException> onFailure) {
        String encodedDisplayName = URLEncoder.encode(displayName, StandardCharsets.UTF_8);
        Request request = new Request.Builder()
                .url(serverUrl + "/dump-alerts?display_name=" + encodedDisplayName)
                .addHeader("Authorization", "Bearer " + copilotLoginManager.getJwtToken())
                .post(RequestBody.create(MediaType.get("application/json; charset=utf-8"), ""))
                .build();

        Call call = client.newBuilder()
                .readTimeout(10, TimeUnit.SECONDS)
                .callTimeout(0, TimeUnit.MILLISECONDS)
                .build()
                .newCall(request);

        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("error consuming dump alerts", e);
                onFailure.accept(new HttpResponseException(-1, UNKNOWN_ERROR));
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (!response.isSuccessful()) {
                    if(response.code() == UNAUTHORIZED_CODE) {
                        copilotLoginController.onLogout();
                    }
                    String errorMessage = extractErrorMessage(response);
                    response.close();
                    onFailure.accept(new HttpResponseException(response.code(), errorMessage));
                    return;
                }
                if (response.body() == null) {
                    response.close();
                    onFailure.accept(new HttpResponseException(-1, UNKNOWN_ERROR));
                    return;
                }
                onSuccess.accept(response);
            }
        });

        return call;
    }


    public void asyncOrphanTransaction(AckedTransaction transaction, BiConsumer<Integer, List<FlipV2>> onSuccess, Runnable onFailure) {
        JsonObject body = new JsonObject();
        body.addProperty("transaction_id", transaction.getId().toString());
        body.addProperty("account_id", transaction.getAccountId());
        Integer userId = copilotLoginManager.getCopilotUserId();
        Request request = new Request.Builder()
                .url(serverUrl + "/profit-tracking/orphan-transaction")
                .addHeader("Authorization", "Bearer " + copilotLoginManager.getJwtToken())
                .header("Accept", "application/x-bytes")
                .post(RequestBody.create(MediaType.get("application/json; charset=utf-8"), body.toString()))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.error("orphaning transaction {}", transaction.getId(), e);
                onFailure.run();
            }
            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful()) {
                        if(response.code() == UNAUTHORIZED_CODE) {
                            copilotLoginController.onLogout();
                        }
                        log.error("orphaning transaction {}, bad response code {}", transaction.getId(), response.code());
                        onFailure.run();
                    } else {
                        List<FlipV2> flips = FlipV2.listFromRaw(response.body().bytes());
                        onSuccess.accept(userId, flips);
                    }
                } catch (Exception e) {
                    log.error("orphaning transaction {}", transaction.getId(), e);
                    onFailure.run();
                }
            }
        });
    }

    public void asyncDeleteTransaction(AckedTransaction transaction, BiConsumer<Integer, List<FlipV2>> onSuccess, Runnable onFailure) {
        JsonObject body = new JsonObject();
        body.addProperty("transaction_id", transaction.getId().toString());
        body.addProperty("account_id", transaction.getAccountId());
        Integer userId = copilotLoginManager.getCopilotUserId();
        Request request = new Request.Builder()
                .url(serverUrl + "/profit-tracking/delete-transaction")
                .addHeader("Authorization", "Bearer " + copilotLoginManager.getJwtToken())
                .header("Accept", "application/x-bytes")
                .post(RequestBody.create(MediaType.get("application/json; charset=utf-8"), body.toString()))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.error("delete transaction {}", transaction.getId(), e);
                onFailure.run();
            }
            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful()) {
                        if(response.code() == UNAUTHORIZED_CODE) {
                            copilotLoginController.onLogout();
                        }
                        log.error("delete transaction {}, bad response code {}", transaction.getId(), response.code());
                        onFailure.run();
                    } else {
                        List<FlipV2> flips = FlipV2.listFromRaw(response.body().bytes());
                        onSuccess.accept(userId, flips);
                    }
                } catch (Exception e) {
                    log.error("delete transaction {}", transaction.getId(), e);
                    onFailure.run();
                }
            }
        });
    }

    public void asyncLoadRecentAccountTransactions(String displayName, int endTime, Consumer<List<AckedTransaction>> onSuccess, Consumer<String> onFailure) {
        JsonObject body = new JsonObject();
        body.addProperty("limit", 30);
        body.addProperty("end", endTime);
        Request request = new Request.Builder()
                .url(serverUrl + "/profit-tracking/account-client-transactions?display_name=" + displayName)
                .addHeader("Authorization", "Bearer " + copilotLoginManager.getJwtToken())
                .header("Accept", "application/x-bytes")
                .post(RequestBody.create(MediaType.get("application/json; charset=utf-8"), body.toString()))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.error("error loading transactions", e);
                onFailure.accept(UNKNOWN_ERROR);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful()) {
                        if(response.code() == UNAUTHORIZED_CODE) {
                            copilotLoginController.onLogout();
                        }
                        String errorMessage = extractErrorMessage(response);
                        log.error("load transactions failed with http status code {}, error message {}", response.code(), errorMessage);
                        onFailure.accept(errorMessage);
                        return;
                    }
                    onSuccess.accept(AckedTransaction.listFromRaw(response.body().bytes()));
                } catch (Exception e) {
                    log.error("error reading/parsing transactions response body", e);
                    onFailure.accept(UNKNOWN_ERROR);
                }
            }
        });
    }
}
