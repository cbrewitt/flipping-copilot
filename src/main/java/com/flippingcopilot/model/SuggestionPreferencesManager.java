package com.flippingcopilot.model;

import com.flippingcopilot.controller.Persistance;
import com.google.gson.Gson;
import com.google.inject.name.Named;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Singleton
@Slf4j
public class SuggestionPreferencesManager {


    private static final Path DEPRECATED_PREFERENCES_FILE = Paths.get(Persistance.COPILOT_DIR.getPath(),"shared_preferences.json");

    public static final Path DEFAULT_PROFILE_PATH = Paths.get(Persistance.COPILOT_DIR.getPath(), "Default profile.profile.json");
    public static final String PROFILE_SUFFIX = ".profile.json";
    // dependencies
    private final Gson gson;
    private final ScheduledExecutorService executorService;

    // state
    private SuggestionPreferences cachedPreferences;
    private Path selectedProfile;
    private List<Path> availableProfiles;

    @Getter
    @Setter
    private volatile boolean sellOnlyMode = false;
    private int timeFrame = 5;

    @Inject
    public SuggestionPreferencesManager(Gson gson, @Named("copilotExecutor") ScheduledExecutorService executorService) {
        this.gson = gson;
        this.executorService = executorService;
        init();
        loadAvailableProfiles();
        selectedProfile = DEFAULT_PROFILE_PATH;
        loadCurrentProfile();
        executorService.scheduleAtFixedRate(() -> {
            this.loadAvailableProfiles();
            this.loadCurrentProfile();
        }, 5L, 5L, TimeUnit.SECONDS);
    }

