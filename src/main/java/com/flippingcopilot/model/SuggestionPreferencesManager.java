package com.flippingcopilot.model;

import com.flippingcopilot.controller.Persistance;
import com.flippingcopilot.rs.AccountSuggestionPreferencesRS;
import com.google.gson.Gson;
import com.google.inject.name.Named;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
@Slf4j
public class SuggestionPreferencesManager {

    private static final int DEFAULT_TIMEFRAME = 5;

    public static final Path DEFAULT_PROFILE_PATH = Paths.get(Persistance.COPILOT_DIR.getPath(), "Default profile.profile.json");
    public static final String PROFILE_SUFFIX = ".profile.json";

    // dependencies
    private final Gson gson;
    private final ScheduledExecutorService executorService;
    private final AccountSuggestionPreferencesRS osrsAccountPreferences;

    // state
    private ProfileSuggestionPreferences cachedPreferences;
    private Path selectedProfile;
    private List<Path> availableProfiles;

    @Getter
    @Setter
    private volatile boolean sellOnlyMode = false;

    @Inject
    public SuggestionPreferencesManager(Gson gson,
                                        @Named("copilotExecutor") ScheduledExecutorService executorService,
                                        AccountSuggestionPreferencesRS osrsAccountPreferences) {
        this.gson = gson;
        this.executorService = executorService;
        this.osrsAccountPreferences = osrsAccountPreferences;
        loadAvailableProfiles();
        selectedProfile = DEFAULT_PROFILE_PATH;
        loadCurrentProfile();
        executorService.scheduleAtFixedRate(() -> {
            this.loadAvailableProfiles();
            this.loadCurrentProfile();
        }, 5L, 5L, TimeUnit.SECONDS);
    }

    public synchronized List<String> getAvailableProfiles() {
        return availableProfiles.stream().map(this::toDisplayName).collect(Collectors.toList());
    }

    public synchronized boolean isF2pOnlyMode() {
        return osrsAccountPreferences.get().isF2pOnlyMode();
    }

    public synchronized void setF2pOnlyMode(boolean f2pOnlyMode) {
        AccountSuggestionPreferences preferences = osrsAccountPreferences.get();
        preferences.setF2pOnlyMode(f2pOnlyMode);
        osrsAccountPreferences.updateAndPersist(preferences);
    }

    public synchronized void setTimeframe(int minutes) {
        AccountSuggestionPreferences preferences = osrsAccountPreferences.get();
        preferences.setTimeframe(minutes > 0 ? minutes : DEFAULT_TIMEFRAME);
        osrsAccountPreferences.updateAndPersist(preferences);
    }

    public synchronized int getTimeframe() {
        return osrsAccountPreferences.get().getTimeframe();
    }

    public synchronized void setRiskLevel(RiskLevel riskLevel) {
        AccountSuggestionPreferences preferences = osrsAccountPreferences.get();
        preferences.setRiskLevel(riskLevel == null ? RiskLevel.MEDIUM : riskLevel);
        osrsAccountPreferences.updateAndPersist(preferences);
    }

    public synchronized RiskLevel getRiskLevel() {
        return osrsAccountPreferences.get().getRiskLevel();
    }

    public synchronized void setReservedSlots(int reservedSlots) {
        int clamped = Math.max(0, Math.min(8, reservedSlots));
        AccountSuggestionPreferences preferences = osrsAccountPreferences.get();
        preferences.setReservedSlots(clamped);
        osrsAccountPreferences.updateAndPersist(preferences);
    }

    public synchronized int getReservedSlots() {
        return osrsAccountPreferences.get().getReservedSlots();
    }

    public synchronized void setReceiveDumpSuggestions(boolean receiveDumpSuggestions) {
        AccountSuggestionPreferences preferences = osrsAccountPreferences.get();
        preferences.setReceiveDumpSuggestions(receiveDumpSuggestions);
        osrsAccountPreferences.updateAndPersist(preferences);
    }

    public synchronized boolean isReceiveDumpSuggestions() {
        return osrsAccountPreferences.get().isReceiveDumpSuggestions();
    }

    public synchronized void setBlockedItems(Set<Integer> blockedItems) {
        List<Integer> toUnblock = cachedPreferences.blockedItemIds.stream().filter(i -> !blockedItems.contains(i)).collect(Collectors.toList());
        List<Integer> toBlock = blockedItems.stream().filter(i -> !cachedPreferences.blockedItemIds.contains(i)).collect(Collectors.toList());
        Consumer<ProfileSuggestionPreferences> update = (s) -> {
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
        Consumer<ProfileSuggestionPreferences> update = (s) -> {
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

    private synchronized void updateProfile(Path profile, Consumer<ProfileSuggestionPreferences> changes) {
        Path lockFile = Paths.get(profile+ ".lock");
        Path tmpFile = Paths.get(profile+ ".tmp");
        try {
            ProfileSuggestionPreferences preferences;
            if (Files.exists(profile)) {
                preferences = gson.fromJson(Files.readString(profile), ProfileSuggestionPreferences.class);
            } else {
                preferences = new ProfileSuggestionPreferences();
            }
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
                cachedPreferences = gson.fromJson(Files.readString(selectedProfile), ProfileSuggestionPreferences.class);
            } else {
                cachedPreferences = new ProfileSuggestionPreferences();
            }
        } catch (IOException e) {
            log.error("reading profile {}", selectedProfile, e);
        }
    }

    private synchronized void loadAvailableProfiles() {
        try (Stream<Path> paths = Files.list(Persistance.COPILOT_DIR.toPath())) {
            availableProfiles = paths
                    .filter(p -> p.toString().endsWith(PROFILE_SUFFIX))
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
