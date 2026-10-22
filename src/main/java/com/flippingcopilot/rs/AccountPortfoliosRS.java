package com.flippingcopilot.rs;

import com.flippingcopilot.controller.Persistance;
import com.flippingcopilot.model.AccountPortfolioSnapshot;
import com.flippingcopilot.model.PortfolioState;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

// Latest portfolio snapshot of each account by account id, shared between clients through the plugin directory.
@Slf4j
@Singleton
public class AccountPortfoliosRS extends ReactiveStateImpl<Map<Integer, AccountPortfolioSnapshot>> {

    public static final String PORTFOLIO_SNAPSHOT_FILE_TEMPLATE = "%s_portfolio_snapshot.json";

    private final Persistance persistance;
    private final Gson gson;
    private final ExecutorService executorService;

    private final AtomicLong saveSeq = new AtomicLong();
    private final Map<String, Long> lastWrittenSeq = new HashMap<>();
    private final Map<String, Long> readModifiedTimes = new HashMap<>();

    @Inject
    public AccountPortfoliosRS(Persistance persistance, Gson gson, ExecutorService executorService) {
        super(Collections.emptyMap());
        this.persistance = persistance;
        this.gson = gson;
        this.executorService = executorService;
    }

    public void saveAsync(String displayName, int accountId, PortfolioState state) {
        AccountPortfolioSnapshot snapshot = AccountPortfolioSnapshot.of(displayName, accountId, Instant.now().getEpochSecond(), state);
        long seq = saveSeq.incrementAndGet();
        executorService.submit(() -> {
            Filepath file = file(displayName);
            synchronized (lastWrittenSeq) {
                // don't let an older snapshot overwrite a newer one
                if (lastWrittenSeq.getOrDefault(displayName, 0L) > seq) {
                    return;
                }
                Filepath tmp = null;
                try {
                    // atomic so other clients never read a partial file
                    tmp = file.getParent().createTempFile("portfolio-snapshot", ".tmp");
                    tmp.write(gson.toJson(snapshot));
                    tmp.moveTo(file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    lastWrittenSeq.put(displayName, seq);
                } catch (IOException e) {
                    log.warn("error saving portfolio snapshot to file {}", file, e);
                    deleteQuietly(tmp);
                }
            }
        });
    }

    // file IO, call off the client and Swing threads
    public boolean reload(Map<String, Integer> displayNameToAccountId) {
        Map<Integer, AccountPortfolioSnapshot> current = get();
        Map<Integer, AccountPortfolioSnapshot> next = new HashMap<>();
        synchronized (readModifiedTimes) {
            readModifiedTimes.keySet().retainAll(displayNameToAccountId.keySet());
            displayNameToAccountId.forEach((displayName, accountId) -> {
                AccountPortfolioSnapshot s = read(displayName, accountId, current.get(accountId));
                if (s != null) {
                    next.put(accountId, s);
                }
            });
        }
        boolean changed = !next.equals(current);
        set(Collections.unmodifiableMap(next));
        return changed;
    }

    private AccountPortfolioSnapshot read(String displayName, int accountId, AccountPortfolioSnapshot previous) {
        Filepath file = file(displayName);
        try {
            if (!file.exists()) {
                readModifiedTimes.remove(displayName);
                return null;
            }
            long modified = file.getLastModifiedTime().toMillis();
            if (previous != null && Objects.equals(readModifiedTimes.get(displayName), modified)) {
                return previous;
            }
            try (BufferedReader reader = file.openBufferedReader()) {
                AccountPortfolioSnapshot s = gson.fromJson(reader, AccountPortfolioSnapshot.class);
                // display names can move between accounts
                if (s == null || s.accountId != accountId) {
                    return null;
                }
                if (s.items == null) {
                    s.items = new ArrayList<>();
                }
                readModifiedTimes.put(displayName, modified);
                return s;
            }
        } catch (JsonSyntaxException | JsonIOException | IOException e) {
            log.debug("error reading portfolio snapshot file {}", file, e);
            return previous;
        }
    }

    private static void deleteQuietly(Filepath file) {
        if (file == null) {
            return;
        }
        try {
            file.deleteIfExists();
        } catch (IOException e) {
            log.debug("failed to delete temp file {}", file, e);
        }
    }

    private Filepath file(String displayName) {
        return persistance.getDirectory().joinSegment(String.format(PORTFOLIO_SNAPSHOT_FILE_TEMPLATE, Persistance.hashDisplayName(displayName)));
    }
}
