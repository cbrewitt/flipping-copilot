package com.flippingcopilot.model;

import com.flippingcopilot.controller.Persistance;
import com.google.gson.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

@Singleton
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SessionManager {

    public static final String SESSION_DATA_FILE_TEMPLATE = "%s_session_data.jsonl";
    // other accounts only count while another client is updating their session file
    public static final long ACTIVE_SESSION_MAX_AGE_MILLIS = 30 * 60 * 1000;

    private final Persistance persistance;
    private final OsrsLoginManager osrsLoginManager;
    private final ScheduledExecutorService executorService;
    private final Gson gson;

    private final Map<String, SessionData> cachedSessionData =  new HashMap<>();
    private final Map<String, Filepath> displayNameToFile = new HashMap<>();

    private Instant lastSessionUpdateTime;

    // kept out of cachedSessionData so this client never writes another client's session back to disk
    private final Map<String, FileSessionData> otherAccountFileCache = new HashMap<>();
    private volatile Map<Integer, SessionData> otherAccountSessions = Collections.emptyMap();

    public synchronized SessionData getCachedSessionData() {
        SessionData sd = getSessionData(osrsLoginManager.getPlayerDisplayName());
        return new SessionData(sd.startTime,  sd.durationMillis, sd.averageCash);
    }

    public synchronized void resetSession() {
        String displayName = osrsLoginManager.getPlayerDisplayName();
        SessionData sd = getSessionData(displayName);
        sd.startTime = (int) Instant.now().getEpochSecond();
        sd.averageCash = 0;
        sd.durationMillis = 0;
        saveAsync(displayName);
    }

    public synchronized boolean updateSessionStats(boolean currentlyFlipping, long cashStack) {
        String displayName = osrsLoginManager.getPlayerDisplayName();
        if (!currentlyFlipping || displayName == null) {
            lastSessionUpdateTime = null;
            return false;
        } else if (lastSessionUpdateTime == null) {
            lastSessionUpdateTime = Instant.now();
            return false;
        } else {
            SessionData sd = getSessionData(displayName);
            Instant now = Instant.now();
            long duration = Duration.between(lastSessionUpdateTime, now).toMillis();
            long newAverageCashStack = (cashStack * duration + sd.durationMillis * sd.averageCash) / (sd.durationMillis + duration);
            sd.durationMillis = sd.durationMillis + duration;
            lastSessionUpdateTime = now;
            sd.averageCash = newAverageCashStack;
            saveAsync(displayName);
            return true;
        }
    }

    private void saveAsync(String displayName) {
        executorService.submit(() -> {
            Filepath file = getFile(displayName);
            synchronized (file) {
                SessionData data = cachedSessionData.computeIfAbsent(displayName, this::load);
                try (BufferedWriter writer = file.openBufferedWriter()) {
                    String json = gson.toJson(data);
                    writer.write(json);
                    writer.newLine();
                } catch (IOException e) {
                    log.warn("error storing session data to file {}", file, e);
                }
            }
        });
    }

     private SessionData load(String displayName) {
        Filepath file = getFile(displayName);
        if (!file.exists()) {
            return new SessionData((int) Instant.now().getEpochSecond(), 0 ,0);
        }
        try (BufferedReader reader = file.openBufferedReader()) {
            SessionData sd =  gson.fromJson(reader, SessionData.class);
            if (sd != null) {
                return sd;
            }
        } catch (JsonSyntaxException | JsonIOException | IOException e) {
            log.warn("error loading session data json file {}", file, e);
        }
        return new SessionData((int) Instant.now().getEpochSecond(), 0 ,0);
    }

    private Filepath getFile(String displayName) {
        return displayNameToFile.computeIfAbsent(displayName,
                (k) -> persistance.getDirectory().joinSegment(String.format(SESSION_DATA_FILE_TEMPLATE, Persistance.hashDisplayName(displayName))));
    }

    private SessionData getSessionData(String displayName) {
         return cachedSessionData.computeIfAbsent(displayName, this::load);
    }

    public synchronized void reset() {
        lastSessionUpdateTime = null;
    }

    public Map<Integer, SessionData> getAccountSessions(Integer liveAccountId) {
        Map<Integer, SessionData> sessions = new HashMap<>(otherAccountSessions);
        if (liveAccountId != null && liveAccountId != -1) {
            sessions.put(liveAccountId, getCachedSessionData());
        }
        return sessions;
    }

    // file IO, call off the client and Swing threads
    public boolean refreshOtherAccountSessions(Map<String, Integer> displayNameToAccountId, String liveDisplayName) {
        long now = System.currentTimeMillis();
        Map<Integer, SessionData> sessions = new HashMap<>();
        synchronized (otherAccountFileCache) {
            otherAccountFileCache.keySet().retainAll(displayNameToAccountId.keySet());
            displayNameToAccountId.forEach((displayName, accountId) -> {
                if (displayName.equals(liveDisplayName)) {
                    return;
                }
                SessionData sd = readActiveSession(displayName, now);
                if (sd != null) {
                    sessions.put(accountId, sd);
                }
            });
        }
        boolean changed = !sameSessions(sessions, otherAccountSessions);
        otherAccountSessions = Collections.unmodifiableMap(sessions);
        return changed;
    }

    private SessionData readActiveSession(String displayName, long now) {
        Filepath file = persistance.getDirectory().joinSegment(String.format(SESSION_DATA_FILE_TEMPLATE, Persistance.hashDisplayName(displayName)));
        FileSessionData cached = otherAccountFileCache.get(displayName);
        try {
            if (!file.exists()) {
                otherAccountFileCache.remove(displayName);
                return null;
            }
            long modified = file.getLastModifiedTime().toMillis();
            if (now - modified > ACTIVE_SESSION_MAX_AGE_MILLIS) {
                return null;
            }
            if (cached == null || cached.modified != modified) {
                try (BufferedReader reader = file.openBufferedReader()) {
                    SessionData sd = gson.fromJson(reader, SessionData.class);
                    if (sd != null) {
                        cached = new FileSessionData(modified, sd);
                        otherAccountFileCache.put(displayName, cached);
                    }
                }
            }
        } catch (JsonSyntaxException | JsonIOException | IOException e) {
            // may be mid-write by another client
            log.debug("error reading session data file {}", file, e);
        }
        return cached == null ? null : new SessionData(cached.data.startTime, cached.data.durationMillis, cached.data.averageCash);
    }

    private static boolean sameSessions(Map<Integer, SessionData> a, Map<Integer, SessionData> b) {
        if (!a.keySet().equals(b.keySet())) {
            return false;
        }
        for (Map.Entry<Integer, SessionData> e : a.entrySet()) {
            SessionData other = b.get(e.getKey());
            if (e.getValue().startTime != other.startTime || e.getValue().durationMillis != other.durationMillis) {
                return false;
            }
        }
        return true;
    }

    private static class FileSessionData {
        final long modified;
        final SessionData data;

        FileSessionData(long modified, SessionData data) {
            this.modified = modified;
            this.data = data;
        }
    }
}
