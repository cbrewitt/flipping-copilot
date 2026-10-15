package com.flippingcopilot.manager;

import com.flippingcopilot.controller.Persistance;
import com.flippingcopilot.ui.graph.model.Config;
import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.nio.file.NoSuchFileException;
import java.util.concurrent.ScheduledExecutorService;


@Slf4j
@Singleton
public class PriceGraphConfigManager {

    public static final String JSON_FILE = "price_graph_config.json";

    private final Filepath file;

    // dependencies
    private final Gson gson;
    private final ScheduledExecutorService executorService;

    @Inject
    public PriceGraphConfigManager(Persistance persistance, Gson gson, ScheduledExecutorService executorService) {
        this.file = persistance.getDirectory().joinSegment(JSON_FILE);
        this.gson = gson;
        this.executorService = executorService;
    }

    // state
    private Config cachedConfig;

    public synchronized Config getConfig() {
        if(cachedConfig != null) {
            return cachedConfig;
        }
        cachedConfig = load();
        return cachedConfig;
    }

    public synchronized void setConfig(Config config) {
        if (config == null) {
            return;
        }
        cachedConfig = config;
        saveAsync();
    }

    public void saveAsync() {
        executorService.submit(() -> {
            synchronized (file) {
                Config config = getConfig();
                try {
                    String json = gson.toJson(config);
                    file.write(json);
                } catch (IOException e) {
                    log.warn("error saving graph config {}", e.getMessage(), e);
                }
            }
        });
    }

    public Config load() {
        try (BufferedReader reader = file.openBufferedReader()) {
            return gson.fromJson(reader, Config.class);
        } catch (NoSuchFileException ignored) {
            return new Config();
        } catch (JsonSyntaxException | JsonIOException | IOException e) {
            log.warn("error loading saved graph config json file {}", file, e);
            return new Config();
        }
    }
}
