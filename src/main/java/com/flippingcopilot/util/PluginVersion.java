package com.flippingcopilot.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Slf4j
public final class PluginVersion {

    private static final String VERSION_PROPERTIES = "flippingcopilot.properties";
    private static final String VERSION_KEY = "pluginVersion";
    private static final String UNKNOWN_VERSION = "unknown";

    private static volatile String cachedVersion;

    private PluginVersion() {
    }

    public static String get() {
        String version = cachedVersion;
        if (version != null) {
            return version;
        }

        synchronized (PluginVersion.class) {
            if (cachedVersion == null) {
                cachedVersion = loadVersion();
            }
            return cachedVersion;
        }
    }

    private static String loadVersion() {
        Properties properties = new Properties();

        try (InputStream stream = PluginVersion.class.getClassLoader().getResourceAsStream(VERSION_PROPERTIES)) {
            if (stream == null) {
                log.warn("plugin version resource {} not found", VERSION_PROPERTIES);
                return UNKNOWN_VERSION;
            }

            properties.load(stream);
            return properties.getProperty(VERSION_KEY, UNKNOWN_VERSION);
        } catch (IOException e) {
            log.warn("error loading plugin version from {}", VERSION_PROPERTIES, e);
            return UNKNOWN_VERSION;
        }
    }
}
