package com.flippingcopilot.controller;

import com.flippingcopilot.model.Transaction;
import com.google.common.io.CharStreams;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
public class Persistance {
    private final Gson gson;
    private final Filepath directory;
    private final boolean existingInstallation;
    public static final String UN_ACKED_TRANSACTIONS_FILE_TEMPLATE = "%s_un_acked.jsonl";
    public static final String LOGIN_RESPONSE_JSON_FILE = "login-response.json";

    public Persistance(Gson gson, Filepath directory) throws IOException {
        this.gson = gson;
        this.directory = directory;
        // Capture this before consumers create files so a fresh install stays distinguishable.
        boolean existing = false;
        if (directory.isDirectory()) {
            try (Stream<Filepath> files = directory.walk(1)) {
                existing = files.anyMatch(file -> !file.equals(directory));
            }
        }
        existingInstallation = existing;
        directory.createDirectories();
    }

    public Filepath getDirectory() {
        return directory;
    }

    public boolean hasExistingInstallation() {
        return existingInstallation;
    }

    public static String readString(Filepath file) throws IOException {
        try (Reader reader = file.openReader()) {
            return CharStreams.toString(reader);
        }
    }

    public List<Transaction> loadUnAckedTransactions(String displayName) {
        List<Transaction> transactions = new ArrayList<>();
        Filepath file = directory.joinSegment(String.format(UN_ACKED_TRANSACTIONS_FILE_TEMPLATE, hashDisplayName(displayName)));
        if (!file.exists()) {
            log.info("no existing un acked transactions file for {}", displayName);
            return new ArrayList<>();
        }
        Set<UUID> added = new HashSet<>();
        try (BufferedReader reader = file.openBufferedReader()) {
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
        } catch (NoSuchFileException e) {
            log.info("no existing un acked transactions file for {}", displayName);
            return new ArrayList<>();
        } catch (IOException e) {
            log.warn("error loading un acked transaction file {}", file, e);
            return new ArrayList<>();
        } catch (JsonSyntaxException e) {
            log.warn("corrupted un acked transaction file {}", file, e);
            return new ArrayList<>();
        }
        log.info("loaded {} stored transactions for {}", transactions.size(), displayName);
        return transactions;
    }

    public void storeUnAckedTransactions(List<Transaction> transactions, String displayName) {
        Filepath unackedTransactionsFile = directory.joinSegment(String.format(UN_ACKED_TRANSACTIONS_FILE_TEMPLATE, hashDisplayName(displayName)));
        try (BufferedWriter w = unackedTransactionsFile.openBufferedWriter()) {
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
