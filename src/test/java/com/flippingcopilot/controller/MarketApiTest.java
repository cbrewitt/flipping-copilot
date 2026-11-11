package com.flippingcopilot.controller;

import com.flippingcopilot.model.*;
import com.flippingcopilot.rs.*;
import com.flippingcopilot.util.ProtoUtils;
import com.google.gson.*;
import com.google.gson.stream.*;
import java.time.Instant;
import java.io.IOException;
import net.runelite.client.util.Filepath;
import okhttp3.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class MarketApiTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private ExecutorService executor;
    private Persistance persistence;
    private CopilotLoginRS user;
    private MarketRS markets;
    private OsrsLoginRS login;
    private OkHttpClient client;
    private final BlockingQueue<String> paths = new LinkedBlockingQueue<>();
    private ApiRequestHandler api;

    @Before public void setup() throws Exception {
        Constructor<Filepath> constructor = Filepath.class.getDeclaredConstructor(Path.class, Path.class);
        constructor.setAccessible(true);
        Path path = folder.getRoot().toPath();
        Gson gson = new GsonBuilder().registerTypeAdapter(Instant.class, new TypeAdapter<Instant>() {
            public void write(JsonWriter out, Instant value) throws IOException { if (value == null) out.nullValue(); else out.value(value.toString()); }
            public Instant read(JsonReader in) throws IOException { if (in.peek() == JsonToken.NULL) { in.nextNull(); return null; } return Instant.parse(in.nextString()); }
        }).create();
        persistence = new Persistance(gson, constructor.newInstance(path, path));
        executor = Executors.newSingleThreadExecutor();
        user = new CopilotLoginRS(persistence, new Gson(), executor);
        user.update(s -> { s.loginResponse = new LoginResponse("test", 7, null); return s; });
        login = new OsrsLoginRS(); markets = new MarketRS(login);
        client = new OkHttpClient.Builder().addInterceptor(chain -> {
            paths.add(chain.request().url().encodedPath());
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(ResponseBody.create(MediaType.get("application/protobuf"), new byte[0])).build();
        }).build();
        api = new ApiRequestHandler(client, user, null, null, markets);
    }
    @After public void close() throws Exception {
        client.dispatcher().executorService().shutdownNow();
        client.connectionPool().evictAll();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test public void hcUploadUsesOriginalMarketAfterConversionAndSharedCallsStayShared() throws Exception {
        login.set(OsrsLoginState.builder().loggedIn(true).market(Market.HC).build());
        login.set(login.get().toBuilder().market(Market.OSRS).build());
        CountDownLatch completed = new CountDownLatch(2);
        api.sendTransactionsAsync(Collections.emptyList(), "Player", Market.HC, (id, flips) -> completed.countDown(), e -> fail(e.getMessage()));
        assertEquals("/v2/osrs-hc/profit-tracking/client-transactions", paths.poll(5, TimeUnit.SECONDS));
        login.set(login.get().toBuilder().market(Market.HC).build());
        api.asyncDeleteAccount(42, completed::countDown, () -> fail("delete failed"));
        assertEquals("/v2/profit-tracking/delete-account", paths.poll(5, TimeUnit.SECONDS));
        assertTrue(completed.await(5, TimeUnit.SECONDS));
    }

    @Test public void pendingTransactionsPersistSeparatelyAndSurviveReload() {
        Transaction regular = new Transaction(); regular.setId(UUID.randomUUID());
        Transaction hc = new Transaction(); hc.setId(UUID.randomUUID());
        persistence.storeUnAckedTransactions(Collections.singletonList(regular), "Player", Market.OSRS);
        persistence.storeUnAckedTransactions(Collections.singletonList(hc), "Player", Market.HC);
        assertEquals(regular.getId(), persistence.loadUnAckedTransactions("Player", Market.OSRS).get(0).getId());
        assertEquals(hc.getId(), persistence.loadUnAckedTransactions("Player", Market.HC).get(0).getId());
        persistence.storeUnAckedTransactions(Collections.emptyList(), "Player", Market.HC);
        assertTrue(persistence.loadUnAckedTransactions("Player", Market.HC).isEmpty());
        assertEquals(1, persistence.loadUnAckedTransactions("Player", Market.OSRS).size());
    }

    @Test public void accountDiscoveryUsesHistoryMarketNotCurrentGame() throws Exception {
        byte[] body = ProtoUtils.encodeMessage(out -> {
            for (Market market : Market.values()) {
                byte[] entry = ProtoUtils.encodeMessage(e -> {
                    e.writeInt32(1,42); e.writeString(2,"Player");
                    e.writeString(3,market.id); e.writeString(4,"osrs");
                });
                ProtoUtils.writeDelimitedMessageField(out,2,entry);
            }
        });
        for (Market market : Market.values()) {
            assertEquals(Collections.singletonMap("Player",42), ApiRequestHandler.decodeRsAccountNames(body).get(market.id));
        }
    }
}
