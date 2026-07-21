package com.flippingcopilot.controller;

import com.flippingcopilot.model.*;
import com.flippingcopilot.rs.CopilotLoginRS;
import com.flippingcopilot.ui.graph.model.Data;
import com.flippingcopilot.util.ProtoUtils;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import okhttp3.*;

import javax.inject.Inject;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
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

    private static final String serverUrl = System.getProperty("flippingcopilot.api.host", "https://api.flippingcopilot.com");
    private static final String serverFeUrl = serverUrl.replace("api.", "");
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final MediaType PROTO_MEDIA_TYPE = MediaType.get("application/protobuf");
    private static final String GRAPH_DATA_PRICE_BITS_HEADER = "X-Graph-Data-Price-Bits";
    public static final String DEFAULT_COPILOT_PRICE_ERROR_MESSAGE = "Unable to fetch price copilot price (possible server update)";
    public static final String DEFAULT_PREMIUM_INSTANCE_ERROR_MESSAGE = "Error loading premium instance data (possible server update)";
    public static final String UNKNOWN_ERROR = "Unknown error";
    public static final int UNAUTHORIZED_CODE = 401;
    // dependencies
    private final OkHttpClient client;
    private final Gson gson;
    private final CopilotLoginRS copilotLoginRS;
    private final SuggestionPreferencesManager preferencesManager;
    private final ClientThread clientThread;

    @FunctionalInterface
    private interface CheckedResponseConsumer {
        void accept(Response response) throws Exception;
    }

    private Request.Builder authed(String jwtToken, String path) {
        return new Request.Builder()
                .url(serverUrl + path)
                .addHeader("Authorization", "Bearer " + jwtToken);
    }

    private RequestBody jsonBody(String body) {
        return RequestBody.create(JSON_MEDIA_TYPE, body);
    }

    private RequestBody protoBody(byte[] body) {
        return RequestBody.create(PROTO_MEDIA_TYPE, body);
    }

    private Call timeoutCall(Request request, int seconds) {
        return client.newBuilder()
                // Overall timeout
                .callTimeout(seconds, TimeUnit.SECONDS)
                .build()
                .newCall(request);
    }

    private void clearLoginIfUnauthorized(Response response, String jwtToken) {
        if (response.code() == UNAUTHORIZED_CODE && Objects.equals(jwtToken, copilotLoginRS.get().getJwtToken())) {
            copilotLoginRS.clear();
        }
    }

    private void enqueue(Request request,
                         String jwtToken,
                         String label,
                         Consumer<HttpResponseException> onFailure,
                         CheckedResponseConsumer onSuccess) {
        enqueue(client.newCall(request), jwtToken, label, onFailure, onSuccess);
    }

    private void enqueue(Call call,
                         String jwtToken,
                         String label,
                         Consumer<HttpResponseException> onFailure,
                         CheckedResponseConsumer onSuccess) {
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("{} failed", label, e);
                onFailure.accept(new HttpResponseException(-1, UNKNOWN_ERROR));
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful()) {
                        clearLoginIfUnauthorized(response, jwtToken);
                        String errorMessage = extractErrorMessage(response);
                        log.warn("{} failed status={} error={}", label, response.code(), errorMessage);
                        onFailure.accept(new HttpResponseException(response.code(), errorMessage));
                        return;
                    }
                    onSuccess.accept(response);
                } catch (Exception e) {
                    log.warn("error reading/parsing {} response", label, e);
                    onFailure.accept(new HttpResponseException(-1, UNKNOWN_ERROR));
                }
            }
        });
    }

    private Consumer<HttpResponseException> stringFailure(Consumer<String> onFailure) {
        return error -> onFailure.accept(error.getMessage());
    }

    private Consumer<HttpResponseException> runnableFailure(Runnable onFailure) {
        return ignored -> onFailure.run();
    }


    public void authenticate(String username, String password, Consumer<LoginResponse> successCallback, Consumer<String> failureCallback) {
        Request request = new Request.Builder()
                .url(serverUrl + "/login")
                .addHeader("Authorization", Credentials.basic(username, password))
                .post(jsonBody(""))
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
                            copilotLoginRS.clear();
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

    public Call discordLoginAsync(Consumer<String> oathUrlConsumer,
                                  Consumer<LoginResponse> loginResponseConsumer,
                                  Consumer<HttpResponseException>  onFailure) {
        log.debug("sending request to login via discord");
        Request r = new Request.Builder()
                .url(serverFeUrl + "/v1/plugin-discord-login")
                .get().build();

        Call call = client.newBuilder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .callTimeout(0, TimeUnit.MILLISECONDS)
                .build()
                .newCall(r);

        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("login via discord call failed", e);
                clientThread.invoke(() -> onFailure.accept(new HttpResponseException(-1, UNKNOWN_ERROR)));
            }
            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful()) {
                        if(response.code() == UNAUTHORIZED_CODE) {
                            copilotLoginRS.clear();
                        }
                        log.warn("login via discord call failed with http status code {}", response.code());
                        clientThread.invoke(() -> onFailure.accept(new HttpResponseException(response.code(), extractErrorMessage(response))));
                        return;
                    }
                    if (response.body() == null) {
                        throw new IOException("empty discord login response");
                    }
                    try(DataInputStream is = new DataInputStream(new BufferedInputStream(response.body().byteStream()))) {
                        PluginDiscordLoginInitResponse initResponse = PluginDiscordLoginInitResponse.fromRaw(is);
                        clientThread.invoke(() -> oathUrlConsumer.accept(initResponse.getUrl()));
                        LoginResponse loginResponse = LoginResponse.fromRaw(is);
                        if (loginResponse.getError() != null && !loginResponse.getError().isEmpty()) {
                            clientThread.invoke(() -> onFailure.accept(new HttpResponseException(-1, loginResponse.getError())));
                        } else {
                            clientThread.invoke(() -> loginResponseConsumer.accept(loginResponse));
                        }
                    }
                } catch (Exception e) {
                    log.warn("error reading/parsing discord login response body", e);
                    clientThread.invoke(() -> onFailure.accept(new HttpResponseException(-1, UNKNOWN_ERROR)));
                }
            }
        });

        return call;
    }

    public void getSuggestionAsync(byte[] status,
                                   Consumer<Suggestion> suggestionConsumer,
                                   Consumer<Data> graphDataConsumer,
                                   Consumer<HttpResponseException>  onFailure,
                                   boolean skipGraphData) {
        String jwtToken = copilotLoginRS.get().getJwtToken();
        Request.Builder rb = authed(jwtToken, "/suggestion")
                .addHeader("Accept", "application/protobuf")
                .addHeader(GRAPH_DATA_PRICE_BITS_HEADER, "64")
                .addHeader("X-VERSION", "1")
                .post(protoBody(status));

        if(skipGraphData){
            rb.addHeader("X-SKIP-GD", "true");
        }

        Request request = rb.build();

        enqueue(request, jwtToken, "get suggestion",
                error -> clientThread.invoke(() -> onFailure.accept(error)),
                response -> handleSuggestionResponse(response, suggestionConsumer, graphDataConsumer));
    }

    private void handleSuggestionResponse(Response response, Consumer<Suggestion> suggestionConsumer, Consumer<Data> graphDataConsumer) throws IOException {
        if (response.body() == null) {
            throw new IOException("empty suggestion request response");
        }
        Suggestion s;
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
            s = Suggestion.decodeProto(suggestionBytes);
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
        if (s != null && s.getType() == SuggestionType.WAIT){
            d.fromWaitSuggestion = true;
        }
        Data finalD = d;
        clientThread.invoke(() -> graphDataConsumer.accept(finalD));
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
        Integer userId = copilotLoginRS.get().getUserId();
        String jwtToken = copilotLoginRS.get().getJwtToken();
        String encodedDisplayName = URLEncoder.encode(displayName, StandardCharsets.UTF_8);
        Request request = authed(jwtToken, "/profit-tracking/client-transactions?display_name=" + encodedDisplayName)
                .post(jsonBody(body.toString()))
                .header("Accept", "application/protobuf")
                .build();

        enqueue(request, jwtToken, "sync transactions", onFailure,
                response -> onSuccess.accept(userId, FlipV2.listDecodeProto(response.body().bytes())));
    }

    public void toggleItemPortfolioAsync(ToggleItemPortfolioRequest payload,
                                         BiConsumer<Integer, ToggleItemPortfolioResult> onSuccess,
                                         Consumer<HttpResponseException> onFailure) {
        Integer userId = copilotLoginRS.get().getUserId();
        String jwtToken = copilotLoginRS.get().getJwtToken();
        Request request = authed(jwtToken, "/profit-tracking/toggle-item-portfolio")
                .addHeader("Accept", "application/protobuf")
                .post(protoBody(payload.encodeProto()))
                .build();

        enqueue(request, jwtToken, "toggle item portfolio account=" + payload.getAccountId() + " item=" + payload.getItemId(), onFailure,
                response -> onSuccess.accept(userId, ToggleItemPortfolioResult.decodeProto(response.body().bytes())));
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
        String jwtToken = copilotLoginRS.get().getJwtToken();
        Request request = authed(jwtToken, "/profit-tracking/visualize-flip")
                .addHeader("Accept", "application/x-msgpack")
                .addHeader(GRAPH_DATA_PRICE_BITS_HEADER, "64")
                .addHeader("X-VERSION", "1")
                .post(jsonBody(body.toString()))
                .build();

        enqueue(timeoutCall(request, 30), jwtToken, "visualize flip " + flipID, stringFailure(onFailure), response -> {
            VisualizeFlipResponse rsp = VisualizeFlipResponse.fromMsgPack(ByteBuffer.wrap(response.body().bytes()));
            log.debug("visualize data received for flip {}", flipID);
            onSuccess.accept(rsp);
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
        String jwtToken = copilotLoginRS.get().getJwtToken();
        Request request = authed(jwtToken, "/prices")
                .addHeader("Accept", "application/x-msgpack")
                .addHeader(GRAPH_DATA_PRICE_BITS_HEADER, "64")
                .addHeader("X-VERSION", "1")
                .post(jsonBody(body.toString()))
                .build();

        Consumer<HttpResponseException> emitError = error -> {
            ItemPrice ip = new ItemPrice(0, 0, DEFAULT_COPILOT_PRICE_ERROR_MESSAGE, null);
            clientThread.invoke(() -> consumer.accept(ip));
        };
        enqueue(timeoutCall(request, 30), jwtToken, "copilot price item=" + itemId, emitError, response -> {
            ItemPrice ip = ItemPrice.fromMsgPack(ByteBuffer.wrap(response.body().bytes()));
            log.debug("price graph data received for item {}", itemId);
            clientThread.invoke(() -> consumer.accept(ip));
        });
    }


    public void asyncUpdatePremiumInstances(Consumer<PremiumInstanceStatus> consumer, List<String> displayNames) {
        JsonObject payload = new JsonObject();
        JsonArray arr = new JsonArray();
        displayNames.forEach(arr::add);
        payload.add("premium_display_names", arr);
        String jwtToken = copilotLoginRS.get().getJwtToken();

        Request request = new Request.Builder()
                .url(serverUrl +"/premium-instances/update-assignments")
                .addHeader("Authorization", "Bearer " + jwtToken)
                .post(jsonBody(payload.toString()))
                .build();

        enqueuePremiumStatusRequest(request, jwtToken, "update premium instances", consumer);
    }

    public void asyncGetPremiumInstanceStatus(Consumer<PremiumInstanceStatus> consumer) {
        String jwtToken = copilotLoginRS.get().getJwtToken();
        Request request = new Request.Builder()
                .url(serverUrl +"/premium-instances/status")
                .addHeader("Authorization", "Bearer " + jwtToken)
                .get()
                .build();

        enqueuePremiumStatusRequest(request, jwtToken, "get premium instance status", consumer);
    }

    private void enqueuePremiumStatusRequest(Request request,
                                             String jwtToken,
                                             String label,
                                             Consumer<PremiumInstanceStatus> consumer) {
        enqueue(request, jwtToken, label,
                error -> emitPremiumInstanceError(consumer),
                response -> {
                    PremiumInstanceStatus ip = gson.fromJson(response.body().string(), PremiumInstanceStatus.class);
                    clientThread.invoke(() -> consumer.accept(ip));
                });
    }

    private void emitPremiumInstanceError(Consumer<PremiumInstanceStatus> consumer) {
        clientThread.invoke(() -> consumer.accept(PremiumInstanceStatus.ErrorInstance(DEFAULT_PREMIUM_INSTANCE_ERROR_MESSAGE)));
    }

    public void asyncDeleteFlip(FlipV2 flip, Consumer<FlipV2> onSuccess, Runnable onFailure) {
        JsonObject body = new JsonObject();
        body.addProperty("flip_id", flip.getId().toString());
        String jwtToken = copilotLoginRS.get().getJwtToken();

        Request request = authed(jwtToken, "/profit-tracking/delete-flip")
                .header("Accept", "application/protobuf")
                .post(jsonBody(body.toString()))
                .build();

        enqueue(request, jwtToken, "delete flip " + flip.getId(), runnableFailure(onFailure),
                response -> onSuccess.accept(FlipV2.decodeProto(response.body().bytes())));
    }

    public void asyncAddMissedSale(UUID flipId, long price, int quantity,
                                   BiConsumer<Integer, List<FlipV2>> onSuccess,
                                   Consumer<HttpResponseException> onFailure) {
        byte[] body = ProtoUtils.encodeMessage(out -> {
            out.writeByteArray(1, ProtoUtils.uuidToBytes(flipId));
            out.writeInt32(3, quantity);
            out.writeInt64(4, price);
        });
        postProtoExpectingFlips("/profit-tracking/add-missed-sale", body,
                "add missed sale flip=" + flipId, onSuccess, onFailure);
    }

    public void asyncReviveGhostFlip(UUID flipId,
                                     BiConsumer<Integer, List<FlipV2>> onSuccess,
                                     Consumer<HttpResponseException> onFailure) {
        byte[] body = ProtoUtils.encodeMessage(out -> {
            out.writeByteArray(1, ProtoUtils.uuidToBytes(flipId));
        });
        postProtoExpectingFlips("/profit-tracking/revive-ghost-flip", body,
                "revive ghost flip=" + flipId, onSuccess, onFailure);
    }

    private void postProtoExpectingFlips(String path, byte[] body, String logLabel,
                                         BiConsumer<Integer, List<FlipV2>> onSuccess,
                                         Consumer<HttpResponseException> onFailure) {
        Integer userId = copilotLoginRS.get().getUserId();
        String jwtToken = copilotLoginRS.get().getJwtToken();
        Request request = authed(jwtToken, path)
                .addHeader("Accept", "application/protobuf")
                .post(protoBody(body))
                .build();

        enqueue(request, jwtToken, logLabel, onFailure,
                response -> onSuccess.accept(userId, FlipV2.listDecodeProto(response.body().bytes())));
    }

    public void asyncClearAccountPortfolio(int accountId,
                                            BiConsumer<Integer, ToggleItemPortfolioResult> onSuccess,
                                            Consumer<HttpResponseException> onFailure) {
        JsonObject body = new JsonObject();
        body.addProperty("account_id", accountId);
        Integer userId = copilotLoginRS.get().getUserId();
        String jwtToken = copilotLoginRS.get().getJwtToken();

        Request request = authed(jwtToken, "/profit-tracking/clear-account-portfolio")
                .addHeader("Accept", "application/protobuf")
                .post(jsonBody(body.toString()))
                .build();

        enqueue(request, jwtToken, "clear account portfolio account=" + accountId, onFailure,
                response -> onSuccess.accept(userId, ToggleItemPortfolioResult.decodeProto(response.body().bytes())));
    }

    public void asyncDeleteAccount(int accountId, Runnable onSuccess, Runnable onFailure) {
        JsonObject body = new JsonObject();
        body.addProperty("account_id", accountId);
        String jwtToken = copilotLoginRS.get().getJwtToken();

        Request request = authed(jwtToken, "/profit-tracking/delete-account")
                .header("Accept", "application/x-bytes")
                .post(jsonBody(body.toString()))
                .build();

        enqueue(request, jwtToken, "delete account " + accountId, runnableFailure(onFailure),
                response -> onSuccess.run());
    }

    public void asyncLoadAccounts(Consumer<Map<String, Integer>> onSuccess, Consumer<String> onFailure) {
        String jwtToken = copilotLoginRS.get().getJwtToken();
        Request request = authed(jwtToken, "/profit-tracking/rs-account-names")
                .method("GET", null)
                .build();

        enqueue(request, jwtToken, "load user display names", stringFailure(onFailure), response -> {
            String responseBody = response.body() != null ? response.body().string() : "{}";
            Type respType = new TypeToken<Map<String, Integer>>(){}.getType();
            Map<String, Integer> names = gson.fromJson(responseBody, respType);
            onSuccess.accept(names != null ? names : new HashMap<>());
        });
    }

    public void asyncLoadFlips(Map<Integer, Integer> accountIdTime, BiConsumer<Integer, FlipsDeltaResult> onSuccess, Consumer<String> onFailure) {
        Integer userId = copilotLoginRS.get().getUserId();
        String jwtToken = copilotLoginRS.get().getJwtToken();
        DataDeltaRequest body = new DataDeltaRequest(accountIdTime);
        String bodyStr = gson.toJson(body);

        Request request = authed(jwtToken, "/profit-tracking/client-flips-delta")
                .header("Accept", "application/protobuf")
                .method("POST", jsonBody(bodyStr))
                .build();

        enqueue(request, jwtToken, "load flips", stringFailure(onFailure),
                response -> onSuccess.accept(userId, FlipsDeltaResult.decodeProto(response.body().bytes())));
    }

    public void asyncLoadTransactionsData(String displayName, Consumer<byte[]> onSuccess, Consumer<String> onFailure) {
        String encodedDisplayName = URLEncoder.encode(displayName, StandardCharsets.UTF_8);
        String jwtToken = copilotLoginRS.get().getJwtToken();
        AccountClientTransactionsRequest body = new AccountClientTransactionsRequest(0, 0);

        Request request = authed(jwtToken, "/profit-tracking/account-client-transactions?display_name=" + encodedDisplayName)
                .header("Accept", "application/protobuf")
                .post(protoBody(body.encodeProto()))
                .build();

        enqueue(request, jwtToken, "load transactions", stringFailure(onFailure), response -> {
            onSuccess.accept(AckedTransaction.listDecodeProto(response.body().bytes()));
        });
    }

    public Call asyncConsumeDumpAlerts(String displayName, Consumer<Response> onSuccess, Consumer<HttpResponseException> onFailure) {
        String encodedDisplayName = URLEncoder.encode(displayName, StandardCharsets.UTF_8);
        String jwtToken = copilotLoginRS.get().getJwtToken();
        Request request = authed(jwtToken, "/dump-alerts?display_name=" + encodedDisplayName)
                .post(jsonBody(""))
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
                    if(response.code() == UNAUTHORIZED_CODE && Objects.equals(jwtToken, copilotLoginRS.get().getJwtToken())) {
                        copilotLoginRS.clear();
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
        asyncModifyTransaction("/profit-tracking/orphan-transaction", "orphaning transaction", transaction, onSuccess, onFailure);
    }

    public void asyncDeleteTransaction(AckedTransaction transaction, BiConsumer<Integer, List<FlipV2>> onSuccess, Runnable onFailure) {
        asyncModifyTransaction("/profit-tracking/delete-transaction", "delete transaction", transaction, onSuccess, onFailure);
    }

    private void asyncModifyTransaction(String path,
                                        String label,
                                        AckedTransaction transaction,
                                        BiConsumer<Integer, List<FlipV2>> onSuccess,
                                        Runnable onFailure) {
        JsonObject body = new JsonObject();
        body.addProperty("transaction_id", transaction.getId().toString());
        body.addProperty("account_id", transaction.getAccountId());
        Integer userId = copilotLoginRS.get().getUserId();
        String jwtToken = copilotLoginRS.get().getJwtToken();
        Request request = authed(jwtToken, path)
                .header("Accept", "application/protobuf")
                .post(jsonBody(body.toString()))
                .build();

        enqueue(request, jwtToken, label + " " + transaction.getId(), runnableFailure(onFailure),
                response -> onSuccess.accept(userId, FlipV2.listDecodeProto(response.body().bytes())));
    }

}
