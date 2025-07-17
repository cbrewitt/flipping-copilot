package com.flippingcopilot.manager;


import com.flippingcopilot.model.AckedTransaction;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class ByteRecordArrayTest {

    @Test
    public void testDescBetween() {
        // use 1h time period
        LocalDateTime startDate = LocalDateTime.of(2025, 1, 1, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(2025, 1, 1, 1, 0);
        int rangeStart = (int) startDate.toEpochSecond(ZoneOffset.UTC);
        int rangeEnd = (int) endDate.toEpochSecond(ZoneOffset.UTC);

        // more records than seconds in day to ensure
        int n = 20_000;
        ByteRecordArray arr = generateArray(rangeStart, rangeEnd, n);

        int endTime = Integer.MAX_VALUE;
        UUID endId = UUID.randomUUID();
        int pageSize = 3;
        for (int i = 0; i < (1 + n / pageSize); i++ ) {
            List<ByteRecord> page = arr.descBetween(0, endTime, endId, pageSize);
            int expectedSize = Math.min(n - (i*pageSize), 3);
            assertEquals(expectedSize, page.size());
            int offset = i*pageSize;
            for(int ii = 0; ii < expectedSize; ii++) {
                ByteRecord actual = page.get(ii);
                ByteRecord expected = arr.getRecordAtIndex(n-(offset+ii) - 1);
                endTime = actual.getTime();
                endId = actual.getUUID();
                assertEquals("mismatch on page "+  i+ " item "+ ii, expected, actual);
            }
        }
    }


    private ByteRecordArray generateArray(int start, int end, int n) {
        Random random = new Random(42);
        ByteRecordArray arr = new ByteRecordArray(ByteBuffer.allocate(0), AckedTransaction.RAW_SIZE, AckedTransaction.TIME_BYTE_POS, AckedTransaction.UPDATE_TIME_BYTE_POS);
        for (int i = 0; i < n; i++) {
            int t = start + random.nextInt(end - start);
            ByteRecord r = arr.orderingRecord(t, UUID.randomUUID());
            int ind = arr.findInsertionIndex(r);
            arr.insertAtIndex(r, ind < 0 ? -(ind + 1) : ind);
        }
        return arr;
    }
}