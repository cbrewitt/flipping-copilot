package copilot.controller;

import copilot.model.*;
import copilot.rs.CopilotLogin;
import copilot.util.ProtoUtils;
import com.google.gson.Gson;
import net.runelite.client.callback.ClientThread;
import okhttp3.*;
import okio.Buffer;
import org.junit.After;
import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static org.junit.Assert.*;

public class ApiRequestHandlerTest {
    private final MemoryLogin login = new MemoryLogin();
    private OkHttpClient http;
    private final List<String> clientDispatches = new CopyOnWriteArrayList<>();

    private ApiClient handler(Interceptor interceptor) {
        http = new OkHttpClient.Builder().callTimeout(7, TimeUnit.SECONDS).addInterceptor(interceptor).build();
        ClientThread clientThread = new ClientThread() {
            @Override public void invoke(Runnable action) {
                clientDispatches.add("callback");
                action.run();
            }
        };
        return new ApiClient(http, new Gson(), login, null, clientThread);
    }

    private Response reply(Request request, int status, byte[] bytes) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(status).message("Test response")
                .body(ResponseBody.create(MediaType.get("application/protobuf"), bytes)).build();
    }

    @After public void closeClient() {
        if (http != null) {
            http.dispatcher().executorService().shutdownNow();
            http.connectionPool().evictAll();
        }
    }

    @Test public void portfolioPostPreservesPayloadAndRequestUser() throws Exception {
        login.signIn(7, "original-token");
        PortfolioRequest payload = new PortfolioRequest(11, 42, -1, 4, -1, 2);
        ApiClient api = handler(chain -> {
            Request request = chain.request();
            assertEquals("/v2/profit-tracking/toggle-item-portfolio", request.url().encodedPath());
            assertEquals("POST", request.method());
            assertEquals("Bearer original-token", request.header("Authorization"));
            Buffer bytes = new Buffer();
            request.body().writeTo(bytes);
            assertArrayEquals(payload.encodeProto(), bytes.readByteArray());
            login.signIn(99, "replacement-token");
            return reply(request, 200, new byte[0]);
        });
        CompletableFuture<Integer> completed = new CompletableFuture<>();
        api.toggleItemPortfolioAsync(payload, (userId, result) -> {
            assertNotNull(result);
            assertTrue(result.getPortfolioItems().isEmpty());
            completed.complete(userId);
        }, completed::completeExceptionally);
        assertEquals(Integer.valueOf(7), completed.get(5, TimeUnit.SECONDS));
    }

    @Test public void staleUnauthorizedResponseDoesNotClearNewLogin() throws Exception {
        login.signIn(7, "old-token");
        ApiClient api = handler(chain -> {
            login.signIn(8, "new-token");
            return reply(chain.request(), 401, ProtoUtils.encodeMessage(out -> out.writeString(2, "Expired")));
        });
        CompletableFuture<HttpResponseException> failed = new CompletableFuture<>();
        api.asyncReviveGhostFlip(UUID.randomUUID(), (user, flips) -> fail("Unexpected success"), failed::complete);
        assertEquals(401, failed.get(5, TimeUnit.SECONDS).getResponseCode());
        assertEquals("new-token", login.get().getJwtToken());
        assertEquals(0, login.clears);
    }

    @Test public void currentUnauthorizedResponseClearsLoginAndReportsServerMessage() throws Exception {
        login.signIn(7, "expired-token");
        ApiClient api = handler(chain -> reply(chain.request(), 401,
                ProtoUtils.encodeMessage(out -> out.writeString(2, "Please sign in"))));
        CompletableFuture<HttpResponseException> failed = new CompletableFuture<>();
        api.asyncAddMissedSale(UUID.randomUUID(), 100L, 2,
                (user, flips) -> fail("Unexpected success"), failed::complete);
        assertEquals("Please sign in", failed.get(5, TimeUnit.SECONDS).getResponseMessage());
        assertFalse(login.get().isLoggedIn());
        assertEquals(1, login.clears);
    }

    @Test public void discordHandshakeDispatchesUrlBeforeLoginResult() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        writeString(out, "https://example.invalid/oauth");
        writeString(out, "signed-in-token");
        out.writeInt(7);
        writeString(out, "");
        ApiClient api = handler(chain -> {
            assertEquals("/v1/plugin-discord-login", chain.request().url().encodedPath());
            assertEquals("GET", chain.request().method());
            return reply(chain.request(), 200, bytes.toByteArray());
        });
        List<String> order = new CopyOnWriteArrayList<>();
        CompletableFuture<LoginResponse> result = new CompletableFuture<>();
        api.discordLoginAsync(url -> order.add(url), response -> {
            order.add("login");
            result.complete(response);
        }, result::completeExceptionally);
        assertEquals(7, result.get(5, TimeUnit.SECONDS).getUserId());
        assertEquals(Arrays.asList("https://example.invalid/oauth", "login"), order);
        assertEquals(2, clientDispatches.size());
    }

    @Test public void discordHttpErrorsUseJsonAndClientDispatch() throws Exception {
        login.signIn(7, "expired-token");
        ApiClient api = handler(chain -> reply(chain.request(), 401,
                "{\"message\":\"Discord login expired\"}".getBytes(StandardCharsets.UTF_8)));
        CompletableFuture<HttpResponseException> failed = new CompletableFuture<>();
        api.discordLoginAsync(url -> fail("Unexpected URL"), response -> fail("Unexpected login"), failed::complete);
        assertEquals("Discord login expired", failed.get(5, TimeUnit.SECONDS).getResponseMessage());
        assertEquals(1, login.clears);
        assertEquals(1, clientDispatches.size());
    }

    @Test public void sharedAuthenticatedRequestsPreserveTransportAndCallbacks() throws Exception {
        login.signIn(7, "request-token");
        UUID id = new UUID(12, 34);
        Map<String, byte[]> bodies = new HashMap<>();
        bodies.put("/suggestion", new byte[]{4, 5, 6});
        bodies.put("/profit-tracking/visualize-flip", ProtoUtils.encodeMessage(out -> out.writeByteArray(1, ProtoUtils.uuidToBytes(id))));
        bodies.put("/profit-tracking/delete-flip", bodies.get("/profit-tracking/visualize-flip"));
        bodies.put("/profit-tracking/delete-account", ProtoUtils.encodeMessage(out -> out.writeInt32(1, 42)));
        bodies.put("/premium-instances/update-assignments", ProtoUtils.encodeMessage(out -> {
            out.writeString(1, "first"); out.writeString(1, "second");
        }));
        bodies.put("/premium-instances/status", null);
        bodies.put("/profit-tracking/rs-account-names", null);
        Set<String> received = ConcurrentHashMap.newKeySet();
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        CountDownLatch completed = new CountDownLatch(8); // Suggestion delivers two callbacks.
        Consumer<Object> success = result -> completed.countDown();
        Consumer<String> failure = message -> { errors.add(new AssertionError(message)); completed.countDown(); };
        ApiClient api = handler(chain -> {
            Request request = chain.request();
            String path = request.url().encodedPath().substring(3);
            try {
                assertTrue(bodies.containsKey(path));
                assertTrue(received.add(path));
                assertEquals("Bearer request-token", request.header("Authorization"));
                byte[] expected = bodies.get(path);
                assertEquals(expected == null ? "GET" : "POST", request.method());
                if (expected == null) assertNull(request.body());
                else {
                    Buffer buffer = new Buffer(); request.body().writeTo(buffer);
                    assertArrayEquals(expected, buffer.readByteArray());
                    assertEquals("application/protobuf", request.body().contentType().toString());
                }
                long seconds = path.endsWith("visualize-flip") ? 30 : 7;
                assertEquals(TimeUnit.SECONDS.toNanos(seconds), chain.call().timeout().timeoutNanos());
            } catch (Throwable error) { errors.add(error); }
            return reply(request, 200, new byte[0]).newBuilder()
                    .header("Content-Length", "0").header("X-Suggestion-Content-Length", "0").build();
        });
        api.getSuggestionAsync(bodies.get("/suggestion"), success::accept, success::accept,
                error -> failure.accept(error.getMessage()));
        api.asyncGetVisualizeFlipData(id, success::accept, failure);
        Flip flip = new Flip(); flip.id = id;
        api.asyncDeleteFlip(flip, success::accept, () -> failure.accept("delete flip"));
        api.asyncDeleteAccount(42, () -> success.accept(null), () -> failure.accept("delete account"));
        api.asyncUpdatePremiumInstances(success::accept, Arrays.asList("first", "second"));
        api.asyncGetPremiumInstanceStatus(success::accept);
        api.asyncLoadAccounts(success::accept, failure);
        assertTrue("Callbacks did not finish", completed.await(5, TimeUnit.SECONDS));
        assertEquals(bodies.keySet(), received);
        assertTrue(errors.toString(), errors.isEmpty());
    }

    @Test public void graphFailuresStillDeliverSuggestionBeforeFallback() throws Exception {
        byte[] valid = ProtoUtils.encodeMessage(out -> out.writeString(27, "Item"));
        byte[][] payloads = {new byte[0], new byte[]{(byte) 128}, new byte[]{1}, valid};
        int[] lengths = {0, 1, 2, valid.length};
        java.util.concurrent.atomic.AtomicInteger scenario = new java.util.concurrent.atomic.AtomicInteger();
        ApiClient api = handler(chain -> {
            int i = scenario.get();
            return reply(chain.request(), 200, payloads[i]).newBuilder()
                    .header("Content-Length", String.valueOf(lengths[i]))
                    .header("X-Suggestion-Content-Length", "0").build();
        });
        for (int i = 0; i < payloads.length; i++) {
            scenario.set(i);
            List<String> order = new CopyOnWriteArrayList<>();
            CompletableFuture<copilot.ui.graph.model.Data> result = new CompletableFuture<>();
            api.getSuggestionAsync(new byte[0], suggestion -> order.add("suggestion"), graph -> {
                order.add("graph"); result.complete(graph);
            }, result::completeExceptionally);
            var graph = result.get(5, TimeUnit.SECONDS);
            assertEquals(Arrays.asList("suggestion", "graph"), order);
            if (i == 0) assertEquals("No graph data loaded for this item.", graph.loadingErrorMessage);
            else if (i < 3) assertEquals("There was an issue loading the graph data for this item.", graph.loadingErrorMessage);
            else { assertNull(graph.loadingErrorMessage); assertEquals("Item", graph.name); }
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    // The real persistence hooks are bypassed: tests never read or delete saved credentials.
    private static class MemoryLogin extends CopilotLogin {
        int clears;
        MemoryLogin() { super(new Gson(), new DoesNothingExecutorService()); }
        @Override protected LoginResponse loadLoginResponse() { return null; }
        @Override public void clear() { clears++; set(new CopilotLoginState()); }
        void signIn(int userId, String token) {
            CopilotLoginState state = new CopilotLoginState();
            state.loginResponse = new LoginResponse(token, userId, "");
            set(state);
        }
    }
    @Test public void sharedLengthParserPreservesBothHeadersAndParseFailures() throws Exception {
        ApiClient handler = new ApiClient(null, null, null, null, null);
        java.lang.reflect.Method parser = ApiClient.class.getDeclaredMethod("contentLength", Response.class, String.class);
        parser.setAccessible(true);
        Request request = new Request.Builder().url("https://example.invalid/").build();
        for (String header : new String[]{"Content-Length", "X-Suggestion-Content-Length"}) {
            for (String value : new String[]{"0", "123", "-1", "2147483647"}) {
                try (Response response = reply(request, 200, new byte[0]).newBuilder().header(header, value).build()) {
                    assertEquals(Integer.parseInt(value), parser.invoke(handler, response, header));
                }
            }
            for (String value : new String[]{null, "invalid", "2147483648"}) {
                Response.Builder builder = reply(request, 200, new byte[0]).newBuilder();
                if (value != null) builder.header(header, value);
                try (Response response = builder.build()) {
                    java.lang.reflect.InvocationTargetException failure = assertThrows(
                            java.lang.reflect.InvocationTargetException.class, () -> parser.invoke(handler, response, header));
                    assertTrue(failure.getCause() instanceof IOException);
                    assertEquals("Failed to parse response Content-Length", failure.getCause().getMessage());
                    assertTrue(failure.getCause().getCause() instanceof NumberFormatException);
                }
            }
        }
    }
}
