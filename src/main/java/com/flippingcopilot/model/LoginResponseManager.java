package com.flippingcopilot.model;

import com.flippingcopilot.controller.Persistance;
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
import java.util.concurrent.ScheduledExecutorService;


@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class LoginResponseManager {

    public static final String LOGIN_RESPONSE_JSON_FILE = "login-response.json";

    private final File file = new File(Persistance.PARENT_DIRECTORY, LOGIN_RESPONSE_JSON_FILE);

    // dependencies
    private final Gson gson;
    private final ScheduledExecutorService executorService;

    // state
    private LoginResponse cachedLoginResponse;

    public synchronized LoginResponse getLoginResponse() {
        if(cachedLoginResponse != null) {
            return cachedLoginResponse;
        }
        cachedLoginResponse = load();
        return cachedLoginResponse;
    }

    public synchronized void setLoginResponse(LoginResponse loginResponse) {
        if (loginResponse == null) {
            return;
        }
        cachedLoginResponse = loginResponse;
        saveAsync();
    }

    public boolean isLoggedIn() {
        LoginResponse loginResponse = getLoginResponse();
        return loginResponse != null && !loginResponse.error && !Strings.isNullOrEmpty(loginResponse.jwt);
    }

    public void reset() {
        cachedLoginResponse = null;
        if (file.exists()) {
            if(!file.delete()) {
                log.warn("failed to delete login response file {}", file);
            }
        }
    }

    public void saveAsync() {
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

    public LoginResponse load() {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return gson.fromJson(reader, LoginResponse.class);
        } catch (FileNotFoundException ignored) {
            return null;
        } catch (JsonSyntaxException | JsonIOException | IOException e) {
            log.warn("error loading saved login json file {}", file, e);
            return null;
        }
    }

    public String getJwtToken() {
        if(!isLoggedIn()) {
            return null;
        }
        return getLoginResponse().getJwt();
    }
}
