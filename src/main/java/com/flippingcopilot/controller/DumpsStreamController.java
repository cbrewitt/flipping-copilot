package com.flippingcopilot.controller;


import com.flippingcopilot.model.DumpAlert;
import com.flippingcopilot.model.Suggestion;
import com.flippingcopilot.rs.*;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import okhttp3.Call;
import okhttp3.Response;
import okio.BufferedSource;

import javax.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Singleton
public class DumpsStreamController {

    // a dump alert is a few hundred bytes; anything near this is a framing bug
    private static final long MAX_FRAME_BYTES = 1 << 20;

    private final ClientThread clientThread;
    private final ApiRequestHandler apiRequestHandler;
    private final SuggestionController suggestionController;
    private final AtomicReference<Call> activeCall = new AtomicReference<>();
    private final ReactiveState<String> subscribedDisplayName;

    @Inject
    public DumpsStreamController(ClientThread clientThread,
                                 ApiRequestHandler apiRequestHandler,
                                 SuggestionController suggestionController,
                                 AccountSuggestionPreferencesRS accountSuggestionPreferencesRS,
                                 OsrsLoginRS osrsLoginRS,
                                 CopilotLoginRS copilotLoginRS,
                                 GrandExchangeOpenRS grandExchangeOpenRS) {
        this.clientThread = clientThread;
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
                    return loginState.displayName;
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
        Call call = activeCall.getAndSet(null);
        if (call != null) {
            call.cancel();
        }
    }

    private void consumeDumps() {
        String displayName = subscribedDisplayName.get();
        if(displayName != null) {
            Call previous = activeCall.get();
            Call call = apiRequestHandler.asyncConsumeDumpAlerts(displayName,
                    this::consumeDumpStream,
                    error -> {
                        log.warn("dump alerts connection failed, re-connecting: {}", error.getMessage());
                        consumeDumps();
                    }
            );
            log.info("{} subscribing to dumps", displayName);
            if (activeCall.getAndSet(call) != previous || subscribedDisplayName.get() == null) { // catches race edge case
                ensureUnsubscribed();
            }
        }
    }

    // each frame is a uvarint byte length followed by that many bytes of an encoded DumpAlert; a zero-length frame is the keepalive
    private void consumeDumpStream(Response response) {
        try (Response resp = response) {
            BufferedSource source = resp.body().source();
            while (activeCall.get() != null) {
                long length = readUvarint(source);
                if (length == 0) {
                    continue;
                }
                // a negative length means the uvarint overflowed, and readByteArray would then throw an unchecked exception
                if (length < 0 || length > MAX_FRAME_BYTES) {
                    throw new IOException("invalid dump frame length: " + length);
                }
                handleDumpMessage(source.readByteArray(length));
            }
        } catch (IOException e) {
            String displayName = subscribedDisplayName.get();
            if (displayName != null) {
                log.warn("dump alerts stream error", e);
                consumeDumps();
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

    private void handleDumpMessage(byte[] data) throws IOException {
        Suggestion suggestion = DumpAlert.decodeProto(data).suggestion;
        if (suggestion == null) {
            log.warn("dump suggestion decode failed");
            return;
        }
        suggestion.setMessage("<html><b><font color=#FA4A4B>Dump alert!!</font></b></html>");
        suggestion.setDumpAlert(true);
        suggestion.setDumpAlertReceived(Instant.now());
        clientThread.invoke(() -> suggestionController.handleDumpSuggestion(suggestion));
        log.info("received dump suggestion {} {}", suggestion.getName(), suggestion.getType());
    }

}
