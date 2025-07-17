package com.flippingcopilot.util;

import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ClientThreadUtil {

    private final ClientThread clientThread;
    private final Client client;

    /**
     * Force execution in the client thread in a synchronous manner from the point of view of the caller. Using this is
     * a bit of a code smell so avoid it where possible.
     */
    public <T> T executeInClientThread(Supplier<T> s) {
        if(!client.isClientThread()) {
            CompletableFuture<T> future = new CompletableFuture<>();
            clientThread.invoke(() -> {
                try {
                    future.complete(s.get());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            try {
                return future.get(); // Blocks until result is available
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Failed to execute search on client thread", e);
            }
        } else {
            return s.get();
        }
    }
}
