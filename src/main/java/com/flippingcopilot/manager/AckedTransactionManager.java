package com.flippingcopilot.manager;

import com.flippingcopilot.model.AckedTransaction;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
@AllArgsConstructor(onConstructor_ = @Inject)
@Slf4j
public class AckedTransactionManager {

    public static final Comparator<AckedTransaction> TIME_ID_DESC =  Comparator.comparing(AckedTransaction::getTime).thenComparing(AckedTransaction::getId).reversed();
    public static final String FILE_PREFIX_TEMPLATE = "v1_acked_txs_a{acc_id}_";
    public static final String FILE_TEMPLATE = FILE_PREFIX_TEMPLATE + "{year}_q{quarter}.dat";

    // dependencies
    private final ByteRecordSafeWriter safeWriter;

    // state
    private final Map<Integer, Integer> cachedUpdatedTimes = new HashMap<>();
    private final Map<Integer, Set<ByteRecordDataFile>> cachedDataFiles = new HashMap<>();

    public synchronized List<AckedTransaction> loadTransactionsBetween(Iterable<Integer> accountsIds, int startIncl, int endIncl, UUID endIdExcl, int limit) throws IOException {
        List<AckedTransaction> allTransactions = new ArrayList<>();

        for (Integer accountId : accountsIds) {
            List<AckedTransaction> accountTransactions = new ArrayList<>();

            // Get data files for this account, filter by time range, and sort newest to oldest
            List<ByteRecordDataFile> accountFiles = cachedDataFiles.computeIfAbsent(accountId, k -> new HashSet<>())
                    .stream()
                    .filter(file -> file.endTime >= startIncl && file.startTime <= endIncl)
                    .sorted((f1, f2) -> Integer.compare(f2.endTime, f1.endTime))
                    .collect(Collectors.toList());

            for (ByteRecordDataFile file : accountFiles) {
                if (accountTransactions.size() >= limit) {
                    break;
                }
                ByteRecordArray arr = safeWriter.load(file.filePath.toFile(),
                        AckedTransaction.RAW_SIZE,
                        AckedTransaction.TIME_BYTE_POS,
                        AckedTransaction.UPDATE_TIME_BYTE_POS);
                for(ByteRecord r : arr.descBetween(startIncl, endIncl, endIdExcl, limit)) {
                    accountTransactions.add(AckedTransaction.fromRaw(r.data.array()));
                }
            }
            allTransactions.addAll(accountTransactions);
        }
        return allTransactions.stream().sorted(TIME_ID_DESC).limit(limit).collect(Collectors.toList());
    }

    public synchronized Map<Integer, Integer> loadUpdatedTimes(Iterable<Integer> accountsIds) throws IOException {
        List<ByteRecordDataFile> localFiles = null;
        Map<Integer, Integer> updatedTimes = new HashMap<>();
        for (Integer accountId : accountsIds) {
            if (cachedUpdatedTimes.containsKey(accountId)) {
                updatedTimes.put(accountId, cachedUpdatedTimes.get(accountId));
            } else {
                if (localFiles == null) {
                    localFiles = ByteRecordDataFile.listFiles(FILE_TEMPLATE);
                }
                int updateTime = 0;
                for (ByteRecordDataFile f : localFiles) {
                    if(f.accountId == accountId) {
                        Set<ByteRecordDataFile> accFiles = cachedDataFiles.computeIfAbsent(accountId, (k) -> new HashSet<>());
                        log.info("loading last updated time from {}", f.filePath);
                        ByteRecordArray arr = safeWriter.load(f.filePath.toFile(), AckedTransaction.RAW_SIZE, AckedTransaction.TIME_BYTE_POS, AckedTransaction.UPDATE_TIME_BYTE_POS);
                        for(int i = 0; i < arr.totalRecords(); i++) {
                            updateTime = Math.max(updateTime, arr.getRecordAtIndex(i).getUpdatedTime());
                        }
                        accFiles.add(f);
                    }
                }
                cachedUpdatedTimes.put(accountId, updateTime);
                updatedTimes.put(accountId, updateTime);
            }
        }
        return updatedTimes;
    }

    public synchronized void mergedAckedTransactions(List<AckedTransaction> transactions) throws IOException {
        if(transactions.isEmpty()){
            return;
        }
        int accountId = transactions.get(0).getAccountId();
        transactions.sort(Comparator.comparing(AckedTransaction::getTime));
        Map<ByteRecordDataFile, List<AckedTransaction>> fileNameToTransaction = transactions.stream()
                .collect(Collectors.groupingBy((t) -> ByteRecordDataFile.fromAccountAndTime(FILE_TEMPLATE, accountId, t.getTime())));

        for (Map.Entry<ByteRecordDataFile, List<AckedTransaction>> entry : fileNameToTransaction.entrySet()) {
            ByteRecordDataFile f = entry.getKey();
            List<AckedTransaction> transactionList = entry.getValue();
            ByteRecord[] records = transactionList.stream()
                    .map(AckedTransaction::toByteRecord)
                    .toArray(ByteRecord[]::new);
            safeWriter.upsertRecords(f.filePath.toFile(), records);
            cachedDataFiles.computeIfAbsent(accountId, (k) -> new HashSet<>()).add(f);
        }
    }
}
