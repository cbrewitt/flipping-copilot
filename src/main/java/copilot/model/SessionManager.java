package copilot.model;

import java.util.*;
import java.time.*;
import copilot.controller.*;
import com.google.gson.*;
import lombok.*;
import lombok.extern.slf4j.*;

import javax.inject.*;
import java.io.*;
import java.util.concurrent.*;

@Singleton
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SessionManager {

    public static final String SESSION_DATA_FILE_TEMPLATE = "%s_session_data.jsonl";

    private final PlayerLogin login;
    private final ScheduledExecutorService executor;
    private final Gson gson;

    private final Map<String, SessionData> cachedSessionData =  new HashMap<>();
    private final Map<String, File> displayNameToFile = new HashMap<>();

    private Instant lastSessionUpdateTime;

    public synchronized SessionData getCachedSessionData() {
        var sd = getSessionData(login.getPlayerDisplayName());
        return new SessionData(sd.startTime,  sd.durationMillis, sd.averageCash);
    }

    public synchronized void resetSession() {
        String displayName = login.getPlayerDisplayName();
        var sd = getSessionData(displayName);
        sd.startTime = (int) Instant.now().getEpochSecond(); sd.averageCash = 0; sd.durationMillis = 0;
        saveAsync(displayName);
    }

    public synchronized boolean updateSessionStats(boolean currentlyFlipping, long cashStack) {
        String displayName = login.getPlayerDisplayName();
        if (!currentlyFlipping || displayName == null) {
            lastSessionUpdateTime = null;
            return false;
        } else if (lastSessionUpdateTime == null) {
            lastSessionUpdateTime = Instant.now();
            return false;
        } else {
            var sd = getSessionData(displayName);
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
        executor.submit(() -> {
            File file = getFile(displayName);
            synchronized (file) {
                var data = cachedSessionData.computeIfAbsent(displayName, this::load);
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
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
        File file = getFile(displayName);
        if (!file.exists()) { return new SessionData((int) Instant.now().getEpochSecond(), 0 ,0); }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            SessionData sd =  gson.fromJson(reader, SessionData.class);
            if (sd != null) { return sd; }
        } catch (JsonSyntaxException | JsonIOException | IOException e) {
            log.warn("error loading session data json file {}", file, e);
        }
        return new SessionData((int) Instant.now().getEpochSecond(), 0 ,0);
    }

    private File getFile(String displayName) {
        return displayNameToFile.computeIfAbsent(displayName,
                (k) -> new File(Persistance.COPILOT_DIR, String.format(SESSION_DATA_FILE_TEMPLATE, Persistance.hashDisplayName(displayName))));
    }

    private SessionData getSessionData(String displayName) {
         return cachedSessionData.computeIfAbsent(displayName, this::load);
    }

    public synchronized void reset() { lastSessionUpdateTime = null; }
}