    private void init() {
        try {
            if (!Files.exists(DEFAULT_PROFILE_PATH)) {
                if(Files.exists(DEPRECATED_PREFERENCES_FILE)) {
                    Files.move(DEPRECATED_PREFERENCES_FILE, DEFAULT_PROFILE_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } else {
                    Files.writeString(DEFAULT_PROFILE_PATH,"{}");
                }
            }
        } catch (IOException e) {
            log.error("failed to init profiles {}", DEPRECATED_PREFERENCES_FILE, e);
        }
    }

    public synchronized List<String> getAvailableProfiles() {
        return availableProfiles.stream().map(this::toDisplayName).collect(Collectors.toList());
    }

    public synchronized SuggestionPreferences getPreferences() {
        return cachedPreferences;
    }

    public synchronized boolean isF2pOnlyMode() {
        return cachedPreferences.f2pOnlyMode;
    }

    public synchronized void setF2pOnlyMode(boolean f2pOnlyMode) {
        Consumer<SuggestionPreferences> update = (s) -> {
            s.setF2pOnlyMode(f2pOnlyMode);
        };
        update.accept(cachedPreferences);
        executorService.submit(() -> updateProfile(selectedProfile, update));
    }

    public synchronized void setTimeframe(int minutes) {
        timeFrame = minutes;
    }

    public synchronized int getTimeframe() {
        return timeFrame;
    }

    public synchronized void setBlockedItems(Set<Integer> blockedItems) {
        List<Integer> toUnblock = cachedPreferences.blockedItemIds.stream().filter(i -> !blockedItems.contains(i)).collect(Collectors.toList());
        List<Integer> toBlock = blockedItems.stream().filter(i -> !cachedPreferences.blockedItemIds.contains(i)).collect(Collectors.toList());
        Consumer<SuggestionPreferences> update = (s) -> {
            s.blockedItemIds.removeIf(toUnblock::contains);
            toBlock.forEach(i -> {
                if(!s.blockedItemIds.contains(i)) {
                    s.blockedItemIds.add(i);
                }
            });
        };
        log.debug("blocking {}, unblocking {}", toBlock, toUnblock);
        update.accept(cachedPreferences);
        executorService.submit(() -> updateProfile(selectedProfile, update));
    }

    public synchronized void blockItem(int itemId) {
        Consumer<SuggestionPreferences> update = (s) -> {
            if(!s.blockedItemIds.contains(itemId)) {
                s.blockedItemIds.add(itemId);
            }
        };
        update.accept(cachedPreferences);
        executorService.submit(() -> updateProfile(selectedProfile, update));
        log.debug("blocked item {}", itemId);
    }

    public synchronized List<Integer> blockedItems() {
        return cachedPreferences.getBlockedItemIds();
    }

    public synchronized boolean isDefaultProfileSelected() {
        return DEFAULT_PROFILE_PATH.equals(selectedProfile);
    }

    public synchronized String getCurrentProfile() {
        return toDisplayName(selectedProfile);
    }

    public synchronized void setCurrentProfile(String name) {
        selectedProfile = fromDisplayName(name);
        loadCurrentProfile();
    }

    public synchronized void addProfile(String name) throws IOException {
        Path p = Paths.get(Persistance.COPILOT_DIR.toString(), name + PROFILE_SUFFIX);
        createProfileFile(p);
        availableProfiles.add(p);
        selectedProfile = p;
        loadCurrentProfile();
    }

    private synchronized void updateProfile(Path profile, Consumer<SuggestionPreferences> changes) {
        Path lockFile = Paths.get(profile+ ".lock");
        Path tmpFile = Paths.get(profile+ ".tmp");
        try {
            SuggestionPreferences preferences = gson.fromJson(Files.readString(profile), SuggestionPreferences.class);
            changes.accept(preferences);
            String toWrite = gson.toJson(preferences);
            // acquire <file>.lock
            try (FileChannel lockChannel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE); FileLock l = lockChannel.lock()) {
                // write as .tmp file then re-name
                Files.writeString(tmpFile,toWrite );
                Files.move(tmpFile, profile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(lockFile);
                Files.deleteIfExists(tmpFile);
            }
        } catch (IOException e) {
            log.warn("error saving preferences json file {}", profile, e);
        }
    }

    private synchronized void loadCurrentProfile() {
        try {
            if (Files.exists(selectedProfile)) {
                cachedPreferences = gson.fromJson(Files.readString(selectedProfile), SuggestionPreferences.class);
            } else {
                cachedPreferences = new SuggestionPreferences();
            }
        } catch (IOException e) {
            log.error("reading profile {}", selectedProfile, e);
        }
    }

    private synchronized void loadAvailableProfiles() {
        try {
            availableProfiles = Files.list(Persistance.COPILOT_DIR.toPath()).filter(p -> p.toString().endsWith(PROFILE_SUFFIX))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            availableProfiles = new ArrayList<>();
            availableProfiles.add(DEFAULT_PROFILE_PATH);
            log.error("loading available profiles", e);
        }
    }

    private String toDisplayName(Path p ) {
        return p == null ? null : p.getFileName().toString().replaceAll("\\.profile\\.json$", "");
    }

    private Path fromDisplayName(String name) {
        return Paths.get(Persistance.COPILOT_DIR.toString(), name + PROFILE_SUFFIX);
    }

    public synchronized void deleteSelectedProfile() throws IOException {
        Path lockFile = Paths.get(selectedProfile+ ".lock");
        try (FileChannel lockChannel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE); FileLock l = lockChannel.lock()) {
            Files.delete(selectedProfile);
        } finally {
            Files.deleteIfExists(lockFile);
        }
        selectedProfile = DEFAULT_PROFILE_PATH;
        loadCurrentProfile();
        loadAvailableProfiles();
    }

    private void createProfileFile(Path profile) throws IOException {
        Path lockFile = Paths.get(profile + ".lock");
        Path tmpFile = Paths.get(profile + ".tmp");
        String toWrite = "{}";
        // acquire <file>.lock
        try (FileChannel lockChannel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE); FileLock l = lockChannel.lock()) {
            // write as .tmp file then re-name
            Files.writeString(tmpFile, toWrite);
            Files.move(tmpFile, profile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(lockFile);
        }
    }
}
