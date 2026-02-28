package com.flippingcopilot.rs;

import com.flippingcopilot.controller.Persistance;
import com.flippingcopilot.model.CopilotLoginState;
import com.flippingcopilot.model.LoginResponse;
import com.flippingcopilot.model.OsrsLoginState;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;

@Singleton
@Slf4j
public class CopilotLoginRS extends ReactiveStateImpl<CopilotLoginState> {

    public static final String LOGIN_RESPONSE_JSON_FILE = "login-response.json";
    private final File file = new File(Persistance.COPILOT_DIR, LOGIN_RESPONSE_JSON_FILE);

    private final Gson gson;
    private final ExecutorService executorService;

    @Inject
    public CopilotLoginRS(Gson gson, ExecutorService executorService) {
        super(new CopilotLoginState());
        this.gson = gson;
        this.executorService = executorService;
        registerListener((s) -> log.debug("CopilotLoginRS to {}", s));
        update(s -> {
            s.loginResponse = loadLoginResponse();
            return s;
        });
        ReactiveStateUtil.derive(this, (s)-> s.loginResponse).registerListener(this::saveLoginResponseAsync);
    }

    private void saveLoginResponseAsync(LoginResponse lr) {
        if (lr == null) {
            return;
        }
        executorService.submit(() -> {
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

    private LoginResponse loadLoginResponse() {
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
            CopilotLoginState updated = s.copy();
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
                CopilotLoginState updated = s.copy();
                updated.displayNameToAccountId.put(displayName, accountId);
                updated.accountIdToDisplayName.put(accountId, displayName);
                return updated;
            }
            return s;
        });
    }
}
