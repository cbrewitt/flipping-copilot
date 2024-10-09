package com.flippingcopilot.controller;

import com.flippingcopilot.model.LoginResponse;
import com.flippingcopilot.model.SessionData;
import com.flippingcopilot.model.Transaction;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class Persistance {
    public static Gson gson;
    public static final File PARENT_DIRECTORY = new File(RuneLite.RUNELITE_DIR, "flipping-copilot");
    public static final String UN_ACKED_TRANSACTIONS_FILE_TEMPLATE = "%s_un_acked.jsonl";
    public static final String SESSION_DATE_FILE_TEMPLATE = "%s_session_data.jsonl";
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
                log.info("Failed to generate file {}", file.getPath());
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

    public static void saveLoginResponse(LoginResponse loginResponse) {
        if (loginResponse == null) {
            return;
        }
        try {
            File file = new File(directory, LOGIN_RESPONSE_JSON_FILE);
            String json = gson.toJson(loginResponse);
            Files.write(file.toPath(), json.getBytes());
        } catch (IOException e) {
            log.warn("error saving login response {}", e.getMessage(), e);
        }
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


    public static List<Transaction> loadUnAckedTransactions(String displayName) {
        List<Transaction> transactions = new ArrayList<>();
        File file = new File(PARENT_DIRECTORY, String.format(UN_ACKED_TRANSACTIONS_FILE_TEMPLATE, displayName));
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    Transaction transaction = gson.fromJson(line, Transaction.class);
                    transactions.add(transaction);
                } catch (JsonSyntaxException e) {
                    log.warn("error deserializing transaction line '{}' file {}", line, file, e);
                }
            }
        } catch (FileNotFoundException e) {
            log.info("no existing un acked transactions file for {}", displayName);
            return new ArrayList<>();
        } catch (IOException e) {
            log.warn("error loading un acked transaction file {}", file, e);
            return new ArrayList<>();
        }
        return transactions;
    }

    public static void StoreUnAckedTransactions(Iterable<Transaction> transactions, String displayName) {
        File file = new File(PARENT_DIRECTORY, String.format(UN_ACKED_TRANSACTIONS_FILE_TEMPLATE, displayName));
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
            for (Transaction transaction : transactions) {
                String json = gson.toJson(transaction);
                writer.write(json);
                writer.newLine();
            }
        } catch (IOException e) {
            log.warn("error storing un acked transactions to file {}", file, e);
        }
    }

    public static SessionData loadSessionData(String displayName) {
        File file = new File(PARENT_DIRECTORY, String.format(SESSION_DATE_FILE_TEMPLATE, displayName));
        if (!file.exists()) {
            return new SessionData((int) Instant.now().getEpochSecond(), 0 ,0);
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return gson.fromJson(reader, SessionData.class);
        } catch (JsonSyntaxException | JsonIOException | IOException e) {
            log.warn("error loading session data json file {}", file, e);
        }
        return new SessionData((int) Instant.now().getEpochSecond(), 0 ,0);
    }

    public static void storeSessionData(SessionData data, String displayName) {
        File file = new File(PARENT_DIRECTORY, String.format(SESSION_DATE_FILE_TEMPLATE, displayName));
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
            String json = gson.toJson(data);
            writer.write(json);
            writer.newLine();
        } catch (IOException e) {
            log.warn("error storing session data to file {}", file, e);
        }
    }
}
