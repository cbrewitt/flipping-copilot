package com.flippingcopilot.manager;

import com.flippingcopilot.model.AckedTransaction;
import org.junit.Test;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;



public class AckedTransactionManagerTest {

    private static final List<Integer> TEST_ACCOUNT_IDS = Arrays.asList(1_000_000_001, 1_000_000_002, 1000_000_003, 1000_000_004, 1000_000_005);

    private void clearTestAccountFiles() {
        try {
            for (Integer a : TEST_ACCOUNT_IDS) {
                ByteRecordDataFile.clearAccountFiles(AckedTransactionManager.FILE_TEMPLATE, a);
            }
        } catch (IOException e) {
            throw new RuntimeException("failed to clear test account files", e);
        }
    }

    @Test
    public void basicTest() throws Exception {
        clearTestAccountFiles();
        AckedTransactionManager manager = new AckedTransactionManager(
                new ByteRecordSafeWriter()
        );

        // Generate a single transaction for TEST_ACCOUNT_IDS.get(0) in Q1 2025
        int accountId = TEST_ACCOUNT_IDS.get(0); // 1_000_000_001
        LocalDateTime q1_2025 = LocalDateTime.of(2025, 2, 15, 10, 30); // Mid Q1 2025
        int time = (int) q1_2025.toEpochSecond(ZoneOffset.UTC);

        AckedTransaction originalTransaction = new AckedTransaction(
                UUID.randomUUID(),
                UUID.randomUUID(),
                accountId,
                time,
                1234,
                5,
                1000,
                5000,
                true,
                false,
                time + 60, // updatedTime (1 minute later)
                false
        );

        // Store the transaction
        manager.mergedAckedTransactions(Arrays.asList(originalTransaction));

        String expectedFileName = "v1_acked_txs_a" + accountId + "_2025_q1.dat";
        java.nio.file.Path expectedFilePath = ByteRecordDataFile.DATA_DIR.resolve(expectedFileName);

        assertTrue("Expected data file should exist: " + expectedFilePath,
                java.nio.file.Files.exists(expectedFilePath));

        Set<Integer> accountIds = new HashSet<>(Arrays.asList(accountId));
        manager.loadUpdatedTimes(accountIds); // Required before loading transactions

        List<AckedTransaction> loadedTransactions = manager.loadTransactionsBetween(
                accountIds,
                0, // start time (beginning of time)
                Integer.MAX_VALUE, // end time (end of time)
                new UUID(Long.MAX_VALUE, Long.MAX_VALUE), // endUUID
                100 // limit
        );

        assertEquals("Should load exactly one transaction", 1, loadedTransactions.size());

        AckedTransaction loadedTransaction = loadedTransactions.get(0);
        assertEquals("Loaded transaction should equal original", originalTransaction, loadedTransaction);
    }

    @Test
    public void testLargeScaleTransactionHandling() throws Exception {
        clearTestAccountFiles();
        AckedTransactionManager manager = new AckedTransactionManager(
                new ByteRecordSafeWriter()
        );

        // Generate 10k transactions across 5 account IDs from 2024-01-01 to 2025-06-01
        List<AckedTransaction> transactions = generateTransactions();

        Map<Integer, List<AckedTransaction>> transactionsByAccount = transactions.stream().collect(Collectors.groupingBy(AckedTransaction::getAccountId));

        // Write all transactions
        for (Map.Entry<Integer, List<AckedTransaction>> entry : transactionsByAccount.entrySet()) {
            manager.mergedAckedTransactions(entry.getValue());
        }

        // Test pagination with 5 pages of 200 transactions each
        testPaginatedLoading(manager, transactions);
    }

    private List<AckedTransaction> generateTransactions() {
        Random random = new Random(42); // Fixed seed for reproducibility
        LocalDateTime startDate = LocalDateTime.of(2025, 2, 1, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(2025, 5, 1, 0, 0);

        int startTime = (int) startDate.toEpochSecond(ZoneOffset.UTC);
        int endTime = (int) endDate.toEpochSecond(ZoneOffset.UTC);
        int timeRange = endTime - startTime;

        List<AckedTransaction> transactions = new ArrayList<>();

        for (int i = 0; i < 100_000; i++) {
            int accountId = TEST_ACCOUNT_IDS.get(random.nextInt(TEST_ACCOUNT_IDS.size()));
            int time = startTime + random.nextInt(timeRange);
            AckedTransaction transaction = new AckedTransaction(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    accountId,
                    time,
                    1000 + random.nextInt(9000),
                    1 + random.nextInt(100),
                    100 + random.nextInt(10000),
                    1000 + random.nextInt(100000),
                    random.nextBoolean(),
                    random.nextBoolean(),
                    time + random.nextInt(3600),
                    false
            );
            transactions.add(transaction);
        }

        return transactions;
    }


    private void testPaginatedLoading(AckedTransactionManager manager, List<AckedTransaction> transactions) throws IOException {
        int pageSize = 500;

        manager.loadUpdatedTimes(TEST_ACCOUNT_IDS);

        int startTime = 0;
        int endTime = Integer.MAX_VALUE;
        UUID lastUUID = new UUID(Long.MAX_VALUE, Long.MAX_VALUE);

        List<AckedTransaction> allLoadedTransactions = new ArrayList<>();

        // Load 5 pages
        for (int page = 0; page < 100; page++) {

            long s0 = System.currentTimeMillis();
            List<AckedTransaction> actual = manager.loadTransactionsBetween(
                    TEST_ACCOUNT_IDS, startTime, endTime, lastUUID, pageSize
            );
            System.out.println("Loading page " + (page + 1) + " took: " + (System.currentTimeMillis() - s0) + " ms");
            List<AckedTransaction> expected = getPage(transactions, TEST_ACCOUNT_IDS, startTime, endTime, lastUUID, pageSize);
            assertEquals(actual.size(), expected.size());
            for(int i = 0; i < actual.size(); i ++) {
                assertEquals("not equal at: "+ i, expected.get(i), actual.get(i));
            }
        }

        System.out.println("\nSuccessfully verified " + allLoadedTransactions.size() +
                " transactions across 5 pages");
    }

    private List<AckedTransaction> getPage(List<AckedTransaction> allTransactions, Iterable<Integer> accountsIds, int start, int end, UUID endUUID, int limit) {
        return allTransactions.stream()
                .sorted(AckedTransactionManager.TIME_ID_DESC)
                .filter(i -> i.getTime() >= start && i.getTime() <= end && i.getId().compareTo(endUUID) < 0)
                .limit(limit).collect(Collectors.toList());
    }
}