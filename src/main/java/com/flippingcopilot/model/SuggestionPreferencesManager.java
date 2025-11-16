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
    private final OsrsLoginManager osrsLoginManager;

    // state
    private AccountSuggestionPreferences accountSuggestionPreferences = new AccountSuggestionPreferences();
    private ProfileSuggestionPreferences cachedPreferences;
    private Path selectedProfile;
    private List<Path> availableProfiles;

    @Getter
    @Setter
    private volatile boolean sellOnlyMode = false;

    @Inject
    public SuggestionPreferencesManager(Gson gson, @Named("copilotExecutor") ScheduledExecutorService executorService, OsrsLoginManager osrsLoginManager) {
        this.gson = gson;
        this.executorService = executorService;
        this.osrsLoginManager = osrsLoginManager;
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
        return accountSuggestionPreferences.isF2pOnlyMode();
    }

    public synchronized void setF2pOnlyMode(boolean f2pOnlyMode) {
        accountSuggestionPreferences.setF2pOnlyMode(f2pOnlyMode);
        executorService.submit(() -> updateAccountPreferences(accountSuggestionPreferences));
    }

    public synchronized void setTimeframe(int minutes) {
        accountSuggestionPreferences.setTimeframe(minutes > 0 ? minutes : DEFAULT_TIMEFRAME);
        executorService.submit(() -> updateAccountPreferences(accountSuggestionPreferences));
    }

    public synchronized int getTimeframe() {
        return accountSuggestionPreferences.getTimeframe();
    }

    public synchronized void setRiskLevel(RiskLevel riskLevel) {
        accountSuggestionPreferences.setRiskLevel(riskLevel == null ? RiskLevel.MEDIUM : riskLevel);
        executorService.submit(() -> updateAccountPreferences(accountSuggestionPreferences));
    }

    public synchronized RiskLevel getRiskLevel() {
        return accountSuggestionPreferences.getRiskLevel();
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

    public synchronized void loadAccountPreferences() {
        if(osrsLoginManager.getAccountHash() == null){
            return;
        }
        Path f = accountPreferencesPath();
        try {
            if(Files.exists(f)) {
                accountSuggestionPreferences = gson.fromJson(Files.readString(f), AccountSuggestionPreferences.class);
            } else {
                accountSuggestionPreferences = new AccountSuggestionPreferences();
            }
        } catch (IOException e) {
            log.warn("error loading account preferences json file {}", f, e);
        }
    }

    private synchronized void updateAccountPreferences(AccountSuggestionPreferences ap) {
        Long osrsAccountHash = osrsLoginManager.getAccountHash();
        if (osrsAccountHash == null) {
            return;
        }
        Path f = accountPreferencesPath();
        Path tmpFile = Paths.get(f + ".tmp");
        try {
            String toWrite = gson.toJson(ap);
            // acquire <file>.lock
            try {
                Files.writeString(tmpFile,toWrite );
                Files.move(tmpFile, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(tmpFile);
            }
        } catch (IOException e) {
            log.warn("error saving preferences json file {}", f, e);
        }
    }

    private Path accountPreferencesPath() {
        return Paths.get(Persistance.COPILOT_DIR.getPath(), "acc_" + osrsLoginManager.getAccountHash() + "_prefs.json");
    }
}
