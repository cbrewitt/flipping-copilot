package copilot.controller;
import static copilot.util.ProtoUtils.*;

import javax.inject.Inject;
import com.google.inject.Singleton;
import copilot.ui.graph.model.Data;
import java.util.function.*;
import com.google.protobuf.*;
import copilot.model.*;
import copilot.rs.*;
import copilot.ui.graph.model.*;
import copilot.util.*;
import com.google.gson.*;
import com.google.inject.*;
import lombok.*;
import lombok.extern.slf4j.*;
import net.runelite.client.callback.*;
import okhttp3.*;

import javax.inject.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** The plugin's HTTP surface against the copilot backend, all of it the v2 protobuf contract (servergolang/api-contract/api.proto). */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ApiClient {

    private static final String serverUrl = System.getProperty("flippingcopilot.api.host", "https://api.flippingcopilot.com");
    private static final String serverFeUrl = serverUrl.replace("api.", "");
    private static final MediaType PROTO_MEDIA_TYPE = MediaType.get("application/protobuf");
    private static final String API_VERSION_PREFIX = "/v2";
    private static final byte[] EMPTY_BODY = new byte[0];
    public static final String DEFAULT_COPILOT_PRICE_ERROR_MESSAGE = "Unable to fetch price copilot price (possible server update)";
    public static final String DEFAULT_PREMIUM_INSTANCE_ERROR_MESSAGE = "Error loading premium instance data (possible server update)";
    public static final String UNKNOWN_ERROR = "Unknown error";
    public static final int UNAUTHORIZED_CODE = 401;
    // dependencies
    private final OkHttpClient client;
    private final Gson gson;
    private final CopilotLogin copilotLogin;
    private final Preferences preferences;
    private final ClientThread clientThread;

    @FunctionalInterface
    private interface CheckedResponseConsumer {
        void accept(Response response) throws Exception;
    }

    private Request.Builder unauthed(String path) {
        return new Request.Builder().url(serverUrl + API_VERSION_PREFIX + path);
    }

    private Request.Builder authed(String jwtToken, String path) {
        return unauthed(path).addHeader("Authorization", "Bearer " + jwtToken);
    }

    private RequestBody protoBody(byte[] body) { return RequestBody.create(PROTO_MEDIA_TYPE, body); }

    /** A null body selects GET; zero seconds preserves the client's configured timeout. */
    private void request(String path, byte[] body, int seconds, String label,
                         Consumer<HttpResponseException> onFailure, CheckedResponseConsumer onSuccess) {
        String jwtToken = copilotLogin.get().getJwtToken();
        var builder = authed(jwtToken, path);
        if (body != null) { builder.post(protoBody(body)); }
        var http = seconds == 0 ? client : client.newBuilder().callTimeout(seconds, TimeUnit.SECONDS).build();
        enqueue(http.newCall(builder.build()), jwtToken, label, onFailure, onSuccess);
    }

    // a null jwtToken means the request was the login attempt itself, which always clears the login on a 401
    private void clearLoginIfUnauthorized(Response response, String jwtToken) {
        if (response.code() != UNAUTHORIZED_CODE) { return; }
        if (jwtToken == null || Objects.equals(jwtToken, copilotLogin.get().getJwtToken())) { copilotLogin.clear(); }
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
        enqueue(call, jwtToken, label, onFailure, onSuccess, this::extractErrorMessage);
    }

    private void enqueue(Call call, String jwtToken, String label,
                         Consumer<HttpResponseException> onFailure, CheckedResponseConsumer onSuccess,
                         Function<Response, String> errorMessageReader) {
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
                        String errorMessage = errorMessageReader.apply(response);
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

    private Consumer<HttpResponseException> runnableFailure(Runnable onFailure) { return ignored -> onFailure.run(); }

    public void authenticate(String username, String password, Consumer<LoginResponse> successCallback, Consumer<String> failureCallback) {
        Request request = unauthed("/login")
                .addHeader("Authorization", Credentials.basic(username, password))
                .post(protoBody(EMPTY_BODY))
                .build();

        enqueue(request, null, "login", stringFailure(failureCallback), response -> {
            if (response.body() == null) { throw new IOException("empty login response"); }
            successCallback.accept(LoginResponse.decodeProto(response.body().bytes()));
        });
    }

    // the discord login handshake is served by the website, so it is outside the v2 contract
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

        Consumer<HttpResponseException> failOnClientThread = error ->
                clientThread.invoke(() -> onFailure.accept(error));
        enqueue(call, null, "login via discord", failOnClientThread, response -> {
            if (response.body() == null) { throw new IOException("empty discord login response"); }
            try (DataInputStream is = new DataInputStream(new BufferedInputStream(response.body().byteStream()))) {
                var initResponse = PluginDiscordLoginInitResponse.fromRaw(is);
                clientThread.invoke(() -> oathUrlConsumer.accept(initResponse.url));
                var loginResponse = LoginResponse.fromRaw(is);
                if (loginResponse.error != null && !loginResponse.error.isEmpty()) {
                    failOnClientThread.accept(new HttpResponseException(-1, loginResponse.error));
                } else {
                    clientThread.invoke(() -> loginResponseConsumer.accept(loginResponse));
                }
            }
        }, this::extractJsonErrorMessage);

        return call;
    }

    public void getSuggestionAsync(byte[] status,
                                   Consumer<Suggestion> suggestionConsumer,
                                   Consumer<Data> graphDataConsumer,
                                   Consumer<HttpResponseException>  onFailure) {
        request("/suggestion", status, 0, "get suggestion",
                error -> clientThread.invoke(() -> onFailure.accept(error)),
                response -> handleSuggestionResponse(response, suggestionConsumer, graphDataConsumer));
    }

    private void handleSuggestionResponse(Response response, Consumer<Suggestion> suggestionConsumer, Consumer<Data> graphDataConsumer) throws IOException {
        if (response.body() == null) { throw new IOException("empty suggestion request response"); }
        Suggestion s;
        int contentLength = contentLength(response, "Content-Length");
        int suggestionContentLength = contentLength(response, "X-Suggestion-Content-Length");
        int graphDataContentLength = contentLength - suggestionContentLength;
        log.debug("suggestion response size is: {}, suggestion size is {}", contentLength, suggestionContentLength);

        var d = new Data();
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

            if (graphDataContentLength == 0) { d.loadingErrorMessage = "No graph data loaded for this item."; } else {
                d.loadingErrorMessage = "There was an issue loading the graph data for this item.";
                try {
                    byte[] remainingBytes = is.readAllBytes();
                    if (graphDataContentLength != remainingBytes.length) {
                        log.error("the graph data bytes read {} doesn't match the expected bytes {}", bytesRead, graphDataContentLength);
                    } else {
                        try {
                            d = Data.decodeProto(remainingBytes);
                            log.debug("graph data received");
                        } catch (Exception e) {
                            log.error("error deserializing graph data", e);
                        }
                    }
                } catch (IOException e) {
                    log.error("error on reading graph data bytes from the suggestion response", e);
                }
            }
        }
        if (s != null && s.type == SuggestionType.WAIT) { d.fromWaitSuggestion = true; }
        Data finalD = d;
        clientThread.invoke(() -> graphDataConsumer.accept(finalD));
    }

    private int contentLength(Response response, String header) throws IOException {
        try {
            String value = response.header(header);
            return Integer.parseInt(value != null ? value : "missing Content-Length header");
        } catch (NumberFormatException e) {
            throw new IOException("Failed to parse response Content-Length", e);
        }
    }

    public void sendTransactionsAsync(List<Transaction> transactions, String displayName, BiConsumer<Integer, List<Flip>> onSuccess, Consumer<HttpResponseException> onFailure) {
        log.debug("sending {} transactions for display name {}", transactions.size(), displayName);
        byte[] body = encodeMessage(out -> {
            for (Transaction transaction : transactions) {
                writeDelimitedMessageField(out, 1, transaction.encodeProto());
            }
            out.writeString(2, displayName);
        });
        postProto("/profit-tracking/client-transactions", body, "sync transactions",
                Flip::listDecodeProto, onSuccess, onFailure);
    }

    public void toggleItemPortfolioAsync(PortfolioRequest payload,
                                         BiConsumer<Integer, PortfolioResponse> onSuccess,
                                         Consumer<HttpResponseException> onFailure) {
        postProto("/profit-tracking/toggle-item-portfolio", payload.encodeProto(),
                "toggle item portfolio account=" + payload.accountId + " item=" + payload.itemId,
                PortfolioResponse::decodeProto, onSuccess, onFailure);
    }

    // reads the protobuf error body every v2 endpoint replies with on failure
    private String extractErrorMessage(Response response) {
        if (response.body() != null) {
            try {
                var error = ApiError.decodeProto(response.body().bytes());
                if (!error.displayErr.isEmpty()) { return error.displayErr; }
            } catch (Exception e) {
                log.warn("failed reading/parsing error message from http {} response body", response.code(), e);
            }
        }
        return UNKNOWN_ERROR;
    }

    // reads the website's JSON error body; only the discord login handshake needs this
    private String extractJsonErrorMessage(Response response) {
        if (response.body() != null) {
            try {
                String bodyStr = response.body().string();
                JsonObject errorJson = gson.fromJson(bodyStr, JsonObject.class);
                if (errorJson.has("message")) { return errorJson.get("message").getAsString(); }
            } catch (Exception e) {
                log.warn("failed reading/parsing error message from http {} response body", response.code(), e);
            }
        }
        return UNKNOWN_ERROR;
    }

    public void asyncGetVisualizeFlipData(UUID flipID, Consumer<VisualizeFlipResponse> onSuccess, Consumer<String> onFailure) {
        byte[] body = encodeUuidRequest(flipID);
        log.debug("requesting visualize data for flip {}", flipID);
        request("/profit-tracking/visualize-flip", body, 30, "visualize flip " + flipID, stringFailure(onFailure), response -> {
            var rsp = VisualizeFlipResponse.decodeProto(response.body().bytes());
            log.debug("visualize data received for flip {}", flipID);
            onSuccess.accept(rsp);
        });
    }

    public void asyncGetItemPriceWithGraphData(int itemId, String displayName, Consumer<ItemPrice> consumer, boolean includeGraphData) {
        byte[] body = encodeMessage(out -> {
            out.writeInt32(1, itemId);
            out.writeString(2, displayName);
            out.writeBool(3, preferences.isF2pOnlyMode());
            out.writeDouble(4, preferences.getTimeframe());
            out.writeBool(5, includeGraphData);
        });
        log.debug("requesting price graph data for item {}", itemId);
        Consumer<HttpResponseException> emitError = error -> {
            var ip = new ItemPrice(0, 0, DEFAULT_COPILOT_PRICE_ERROR_MESSAGE, null);
            clientThread.invoke(() -> consumer.accept(ip));
        };
        request("/prices", body, 30, "copilot price item=" + itemId, emitError, response -> {
            var ip = ItemPrice.decodeProto(response.body().bytes());
            log.debug("price graph data received for item {}", itemId);
            clientThread.invoke(() -> consumer.accept(ip));
        });
    }

    public void asyncUpdatePremiumInstances(Consumer<PremiumInstanceStatus> consumer, List<String> displayNames) {
        byte[] payload = encodeMessage(out -> {
            for (String displayName : displayNames) { out.writeString(1, displayName); }
        });
        requestPremiumStatus("/premium-instances/update-assignments", payload, "update premium instances", consumer);
    }

    public void asyncGetPremiumInstanceStatus(Consumer<PremiumInstanceStatus> consumer) {
        requestPremiumStatus("/premium-instances/status", null, "get premium instance status", consumer);
    }

    private void requestPremiumStatus(String path, byte[] body, String label,
                                      Consumer<PremiumInstanceStatus> consumer) {
        request(path, body, 0, label, error -> emitPremiumInstanceError(consumer), response -> {
            var status = PremiumInstanceStatus.decodeProto(response.body().bytes());
            clientThread.invoke(() -> consumer.accept(status));
        });
    }

    private void emitPremiumInstanceError(Consumer<PremiumInstanceStatus> consumer) {
        clientThread.invoke(() -> consumer.accept(PremiumInstanceStatus.ErrorInstance(DEFAULT_PREMIUM_INSTANCE_ERROR_MESSAGE)));
    }

    // v2 replies with the same ClientFlips list as its sibling endpoints, where v1 replied with the single deleted flip
    public void asyncDeleteFlip(Flip flip, Consumer<List<Flip>> onSuccess, Runnable onFailure) {
        byte[] body = encodeUuidRequest(flip.id);
        request("/profit-tracking/delete-flip", body, 0, "delete flip " + flip.id, runnableFailure(onFailure),
                response -> onSuccess.accept(Flip.listDecodeProto(response.body().bytes())));
    }

    public void asyncAddMissedSale(UUID flipId, long price, int quantity,
                                   BiConsumer<Integer, List<Flip>> onSuccess,
                                   Consumer<HttpResponseException> onFailure) {
        byte[] body = encodeMessage(out -> {
            out.writeByteArray(1, uuidToBytes(flipId));
            out.writeInt32(3, quantity);
            out.writeInt64(4, price);
        });
        postProto("/profit-tracking/add-missed-sale", body,
                "add missed sale flip=" + flipId, Flip::listDecodeProto, onSuccess, onFailure);
    }

    public void asyncReviveGhostFlip(UUID flipId,
                                     BiConsumer<Integer, List<Flip>> onSuccess,
                                     Consumer<HttpResponseException> onFailure) {
        byte[] body = encodeMessage(out -> {
            out.writeByteArray(1, uuidToBytes(flipId));
        });
        postProto("/profit-tracking/revive-ghost-flip", body,
                "revive ghost flip=" + flipId, Flip::listDecodeProto, onSuccess, onFailure);
    }

    @FunctionalInterface
    private interface Decoder<T> {
        T decode(byte[] bytes) throws IOException;
    }

    private <T> void postProto(String path, byte[] body, String logLabel, Decoder<T> decoder,
                                         BiConsumer<Integer, T> onSuccess,
                                         Consumer<HttpResponseException> onFailure) {
        Integer userId = copilotLogin.get().getUserId();
        request(path, body, 0, logLabel, onFailure,
                response -> onSuccess.accept(userId, decoder.decode(response.body().bytes())));
    }

    public void asyncClearAccountPortfolio(int accountId,
                                             BiConsumer<Integer, PortfolioResponse> onSuccess,
                                             Consumer<HttpResponseException> onFailure) {
        byte[] body = encodeAccountRequest(accountId);
        postProto("/profit-tracking/clear-account-portfolio", body,
                "clear account portfolio account=" + accountId,
                PortfolioResponse::decodeProto, onSuccess, onFailure);
    }

    public void asyncDeleteAccount(int accountId, Runnable onSuccess, Runnable onFailure) {
        byte[] body = encodeAccountRequest(accountId);
        request("/profit-tracking/delete-account", body, 0, "delete account " + accountId, runnableFailure(onFailure),
                response -> onSuccess.run());
    }

    public void asyncLoadAccounts(Consumer<Map<String, Integer>> onSuccess, Consumer<String> onFailure) {
        request("/profit-tracking/rs-account-names", null, 0, "load user display names", stringFailure(onFailure), response -> {
            byte[] responseBody = response.body() != null ? response.body().bytes() : new byte[0];
            onSuccess.accept(decodeRsAccountNames(responseBody));
        });
    }

    public void asyncLoadFlips(Map<Integer, Integer> accountIdTime, BiConsumer<Integer, FlipsDelta> onSuccess, Consumer<String> onFailure) {
        Integer userId = copilotLogin.get().getUserId();
        String jwtToken = copilotLogin.get().getJwtToken();
        var body = new DataDeltaRequest(accountIdTime);

        Request request = authed(jwtToken, "/profit-tracking/client-flips-delta")
                .post(protoBody(body.encodeProto()))
                .build();

        enqueue(request, jwtToken, "load flips", stringFailure(onFailure),
                response -> onSuccess.accept(userId, FlipsDelta.decodeProto(response.body().bytes())));
    }

    public void asyncLoadTransactionsData(String displayName, Consumer<byte[]> onSuccess, Consumer<String> onFailure) {
        String jwtToken = copilotLogin.get().getJwtToken();
        var body = new TransactionsRequest(0, 0, displayName);

        Request request = authed(jwtToken, "/profit-tracking/account-client-transactions")
                .post(protoBody(body.encodeProto()))
                .build();

        enqueue(request, jwtToken, "load transactions", stringFailure(onFailure), response -> {
            onSuccess.accept(AckedTransaction.listDecodeProto(response.body().bytes()));
        });
    }

    // the response is a long-lived stream of length-prefixed DumpAlert frames, so it is handed to the caller open
    public Call asyncConsumeDumpAlerts(String displayName, Consumer<Response> onSuccess, Consumer<HttpResponseException> onFailure) {
        String jwtToken = copilotLogin.get().getJwtToken();
        byte[] body = encodeMessage(out -> out.writeString(1, displayName));
        Request request = authed(jwtToken, "/dump-alerts")
                .post(protoBody(body))
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
                    clearLoginIfUnauthorized(response, jwtToken);
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

    private static byte[] encodeUuidRequest(UUID id) {
        return encodeMessage(out -> out.writeByteArray(1, uuidToBytes(id)));
    }

    private static byte[] encodeAccountRequest(int accountId) {
        return encodeMessage(out -> out.writeInt32(1, accountId));
    }

    private static Map<String, Integer> decodeRsAccountNames(byte[] bytes) throws IOException {
        Map<String, Integer> names = new HashMap<>();
        var input = CodedInputStream.newInstance(bytes);
        for (int tag; (tag = input.readTag()) != 0;) {
            if (WireFormat.getTagFieldNumber(tag) != 1) {
                input.skipField(tag);
                continue;
            }

            int length = input.readRawVarint32(), limit = input.pushLimit(length);
            String displayName = "";
            int accountId = 0;
            for (int entryTag; (entryTag = input.readTag()) != 0;) {
                switch (WireFormat.getTagFieldNumber(entryTag)) {
                    case 1: displayName = input.readString(); break;
                    case 2: accountId = input.readInt32(); break;
                    default:
                        input.skipField(entryTag);
                }
            }
            input.popLimit(limit);
            names.put(displayName, accountId);
        }
        return names;
    }

    public void asyncOrphanTransaction(AckedTransaction transaction, BiConsumer<Integer, List<Flip>> onSuccess, Runnable onFailure) {
        asyncModifyTransaction("/profit-tracking/orphan-transaction", "orphaning transaction", transaction, onSuccess, onFailure);
    }

    public void asyncDeleteTransaction(AckedTransaction transaction, BiConsumer<Integer, List<Flip>> onSuccess, Runnable onFailure) {
        asyncModifyTransaction("/profit-tracking/delete-transaction", "delete transaction", transaction, onSuccess, onFailure);
    }

    private void asyncModifyTransaction(String path,
                                        String label,
                                        AckedTransaction transaction,
                                        BiConsumer<Integer, List<Flip>> onSuccess,
                                        Runnable onFailure) {
        byte[] body = encodeMessage(out -> {
            out.writeByteArray(1, uuidToBytes(transaction.id));
            out.writeInt32(2, transaction.accountId);
        });
        postProto(path, body, label + " " + transaction.id,
                Flip::listDecodeProto, onSuccess, runnableFailure(onFailure));
    }

}
