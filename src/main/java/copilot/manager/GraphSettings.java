package copilot.manager;

import copilot.controller.*;
import copilot.ui.graph.model.Config;
import com.google.gson.*;
import lombok.*;
import lombok.extern.slf4j.*;

import javax.inject.*;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class GraphSettings {

    public static final String JSON_FILE = "price_graph_config.json";

    private final File file = new File(Persistance.COPILOT_DIR, JSON_FILE);

    // dependencies
    private final Gson gson;
    private final ScheduledExecutorService executor;

    // state
    private Config cachedConfig;

    public synchronized Config getConfig() {
        if (cachedConfig != null) { return cachedConfig; }
        cachedConfig = load();
        return cachedConfig;
    }

    public synchronized void setConfig(Config config) {
        if (config == null) { return; }
        cachedConfig = config;
        saveAsync();
    }

    public void saveAsync() {
        executor.submit(() -> {
            synchronized (file) {
                Config config = getConfig();
                try {
                    String json = gson.toJson(config);
                    Files.write(file.toPath(), json.getBytes());
                } catch (IOException e) {
                    log.warn("error saving graph config {}", e.getMessage(), e);
                }
            }
        });
    }

    public Config load() {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return gson.fromJson(reader, Config.class);
        } catch (FileNotFoundException ignored) {
            return new Config();
        } catch (JsonSyntaxException | JsonIOException | IOException e) {
            log.warn("error loading saved graph config json file {}", file, e);
            return new Config();
        }
    }
}
