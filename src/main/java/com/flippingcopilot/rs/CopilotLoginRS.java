package com.flippingcopilot.rs;

import com.flippingcopilot.controller.Persistance;
import com.flippingcopilot.model.CopilotLoginState;
import com.flippingcopilot.model.LoginResponse;
import com.google.gson.*;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;

@Singleton
@Slf4j
public class CopilotLoginRS extends ReactiveStateImpl<CopilotLoginState> {

    private final Filepath file;

    private final Gson gson;
    private final ExecutorService executorService;

    @Inject
    public CopilotLoginRS(Persistance persistance, Gson gson, ExecutorService executorService) {
        super(new CopilotLoginState());
        this.file = persistance.getDirectory().joinSegment(Persistance.LOGIN_RESPONSE_JSON_FILE);
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
                Filepath target = file;
                Filepath tmp = target.getParent().createTempFile("login-response", ".tmp");
                tmp.write(json);
                tmp.moveTo(target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                log.warn("Error saving login response", e);
            }
        });
    }

    private LoginResponse loadLoginResponse() {
        try (BufferedReader reader = file.openBufferedReader()) {
            return gson.fromJson(reader, LoginResponse.class);
        } catch (NoSuchFileException ignored) {
            return null;
        } catch (JsonSyntaxException | JsonIOException | IOException e) {
            log.warn("error loading saved login json file {}", file, e);
            return null;
        }
    }

    public void clear () {
        try {
            file.deleteIfExists();
        } catch (IOException e) {
            log.warn("failed to delete login response file {}", file, e);
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
