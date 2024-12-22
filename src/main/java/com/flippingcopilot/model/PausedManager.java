package com.flippingcopilot.model;


import com.flippingcopilot.controller.Persistance;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.HashMap;
import java.util.Map;

@Singleton
@Slf4j
public class PausedManager {

    private static final String PAUSED_FILE_TEMPLATE = "acc_%d_paused.json";

    // dependencies
    private final OsrsLoginManager osrsLoginManager;

    // state
    private final Map<Long, Boolean> cachedPaused = new HashMap<>();

    @Inject
    public PausedManager(OsrsLoginManager osrsLoginManager) {
        this.osrsLoginManager = osrsLoginManager;
    }

    public synchronized boolean isPaused() {
        Long accountHash = osrsLoginManager.getAccountHash();
        return cachedPaused.computeIfAbsent(accountHash, (k) -> {
            File file = getFile(k);
            try {
                String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                return text.contains("true");
            }catch (NoSuchFileException e ){
                return false;
            } catch (JsonSyntaxException | JsonIOException | IOException e) {
                log.warn("error loading stored paused state file {}", file, e);
                return false;
            }
        });
    }

    public synchronized void setPaused(boolean isPaused) {
        Long accountHash = osrsLoginManager.getAccountHash();
        String text = isPaused ? "{\"isPaused\":true}" : "{\"isPaused\":false}";
        File file = getFile(accountHash);
        cachedPaused.put(accountHash, isPaused);
        try {
            Files.write(file.toPath(), text.getBytes());
        } catch(IOException e) {
            log.warn("error storing paused.json file {}", file, e);
        }
    }

    private File getFile(Long accountHash) {
        return new File(Persistance.PARENT_DIRECTORY, String.format(PAUSED_FILE_TEMPLATE, accountHash));
    }
}