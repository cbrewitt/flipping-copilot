package com.flippingcopilot.rs;

import com.flippingcopilot.controller.Persistance;
import com.flippingcopilot.model.AccountSuggestionPreferences;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.ScheduledExecutorService;

@Singleton
@Slf4j
public class AccountSuggestionPreferencesRS extends ReactiveStateImpl<AccountSuggestionPreferences> {

    private final Persistance persistance;
    private final Gson gson;
    private final ScheduledExecutorService executorService;
    private final ReactiveState<Long> accountHashState;

    @Inject
    public AccountSuggestionPreferencesRS(Persistance persistance, Gson gson,
                                          @Named("copilotExecutor") ScheduledExecutorService executorService,
                                          OsrsLoginRS osrsLoginRS) {
        super(new AccountSuggestionPreferences());
        this.persistance = persistance;
        this.gson = gson;
        this.executorService = executorService;
        this.accountHashState = ReactiveStateUtil.derive(osrsLoginRS, s -> s == null ? null : s.accountHash);
        this.accountHashState.registerListener(ah -> this.executorService.submit(() -> loadAccountPreferences(ah)));
        Long accountHash = accountHashState.get();
        this.executorService.submit(() -> loadAccountPreferences(accountHash));
    }

    /**
     * True once an OSRS account hash is known. The hash is retained after logout, so preferences
     * remain editable and persistable for the last account seen in this client run.
     */
    public boolean hasAccount() {
        return accountHashState.get() != null;
    }

    public void updateAndPersist(AccountSuggestionPreferences preferences) {
        Long osrsAccountHash = accountHashState.get();
        if (osrsAccountHash == null) {
            // Callers must gate on hasAccount(); reaching here drops the change on the floor.
            log.error("updateAndPersist called before any OSRS account hash is known, discarding {}", preferences);
        } else {
            forceSet(preferences);
            executorService.submit(() -> persist(preferences, osrsAccountHash));
        }
    }

    private synchronized void persist(AccountSuggestionPreferences preferences, Long ah) {
        Filepath file = accountPreferencesPath(ah);
        Filepath tmpFile = file.getParent().joinSegment(file.getFileName() + ".tmp");
        try {
            String toWrite = gson.toJson(preferences);
            try {
                tmpFile.write(toWrite);
                tmpFile.moveTo(file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                tmpFile.deleteIfExists();
            }
        } catch (IOException e) {
            log.warn("error saving account preferences json file {}", file, e);
        }
    }

    private void loadAccountPreferences(Long accountHash) {
        AccountSuggestionPreferences preferences = new AccountSuggestionPreferences();
        if (accountHash != null) {
            Filepath file = accountPreferencesPath(accountHash);
            try {
                if (file.exists()) {
                    preferences = gson.fromJson(Persistance.readString(file), AccountSuggestionPreferences.class);
                }
            } catch (IOException e) {
                log.warn("error loading account preferences json file {}", file, e);
            }
        }
        set(preferences);
    }

    private Filepath accountPreferencesPath(Long accountHash) {
        return persistance.getDirectory().joinSegment("acc_" + accountHash + "_prefs.json");
    }
}
