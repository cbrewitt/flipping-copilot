package copilot.ui.flipsdialog;

import copilot.model.AckedTransaction;
import org.junit.Test;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.Assert.*;

public class TransactionDataWrapperTest {
    @Test public void updatesRequireAllUuidBytesAndReplaceOnlyFirstMatch() {
        UUID id = new UUID(0x0102030405060708L, 0x1112131415161718L);
        List<AckedTransaction> records = new ArrayList<>();
        byte[] uuid = ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
        for (int i = 0; i < 16; i++) {
            byte[] changed = uuid.clone(); changed[i] ^= 1;
            ByteBuffer bytes = ByteBuffer.wrap(changed);
            records.add(transaction(new UUID(bytes.getLong(), bytes.getLong()), 1, 10, i));
        }
        records.add(transaction(id, 1, 10, 100));
        records.add(transaction(id, 2, 20, 200));
        TransactionDataWrapper wrapper = wrapper(records);
        AckedTransaction replacement = transaction(id, 7, 42, 999);
        wrapper.update(replacement);
        List<AckedTransaction> actual = wrapper.stream(Collections.emptySet(), null).collect(Collectors.toList());
        assertEquals(18, actual.size());
        for (int i = 0; i < 16; i++) assertEquals(records.get(i), actual.get(i));
        assertEquals(replacement, actual.get(16));
        assertEquals(records.get(17), actual.get(17));
        wrapper.update(transaction(new UUID(0, 0), 99, 99, 99));
        assertEquals(actual, wrapper.stream(Collections.emptySet(), null).collect(Collectors.toList()));
    }

    @Test public void countsRespectBothFiltersAndIgnoreIncompleteTrailingRecord() {
        var wrapper = wrapper(Arrays.asList(transaction(new UUID(0, 1), 1, 10, 1),
                transaction(new UUID(0, 2), 1, 20, 2), transaction(new UUID(0, 3), 2, 10, 3)));
        assertEquals(3, wrapper.totalRecords(Collections.emptySet(), null));
        assertEquals(2, wrapper.totalRecords(Collections.emptySet(), 1));
        assertEquals(2, wrapper.totalRecords(Collections.singleton(10), null));
        assertEquals(1, wrapper.totalRecords(Collections.singleton(10), 2));
        assertEquals(0, wrapper.totalRecords(Collections.singleton(20), 2));
        assertEquals(0, new TransactionDataWrapper(new byte[3]).totalRecords(Collections.emptySet(), null));
    }

    @Test public void sharedFilteringPreservesExistingPagingSemantics() {
        ByteBuffer bytes = ByteBuffer.allocate(20 * AckedTransaction.RAW_SIZE + 3);
        for (int i = 0; i < 20; i++) bytes.put(transaction(new UUID(0, i), i % 3, i % 4, i).toRaw());
        var original = new ReferenceTransactionDataWrapper(bytes.array());
        var current = new TransactionDataWrapper(bytes.array());
        for (Set<Integer> items : Arrays.asList(Collections.<Integer>emptySet(), Collections.singleton(1),
                new HashSet<>(Arrays.asList(1, 2)), Collections.singleton(99))) {
            for (Integer account : new Integer[]{null, 0, 1, 99}) {
                for (int page : new int[]{-1, 0, 1, 2, 3, 10, Integer.MAX_VALUE}) {
                    for (int size : new int[]{-1, 0, 1, 2, 3, 10, Integer.MAX_VALUE}) {
                        assertEquals(original.getPage(items, account, page, size), current.getPage(items, account, page, size));
                    }
                }
            }
        }
    }

    private static AckedTransaction transaction(UUID id, int account, int item, long price) {
        return new AckedTransaction(id, new UUID(9, 8), account, 123, item, 5, price, price * 5);
    }

    private static TransactionDataWrapper wrapper(List<AckedTransaction> records) {
        ByteBuffer bytes = ByteBuffer.allocate(records.size() * AckedTransaction.RAW_SIZE + 3);
        records.forEach(record -> bytes.put(record.toRaw()));
        return new TransactionDataWrapper(bytes.array());
    }
}
