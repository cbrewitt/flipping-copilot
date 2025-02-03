package com.flippingcopilot.controller;

import com.flippingcopilot.model.LoginResponse;
import com.flippingcopilot.model.SessionData;
import com.flippingcopilot.model.Transaction;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@Slf4j
public class Persistance {
    public static Gson gson;
    public static final File PARENT_DIRECTORY = new File(RuneLite.RUNELITE_DIR, "flipping-copilot");
    public static final String UN_ACKED_TRANSACTIONS_FILE_TEMPLATE = "%s_un_acked.jsonl";
    public static final String ALL_TRANSACTIONS_FILE_TEMPLATE = "%s_all_transactions.jsonl";
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
        File file = new File(PARENT_DIRECTORY, String.format(UN_ACKED_TRANSACTIONS_FILE_TEMPLATE, hashDisplayName(displayName)));
        if (!file.exists()) {
            log.info("no existing un acked transactions file for {}", displayName);
            return new ArrayList<>();
        }
        Set<UUID> added = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    Transaction transaction = gson.fromJson(line, Transaction.class);
                    // there was previously a bug where the same transaction was being added many times to the list
                    // just clean things here to be safe
                    if (!added.contains(transaction.getId())) {
                        transactions.add(transaction);
                        added.add(transaction.getId());
                    }
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
        log.info("loaded {} stored transactions for {}", transactions.size(), displayName);
        return transactions;
    }

    public static void storeUnAckedTransactions(List<Transaction> transactions, String displayName) {
        File unackedTransactionsFile = new File(PARENT_DIRECTORY, String.format(UN_ACKED_TRANSACTIONS_FILE_TEMPLATE, hashDisplayName(displayName)));
        try (BufferedWriter w = new BufferedWriter(new FileWriter(unackedTransactionsFile, false))) {
            for (Transaction transaction : transactions) {
                String json = gson.toJson(transaction);
                w.write(json);
                w.newLine();
            }
        } catch (IOException e) {
            log.warn("error storing un acked transactions to file {}", unackedTransactionsFile, e);
        }
    }

    public static String hashDisplayName(String displayName) {
        if(displayName == null) {
            return "null";
        }
        // we hash the display name just to ensure that it's a valid file name
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hashBytes = digest.digest(displayName.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
