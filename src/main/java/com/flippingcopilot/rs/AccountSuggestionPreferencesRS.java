package com.flippingcopilot.rs;

import com.flippingcopilot.controller.Persistance;
import com.flippingcopilot.model.AccountSuggestionPreferences;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ScheduledExecutorService;

@Singleton
@Slf4j
public class AccountSuggestionPreferencesRS extends ReactiveStateImpl<AccountSuggestionPreferences> {

    private final Gson gson;
    private final ScheduledExecutorService executorService;
    private final ReactiveState<Long> accountHashState;

    @Inject
    public AccountSuggestionPreferencesRS(Gson gson,
                                          @Named("copilotExecutor") ScheduledExecutorService executorService,
                                          OsrsLoginRS osrsLoginRS) {
        super(new AccountSuggestionPreferences());
        this.gson = gson;
        this.executorService = executorService;
        this.accountHashState = ReactiveStateUtil.derive(osrsLoginRS, s -> s == null ? null : s.accountHash);
        this.accountHashState.registerListener(ah -> this.executorService.submit(() -> loadAccountPreferences(ah)));
        Long accountHash = accountHashState.get();
        this.executorService.submit(() -> loadAccountPreferences(accountHash));
    }

    public void updateAndPersist(AccountSuggestionPreferences preferences) {
        Long osrsAccountHash = accountHashState.get();
        if (osrsAccountHash == null) {
            log.warn("updateAndPersist called when not logged in to OSRS");
        } else {
            forceSet(preferences);
            executorService.submit(() -> persist(preferences, osrsAccountHash));
        }
    }

    private synchronized void persist(AccountSuggestionPreferences preferences, Long ah) {
        Path file = accountPreferencesPath(ah);
        Path tmpFile = Paths.get(file + ".tmp");
        try {
            String toWrite = gson.toJson(preferences);
            try {
                Files.writeString(tmpFile, toWrite);
                Files.move(tmpFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(tmpFile);
            }
        } catch (IOException e) {
            log.warn("error saving account preferences json file {}", file, e);
        }
    }

    private void loadAccountPreferences(Long accountHash) {
        AccountSuggestionPreferences preferences = new AccountSuggestionPreferences();
        if (accountHash != null) {
            Path file = accountPreferencesPath(accountHash);
            try {
                if (Files.exists(file)) {
                    preferences = gson.fromJson(Files.readString(file), AccountSuggestionPreferences.class);
                }
            } catch (IOException e) {
                log.warn("error loading account preferences json file {}", file, e);
            }
        }
        set(preferences);
    }

    private Path accountPreferencesPath(Long accountHash) {
        return Paths.get(Persistance.COPILOT_DIR.getPath(), "acc_" + accountHash + "_prefs.json");
    }
}
