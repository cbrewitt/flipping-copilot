package copilot.ui.flipsdialog;

import java.util.stream.*;
import copilot.model.*;
import lombok.*;

import java.util.*;
import java.util.function.*;

@AllArgsConstructor
public class TransactionDataWrapper {

    // 1 million transactions would be ~64MB in memory. Average transactions per user as of Aug 2025 is ~5000
    private volatile byte[] data;

    public List<AckedTransaction> getPage(Set<Integer> filteredItems, Integer selectedAccount, int page, int pageSize) {
        int toSkip = pageSize * (page - 1), found = 0;
        List<AckedTransaction> pageTransactions = new ArrayList<>();
        var matches = stream(filteredItems, selectedAccount).iterator();
        while (matches.hasNext()) {
            var transaction = matches.next();
            if (found < toSkip) {
                toSkip--;
                found++;
            } else {
                pageTransactions.add(transaction);
                found++;
                if (found == pageSize) { return pageTransactions; }
            }
        }
        return pageTransactions;
    }

    public int totalRecords(Set<Integer> filteredItems, Integer selectedAccount) {
        return (int) stream(filteredItems, selectedAccount).count();
    }

    private AckedTransaction atIndex(int n) {
        byte[] record = new byte[AckedTransaction.RAW_SIZE];
        System.arraycopy(data, AckedTransaction.RAW_SIZE*n, record, 0, AckedTransaction.RAW_SIZE);
        return AckedTransaction.fromRaw(record);
    }

    public Stream<AckedTransaction> stream(Set<Integer> filteredItems, Integer selectedAccount) {
        int totalTransactions = (data.length) / AckedTransaction.RAW_SIZE;
        return IntStream.range(0, totalTransactions)
                .mapToObj(this::atIndex)
                .filter(t -> (filteredItems.isEmpty() || filteredItems.contains(t.itemId))
                        && (selectedAccount == null || selectedAccount.equals(t.accountId)));
    }

    public void deleteOne(Predicate<AckedTransaction> predicate) {
        int totalTransactions = (data.length) / AckedTransaction.RAW_SIZE;
        for (int i = 0; i < totalTransactions; i++) {
            var transaction = atIndex(i);
            if (predicate.test(transaction)) {
                byte[] newData = new byte[data.length - AckedTransaction.RAW_SIZE];
                System.arraycopy(data, 0, newData, 0, i * AckedTransaction.RAW_SIZE);
                int rightSide = (totalTransactions - i - 1) * AckedTransaction.RAW_SIZE;
                if (rightSide > 0) {
                    System.arraycopy(data, (i + 1) * AckedTransaction.RAW_SIZE, newData, i * AckedTransaction.RAW_SIZE, rightSide);
                }
                data = newData;
                break;
            }
        }
    }

    public void update(AckedTransaction t) {
        byte[] newRaw = t.toRaw();
        int totalTransactions = (data.length) / AckedTransaction.RAW_SIZE;
        for (int i = 0; i < totalTransactions; i++) {
            int o = i * AckedTransaction.RAW_SIZE;
            // Compare first 16 bytes to match by ID
            if (Arrays.equals(data, o, o + 16, newRaw, 0, 16)) {
                // Overwrite the entire transaction with new raw data
                System.arraycopy(newRaw, 0, data, o, AckedTransaction.RAW_SIZE);
                break;
            }
        }
    }
}
