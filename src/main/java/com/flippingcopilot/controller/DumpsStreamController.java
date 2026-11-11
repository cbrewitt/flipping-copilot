package com.flippingcopilot.controller;


import com.flippingcopilot.model.*;
import com.flippingcopilot.rs.*;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import okhttp3.*;
import okio.BufferedSource;

import javax.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.atomic.*;
import java.util.concurrent.*;

@Slf4j
@Singleton
public class DumpsStreamController {

    // a dump alert is a few hundred bytes; anything near this is a framing bug
    private static final long MAX_FRAME_BYTES = 1 << 20;

    private final ClientThread clientThread;
    private final ScheduledExecutorService executor;
    private final ApiRequestHandler apiRequestHandler;
    private final SuggestionController suggestionController;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<Call> activeCall = new AtomicReference<>();
    private final ReactiveState<String> subscribedDisplayName;

    @Inject
    public DumpsStreamController(ClientThread clientThread,
                                 ApiRequestHandler apiRequestHandler,
                                 SuggestionController suggestionController,
                                 AccountSuggestionPreferencesRS accountSuggestionPreferencesRS,
                                 OsrsLoginRS osrsLoginRS,
                                 CopilotLoginRS copilotLoginRS,
                                 GrandExchangeOpenRS grandExchangeOpenRS, ScheduledExecutorService executor) {
        this.clientThread = clientThread;
        this.executor = executor;
        this.apiRequestHandler = apiRequestHandler;
        this.suggestionController = suggestionController;
        this.subscribedDisplayName = ReactiveStateUtil.derive(
                copilotLoginRS,
                osrsLoginRS,
                accountSuggestionPreferencesRS,
                grandExchangeOpenRS,
                (copilotLoginState, loginState, preferences, isGrandExchangeOpen) -> {
                    if (!loginState.loggedIn
                            || loginState.displayName == null
                            || loginState.displayName.isBlank()
                            || !copilotLoginState.isLoggedIn()
                            || !preferences.isReceiveDumpSuggestions()
                            || !Boolean.TRUE.equals(isGrandExchangeOpen)) {
                        return null;
                    }
                    return loginState.market.id + ":" + loginState.displayName;
                });
        subscribedDisplayName.registerListener(name -> {
            if (name != null) {
                consumeDumps();
            } else {
                ensureUnsubscribed();
            }
        });
    }

    public void ensureUnsubscribed() {
        generation.incrementAndGet();
        Call call = activeCall.getAndSet(null);
        if (call != null) {
            call.cancel();
        }
    }

    private void consumeDumps() {
        String subscription = subscribedDisplayName.get();
        if (subscription == null) return;
        long expected = generation.incrementAndGet();
        int separator = subscription.indexOf(':');
        Market market = subscription.startsWith(Market.HC.id + ":") ? Market.HC : Market.OSRS;
        String displayName = subscription.substring(separator + 1);
        Call call = apiRequestHandler.asyncConsumeDumpAlerts(displayName, market,
                response -> {
                    if (generation.get() == expected) consumeDumpStream(response, expected);
                    else response.close();
                }, error -> {
                    reconnect(expected);
                });
        Call previous = activeCall.getAndSet(call);
        if (previous != null) previous.cancel();
        if (generation.get() != expected) call.cancel();
    }

    private void reconnect(long expected) {
        executor.schedule(() -> { if (generation.get() == expected) consumeDumps(); }, 5, TimeUnit.SECONDS);
    }

    // each frame is a uvarint byte length followed by that many bytes of an encoded DumpAlert; a zero-length frame is the keepalive
    private void consumeDumpStream(Response response, long expected) {
        try (Response resp = response) {
            BufferedSource source = resp.body().source();
            while (generation.get() == expected) {
                long length = readUvarint(source);
                if (length == 0) {
                    continue;
                }
                // a negative length means the uvarint overflowed, and readByteArray would then throw an unchecked exception
                if (length < 0 || length > MAX_FRAME_BYTES) {
                    throw new IOException("invalid dump frame length: " + length);
                }
                handleDumpMessage(source.readByteArray(length), expected);
            }
        } catch (IOException e) {
            String displayName = subscribedDisplayName.get();
            if (displayName != null && generation.get() == expected) {
                log.warn("dump alerts stream error", e);
                reconnect(expected);
            } else {
                log.info("consumeDumpStream ended gracefully");
            }
        }
    }

    private static long readUvarint(BufferedSource source) throws IOException {
        long value = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            int b = source.readByte() & 0xFF;
            value |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
        }
        throw new IOException("dump frame length is not a valid uvarint");
    }

    private void handleDumpMessage(byte[] data, long expected) throws IOException {
        Suggestion suggestion = DumpAlert.decodeProto(data).suggestion;
        if (suggestion == null) {
            log.warn("dump suggestion decode failed");
            return;
        }
        suggestion.setMessage("<html><b><font color=#FA4A4B>Dump alert!!</font></b></html>");
        suggestion.setDumpAlert(true);
        suggestion.setDumpAlertReceived(Instant.now());
        clientThread.invoke(() -> {
            if (generation.get() == expected) suggestionController.handleDumpSuggestion(suggestion);
        });
        log.info("received dump suggestion {} {}", suggestion.getName(), suggestion.getType());
    }

}
