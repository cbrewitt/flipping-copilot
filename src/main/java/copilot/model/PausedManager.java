package copilot.model;

import java.util.*;
import java.nio.file.*;
import com.google.gson.*;

import copilot.controller.*;
import lombok.*;
import lombok.extern.slf4j.*;

import javax.inject.*;
import java.io.*;
import java.nio.charset.*;
import java.util.concurrent.*;

@Singleton
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PausedManager {

    private static final String PAUSED_FILE_TEMPLATE = "acc_%d_paused.json";

    // dependencies
    private final PlayerLogin login;
    private final ScheduledExecutorService executor;

    // state
    private final Map<Long, Boolean> cachedPaused = new HashMap<>();
    private final Map<Long, File> accountHashToFile = new HashMap<>();

    public synchronized boolean isPaused() {
        Long accountHash = login.getAccountHash();
        return cachedPaused.computeIfAbsent(accountHash, (k) -> {
            File file = getFile(k);
            try {
                String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                return text.contains("true");
            } catch (NoSuchFileException e ){
                return false;
            } catch (JsonSyntaxException | JsonIOException | IOException e) {
                log.warn("error loading stored paused state file {}", file, e);
                return false;
            }
        });
    }

    public synchronized void setPaused(boolean isPaused) {
        Long accountHash = login.getAccountHash();
        cachedPaused.put(accountHash, isPaused);
        saveAsync(accountHash);
    }

    private void saveAsync(Long accountHash) {
        executor.submit(() -> {
            File file = getFile(accountHash);
            synchronized (file) {
                boolean isPaused = cachedPaused.getOrDefault(accountHash, false);
                String text = isPaused ? "{\"isPaused\":true}" : "{\"isPaused\":false}";
                try {
                    Files.write(file.toPath(), text.getBytes());
                } catch (IOException e) {
                    log.warn("error storing paused.json file {}", file, e);
                }
            }
        });
    }

    private File getFile(Long accountHash) {
        return accountHashToFile.computeIfAbsent(accountHash,
                (k) -> new File(Persistance.COPILOT_DIR, String.format(PAUSED_FILE_TEMPLATE, accountHash)));
    }
}