package com.flippingcopilot.model;


import com.flippingcopilot.controller.Persistance;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

@Singleton
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PausedManager {

    private static final String PAUSED_FILE_TEMPLATE = "acc_%d_paused.json";

    // dependencies
    private final OsrsLoginManager osrsLoginManager;
    private final ScheduledExecutorService executorService;

    // state
    private final Map<Long, Boolean> cachedPaused = new HashMap<>();
    private final Map<Long, File> accountHashToFile = new HashMap<>();

    public synchronized boolean isPaused() {
        Long accountHash = osrsLoginManager.getAccountHash();
        return cachedPaused.computeIfAbsent(accountHash, (k) -> {
            File file = getFile(k);
            try {
                String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                return text.contains("true");
            } catch (NoSuchFileException e ){
                return false;
            } catch (JsonSyntaxException | JsonIOException | IOException e) {
                log.warn("error loading stored paused state file {}", file, e);
                return false;
            }
        });
    }

    public synchronized void setPaused(boolean isPaused) {
        Long accountHash = osrsLoginManager.getAccountHash();
        cachedPaused.put(accountHash, isPaused);
        saveAsync(accountHash);
    }

    private void saveAsync(Long accountHash) {
        executorService.submit(() -> {
            File file = getFile(accountHash);
            synchronized (file) {
                boolean isPaused = cachedPaused.getOrDefault(accountHash, false);
                String text = isPaused ? "{\"isPaused\":true}" : "{\"isPaused\":false}";
                try {
                    Files.write(file.toPath(), text.getBytes());
                } catch (IOException e) {
                    log.warn("error storing paused.json file {}", file, e);
                }
            }
        });
    }

    private File getFile(Long accountHash) {
        return accountHashToFile.computeIfAbsent(accountHash,
                (k) -> new File(Persistance.COPILOT_DIR, String.format(PAUSED_FILE_TEMPLATE, accountHash)));
    }
}