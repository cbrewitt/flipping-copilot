package com.flippingcopilot.model;

import com.flippingcopilot.controller.Persistance;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import joptsimple.internal.Strings;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.nio.file.Files;


@Slf4j
@Singleton
public class LoginResponseManager {

    public static final String LOGIN_RESPONSE_JSON_FILE = "login-response.json";

    private final Gson gson;

    private LoginResponse cachedLoginResponse;

    @Inject
    public LoginResponseManager(Gson gson) {
        this.gson = gson;
    }

    public LoginResponse getLoginResponse() {
        if(cachedLoginResponse != null) {
            return cachedLoginResponse;
        }
        File file = getFile();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            cachedLoginResponse = gson.fromJson(reader, LoginResponse.class);
            return cachedLoginResponse;
        } catch (FileNotFoundException ignored) {
            return null;
        } catch (JsonSyntaxException | JsonIOException | IOException e) {
            log.warn("error loading saved login json file {}", file, e);
            return null;
        }
    }

    public void setLoginResponse(LoginResponse loginResponse) {
        if (loginResponse == null) {
            return;
        }
        File file = getFile();
        try {
            String json = gson.toJson(loginResponse);
            Files.write(file.toPath(), json.getBytes());
        } catch (IOException e) {
            log.warn("error saving login response {}", e.getMessage(), e);
            return;
        }
        cachedLoginResponse = loginResponse;
    }

    public boolean isLoggedIn() {
        LoginResponse loginResponse = getLoginResponse();
        return loginResponse != null && !loginResponse.error && !Strings.isNullOrEmpty(loginResponse.jwt);
    }

    public void reset() {
        cachedLoginResponse = null;
        File file = getFile();
        if (file.exists()) {
            if(!file.delete()) {
                log.warn("failed to delete login response file {}", file);
            }
        }
    }

    public String getJwtToken() {
        if(!isLoggedIn()) {
            return null;
        }
        return getLoginResponse().getJwt();
    }

    private File getFile() {
       return new File(Persistance.PARENT_DIRECTORY, LOGIN_RESPONSE_JSON_FILE);
    }
}
