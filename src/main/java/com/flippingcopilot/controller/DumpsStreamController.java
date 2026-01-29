package com.flippingcopilot.controller;


import com.flippingcopilot.model.Suggestion;
import com.flippingcopilot.rs.AccountSuggestionPreferencesRS;
import com.flippingcopilot.rs.GrandExchangeOpenRS;
import com.flippingcopilot.rs.OsrsLoginRS;
import com.flippingcopilot.rs.ReactiveState;
import com.flippingcopilot.rs.ReactiveStateUtil;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import okhttp3.Call;
import okhttp3.Response;
import okio.BufferedSource;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Singleton
public class DumpsStreamController {

    private static final byte HEARTBEAT_BYTE = (byte) 0x01;
    private static final byte MESSAGE_BYTE = (byte) 0x02;

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
                                 GrandExchangeOpenRS grandExchangeOpenRS) {
        this.clientThread = clientThread;
        this.apiRequestHandler = apiRequestHandler;
        this.suggestionController = suggestionController;
        this.subscribedDisplayName = ReactiveStateUtil.derive(
                osrsLoginRS,
                accountSuggestionPreferencesRS,
                grandExchangeOpenRS,
                (loginState, preferences, isGrandExchangeOpen) -> {
                    if (!loginState.loggedIn
                            || loginState.displayName == null
                            || loginState.displayName.isBlank()
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

    private void consumeDumpStream(Response response) {
        try (Response resp = response) {
            BufferedSource source = resp.body().source();
            while (activeCall.get() != null) {
                byte frameType = source.readByte();
                if (frameType == HEARTBEAT_BYTE) {
                    continue;
                }
                if (frameType == MESSAGE_BYTE) {
                    int length = source.readInt();
                    if (length < 0) {
                        throw new IOException("invalid message length: " + length);
                    }
                    byte[] data = source.readByteArray(length);
                    if (data.length != length) {
                        throw new IOException("incomplete message payload");
                    }
                    handleDumpMessage(data);
                    continue;
                }
                throw new IOException("unknown dump frame type: " + frameType);
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

    private void handleDumpMessage(byte[] data) {
        Suggestion suggestion = Suggestion.fromMsgPack(ByteBuffer.wrap(data));
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
