package copilot.rs;

import copilot.controller.*;
import copilot.model.*;
import com.google.gson.*;
import com.google.inject.*;
import com.google.inject.name.*;
import lombok.extern.slf4j.*;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;

@Singleton
@Slf4j
public class AccountPreferencesRS extends ReactiveStateImpl<AccountPreferences> {

    private final Gson gson;
    private final ScheduledExecutorService executor;
    private final ReactiveState<Long> accountHashState;

    @Inject
    public AccountPreferencesRS(Gson gson,
                                          @Named("copilotExecutor") ScheduledExecutorService executor,
                                          GameLogin osrsLoginRS) {
        super(new AccountPreferences());
        this.gson = gson; this.executor = executor;
        accountHashState = ReactiveStateUtil.derive(osrsLoginRS, s -> s == null ? null : s.accountHash);
        accountHashState.registerListener(ah -> this.executor.submit(() -> loadAccountPreferences(ah)));
        Long accountHash = accountHashState.get();
        this.executor.submit(() -> loadAccountPreferences(accountHash));
    }

    /**
     * True once an OSRS account hash is known. The hash is retained after logout, so preferences
     * remain editable and persistable for the last account seen in this client run.
     */
    public boolean hasAccount() { return accountHashState.get() != null; }

    public void updateAndPersist(AccountPreferences preferences) {
        Long osrsAccountHash = accountHashState.get();
        if (osrsAccountHash == null) {
            // Callers must gate on hasAccount(); reaching here drops the change on the floor.
            log.error("updateAndPersist called before any OSRS account hash is known, discarding {}", preferences);
        } else {
            forceSet(preferences);
            executor.submit(() -> persist(preferences, osrsAccountHash));
        }
    }

    private synchronized void persist(AccountPreferences preferences, Long ah) {
        Path file = accountPreferencesPath(ah), tmpFile = Paths.get(file + ".tmp");
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
        var preferences = new AccountPreferences();
        if (accountHash != null) {
            Path file = accountPreferencesPath(accountHash);
            try {
                if (Files.exists(file)) {
                    preferences = gson.fromJson(Files.readString(file), AccountPreferences.class);
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
