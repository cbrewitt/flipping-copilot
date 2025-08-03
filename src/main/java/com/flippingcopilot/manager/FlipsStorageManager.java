package com.flippingcopilot.manager;

import com.flippingcopilot.model.FlipV2;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
@AllArgsConstructor(onConstructor_ = @Inject)
@Slf4j
public class FlipsStorageManager {

    public static final String FILE_PREFIX_TEMPLATE = "v1_flips_a{acc_id}_";
    public static final String FILE_TEMPLATE = FILE_PREFIX_TEMPLATE + "{year}_q{quarter}.dat";

    // dependencies
    private final ByteRecordSafeWriter safeWriter;

    // state
    private final Map<Integer, List<ByteRecordDataFile>> cachedDataFiles = new HashMap<>();


    public Map<Integer, Integer> loadSavedFlips(Iterable<Integer> accountsIds,  Function<List<FlipV2>, Boolean> c) throws IOException {
        Map<Integer, Integer> latestUpdatedTimes = new HashMap<>();
        for (Integer accountId : accountsIds) {
            if(!cachedDataFiles.containsKey(accountId)) {
                List<ByteRecordDataFile> df = listAccountDataFiles(accountId);
                cachedDataFiles.put(accountId, df);
            }
            List<ByteRecordDataFile> df = cachedDataFiles.get(accountId);
            int maxUpdatedTime = 0;
            List<FlipV2> flips = new ArrayList<>();
            for (ByteRecordDataFile file : df) {
                ByteRecordArray arr = safeWriter.load(file.filePath.toFile(),
                        FlipV2.RAW_SIZE,
                        FlipV2.OPENED_TIME_BYTE_POS,
                        FlipV2.UPDATED_TIME_BYTE_POS);
                for (int i = 0; i < arr.totalRecords(); i++) {
                    FlipV2 f = FlipV2.fromRaw(arr.getRecordAtIndex(i).data.array());
                    if (f.getUpdatedTime() > maxUpdatedTime) {
                        maxUpdatedTime = f.getUpdatedTime();
                    }
                    flips.add(f);
                }
            }
            if (!c.apply(flips)) {
                return latestUpdatedTimes;
            }
            log.info("loaded {} flips from disk for account {}, max update time {}", flips.size(), accountId, maxUpdatedTime);
            latestUpdatedTimes.put(accountId, maxUpdatedTime);
        }
        return latestUpdatedTimes;
    }

    public synchronized void mergeFlips(List<FlipV2> flips) throws IOException {
        if(flips.isEmpty()){
            return;
        }
        int accountId = flips.get(0).getAccountId();
        flips.sort(Comparator.comparing(FlipV2::getClosedTime));
        Map<ByteRecordDataFile, List<FlipV2>> fileNameToTransaction = flips.stream()
                .collect(Collectors.groupingBy((FlipV2 t) -> ByteRecordDataFile.fromAccountAndTime(FILE_TEMPLATE, accountId, t.getClosedTime())));

        for (Map.Entry<ByteRecordDataFile, List<FlipV2>> entry : fileNameToTransaction.entrySet()) {
            ByteRecordDataFile f = entry.getKey();
            List<FlipV2> flipList = entry.getValue();
            ByteRecord[] records = flipList.stream()
                    .map(FlipV2::toByteRecord)
                    .toArray(ByteRecord[]::new);
            safeWriter.upsertRecords(f.filePath.toFile(), records);
            cachedDataFiles.computeIfAbsent(accountId, (k) -> new ArrayList<>()).add(f);
        }
    }

    private List<ByteRecordDataFile> listAccountDataFiles(int accountId) throws IOException {
        return ByteRecordDataFile.listFiles(FILE_TEMPLATE).stream().filter(i -> i.accountId == accountId).collect(Collectors.toList());
    }
}
