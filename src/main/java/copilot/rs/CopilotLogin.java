package copilot.rs;

import copilot.model.*;
import copilot.controller.*;
import com.google.gson.*;
import com.google.inject.*;
import lombok.extern.slf4j.*;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.concurrent.*;

@Singleton
@Slf4j
public class CopilotLogin extends ReactiveStateImpl<CopilotLoginState> {

    private final File file = new File(Persistance.COPILOT_DIR, Persistance.LOGIN_RESPONSE_JSON_FILE);

    private final Gson gson;
    private final ExecutorService executor;

    @Inject
    public CopilotLogin(Gson gson, ExecutorService executor) {
        super(new CopilotLoginState());
        this.gson = gson; this.executor = executor;
        registerListener((s) -> log.debug("CopilotLoginRS to {}", s));
        update(s -> {
            s.loginResponse = loadLoginResponse();
            return s;
        });
        ReactiveStateUtil.derive(this, (s)-> s.loginResponse).registerListener(this::saveLoginResponseAsync);
    }

    private void saveLoginResponseAsync(LoginResponse lr) {
        if (lr == null) { return; }
        executor.submit(() -> {
            try {
                String json = gson.toJson(lr);
                Path target = file.toPath();
                Path tmp = Files.createTempFile(target.getParent(), "login-response", ".tmp");
                Files.writeString(tmp, json, StandardCharsets.UTF_8);
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                log.warn("Error saving login response", e);
            }
        });
    }

    protected LoginResponse loadLoginResponse() {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return gson.fromJson(reader, LoginResponse.class);
        } catch (FileNotFoundException ignored) {
            return null;
        } catch (JsonSyntaxException | JsonIOException | IOException e) {
            log.warn("error loading saved login json file {}", file, e);
            return null;
        }
    }

    public void clear () {
        if (file.exists() && !file.delete()) {
            log.warn("failed to delete login response file {}", file);
        }
        set(new CopilotLoginState());
    }

    public void removeAccount(Integer accountId) {
        update((s) -> {
            var updated = s.copy();
            String displayName = updated.accountIdToDisplayName.get(accountId);
            updated.accountIdToDisplayName.remove(accountId);
            if(displayName != null){
                updated.displayNameToAccountId.remove(displayName);
            }
            return updated;
        });
    }

    public void addAccountIfMissing(Integer accountId, String displayName, int copilotUserId) {
        update((s) -> {
            if (!s.accountIdToDisplayName.containsKey(accountId) && s.getUserId() == copilotUserId) {
                var updated = s.copy();
                updated.displayNameToAccountId.put(displayName, accountId);
                updated.accountIdToDisplayName.put(accountId, displayName);
                return updated;
            }
            return s;
        });
    }
}
