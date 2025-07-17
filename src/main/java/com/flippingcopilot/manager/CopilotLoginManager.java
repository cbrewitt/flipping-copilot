package com.flippingcopilot.manager;

import com.flippingcopilot.controller.Persistance;
import com.flippingcopilot.model.LoginResponse;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import joptsimple.internal.Strings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;


@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class CopilotLoginManager {

    public static final String LOGIN_RESPONSE_JSON_FILE = "login-response.json";

    private final File file = new File(Persistance.PARENT_DIRECTORY, LOGIN_RESPONSE_JSON_FILE);

    // dependencies
    private final Gson gson;
    private final ScheduledExecutorService executorService;

    // state
    private LoginResponse cachedLoginResponse;
    private final ConcurrentMap<String,Integer> displayNameToAccountId = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, String> accountIdToDisplayName = new ConcurrentHashMap<>();

    public synchronized void removeAccount(Integer accountId) {
        String displayName = accountIdToDisplayName.get(accountId);
        accountIdToDisplayName.remove(accountId);
        if(displayName != null){
            displayNameToAccountId.remove(displayName);
        }
    }
    public void addAccountIfMissing(Integer accountId, String displayName, int copilotUserId) {
        if(!accountIdToDisplayName.containsKey(accountId) && getCopilotUserId() == copilotUserId) {
            synchronized (this) {
                displayNameToAccountId.put(displayName, accountId);
                accountIdToDisplayName.put(accountId, displayName);
            }
        }
    }

    public synchronized Map<String, Integer> displayNameToAccountIdMap() {
        return new HashMap<>(displayNameToAccountId);
    }

    public synchronized Map<Integer, String> accountIDToDisplayNameMap() {
        return new HashMap<>(accountIdToDisplayName);
    }

    public synchronized Set<Integer> accountIds() {
        return new HashSet<>(accountIdToDisplayName.keySet());
    }

    public synchronized Integer getAccountId(String displayName) {
        if(displayName == null) {
            return null;
        }
        return displayNameToAccountId.getOrDefault(displayName, -1);
    }

    public synchronized String getDisplayName(Integer accountId) {
        if(accountId == null){
            return null;
        }
        return accountIdToDisplayName.getOrDefault(accountId, "Unknown");
    }

    public synchronized void setLoginResponse(LoginResponse loginResponse) {
        if (loginResponse == null) {
            return;
        }
        cachedLoginResponse = loginResponse;
        saveLoginResponseAsync();
    }

    public synchronized boolean isLoggedIn() {
        LoginResponse loginResponse = getLoginResponse();
        return loginResponse != null && !Strings.isNullOrEmpty(loginResponse.jwt);
    }

    public synchronized void reset() {
        cachedLoginResponse = null;
        displayNameToAccountId.clear();
        accountIdToDisplayName.clear();
        if (file.exists()) {
            if(!file.delete()) {
                log.warn("failed to delete login response file {}", file);
            }
        }
    }

    public synchronized int getCopilotUserId() {
        LoginResponse loginResponse = getLoginResponse();
        if (loginResponse != null) {
            return loginResponse.userId;
        }
        return -1;
    }

    public synchronized String getJwtToken() {
        if(!isLoggedIn()) {
            return null;
        }
        return getLoginResponse().getJwt();
    }

    private LoginResponse getLoginResponse() {
        if(cachedLoginResponse != null) {
            return cachedLoginResponse;
        }
        cachedLoginResponse = loadLoginResponse();
        return cachedLoginResponse;
    }

    private void saveLoginResponseAsync() {
        executorService.submit(() -> {
            synchronized (file) {
                LoginResponse loginResponse = getLoginResponse();
                if (loginResponse != null) {
                    try {
                        String json = gson.toJson(loginResponse);
                        Files.write(file.toPath(), json.getBytes());
                    } catch (IOException e) {
                        log.warn("error saving login response {}", e.getMessage(), e);
                    }
                }
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
}
