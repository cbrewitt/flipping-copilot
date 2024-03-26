package com.flippingcopilot.controller;

import com.flippingcopilot.model.LoginResponse;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class Persistance {
    public static Gson gson;
    public static final File PARENT_DIRECTORY = new File(RuneLite.RUNELITE_DIR, "flipping-copilot");
    public static final String LOGIN_RESPONSE_JSON_FILE = "login-response.json";
    public static File directory;

    public static void setUp(String directoryPath) throws IOException {
        directory = new File(directoryPath);
        createDirectory(directory);
        createRequiredFiles();
    }

    public static void setUp(Gson gson) throws IOException {
        Persistance.gson = gson;
        directory = PARENT_DIRECTORY;
        createDirectory(PARENT_DIRECTORY);
        createRequiredFiles();
    }

    private static void createRequiredFiles() throws IOException {
        generateFileIfDoesNotExist(LOGIN_RESPONSE_JSON_FILE);
    }

    private static void generateFileIfDoesNotExist(String filename) throws IOException {
        File file = new File(directory, filename);
        if (!file.exists()) {
            if (!file.createNewFile()) {
                log.info("Failed to generate file " + file.getPath());
            }
        }
    }

    private static void createDirectory(File directory) throws IOException {
        if (!directory.exists()) {
            if (!directory.mkdir()) {
                throw new IOException("unable to create parent directory!");
            }
        }
    }

    public static LoginResponse loadLoginResponse() throws IOException {
        String jsonString = getFileContent(LOGIN_RESPONSE_JSON_FILE);
        return gson.fromJson(jsonString, LoginResponse.class);
    }

    public static void saveLoginResponse(LoginResponse loginResponse) throws IOException {
        if (loginResponse == null) {
            return;
        }

        File file = new File(directory, LOGIN_RESPONSE_JSON_FILE);
        String json = gson.toJson(loginResponse);
        Files.write(file.toPath(), json.getBytes());
    }

    public static void deleteLoginResponse() {
        File file = new File(directory, LOGIN_RESPONSE_JSON_FILE);
        if (file.exists()) {
            file.delete();
        }
    }

    private static String getFileContent(String filename) throws IOException {
        Path filePath = Paths.get(directory.getAbsolutePath(), filename);
        byte[] fileBytes = Files.readAllBytes(filePath);
        return new String(fileBytes);
    }
}
