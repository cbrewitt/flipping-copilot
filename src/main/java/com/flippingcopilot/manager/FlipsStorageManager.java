package com.flippingcopilot.manager;

import com.flippingcopilot.model.AckedTransaction;
import com.flippingcopilot.model.FlipV2;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Singleton
@AllArgsConstructor(onConstructor_ = @Inject)
@Slf4j
public class FlipsStorageManager {

    public static final Comparator<AckedTransaction> TIME_ID_DESC =  Comparator.comparing(AckedTransaction::getTime).thenComparing(AckedTransaction::getId).reversed();
    public static final String FILE_PREFIX_TEMPLATE = "v1_flips_a{acc_id}_";
    public static final String FILE_TEMPLATE = FILE_PREFIX_TEMPLATE + "{year}_q{quarter}.dat";

    // dependencies
    private final ByteRecordSafeWriter safeWriter;

    // state
    private final Map<Integer, Set<ByteRecordDataFile>> cachedDataFiles = new HashMap<>();


    public void loadUninitialisedAccounts(Iterable<Integer> accountsIds, Map<Integer, Integer> latestUpdatedTimes, Consumer<List<FlipV2>> c) throws IOException {
        for (Integer accountId : accountsIds) {
            if(latestUpdatedTimes.containsKey(accountId)) {
                continue;
            }
            List<ByteRecordDataFile> accountFiles = cachedDataFiles.computeIfAbsent(accountId, k -> new HashSet<>())
                    .stream()
                    .sorted(Comparator.comparing(ByteRecordDataFile::getEndTime))
                    .collect(Collectors.toList());
            int maxUpdatedTime = 0;
            for (ByteRecordDataFile file : accountFiles) {
                ByteRecordArray arr = safeWriter.load(file.filePath.toFile(),
                        AckedTransaction.RAW_SIZE,
                        AckedTransaction.TIME_BYTE_POS,
                        AckedTransaction.UPDATE_TIME_BYTE_POS);
                List<FlipV2> flips = FlipV2.listFromRaw(arr.data.array());
                for (FlipV2 f : flips) {
                    if (f.getUpdatedTime() > maxUpdatedTime) {
                        maxUpdatedTime = f.getUpdatedTime();
                    }
                }
                c.accept(flips);
            }
            latestUpdatedTimes.put(accountId, maxUpdatedTime);
        }
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
            cachedDataFiles.computeIfAbsent(accountId, (k) -> new HashSet<>()).add(f);
        }
    }


}
