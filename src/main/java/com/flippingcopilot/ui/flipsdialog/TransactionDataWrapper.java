package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.model.AckedTransaction;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@AllArgsConstructor
public class TransactionDataWrapper {

    // 1 million transactions would be ~56MB in memory. Average transactions per user as of Aug 2025 is ~5000
    private volatile byte[] data;

    public List<AckedTransaction> getPage(Set<Integer> filteredItems, Integer selectedAccount, int page, int pageSize) {
        int n = data.length / AckedTransaction.RAW_SIZE;
        int toSkip = pageSize*(page-1);
        int found = 0;
        List<AckedTransaction> pageTransactions = new ArrayList<>();
        for(int i = 0; i < n; i++) {
            AckedTransaction t = atIndex(i);
            if((filteredItems.isEmpty() || filteredItems.contains(t.getItemId())) && (selectedAccount == null || selectedAccount.equals(t.getAccountId()))){
                if(found < toSkip) {
                    toSkip--;
                    found++;
                } else {
                    pageTransactions.add(t);
                    found++;
                    if (found == pageSize) {
                        return pageTransactions;
                    }
                }
            }
        }
        return pageTransactions;
    }

    public int totalRecords(Set<Integer> filteredItems, Integer selectedAccount) {
        int found =0;
        int n = data.length / AckedTransaction.RAW_SIZE;
        for(int i = 0; i < n; i++) {
            AckedTransaction t = atIndex(i);
            if((filteredItems.isEmpty() || filteredItems.contains(t.getItemId())) && (selectedAccount == null || selectedAccount.equals(t.getAccountId()))){
                found++;
            }
        }
        return found;
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
                .filter(t -> (filteredItems.isEmpty() || filteredItems.contains(t.getItemId()))
                        && (selectedAccount == null || selectedAccount.equals(t.getAccountId())));
    }


    public void deleteOne(Predicate<AckedTransaction> predicate) {
        int totalTransactions = (data.length) / AckedTransaction.RAW_SIZE;
        for (int i = 0; i < totalTransactions; i++) {
            AckedTransaction transaction = atIndex(i);
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
            if (data[o] == newRaw[0] && data[o + 1] == newRaw[1] && data[o + 2] == newRaw[2] && data[o + 3] == newRaw[3] &&
                    data[o + 4] == newRaw[4] && data[o + 5] == newRaw[5] && data[o + 6] == newRaw[6] && data[o + 7] == newRaw[7] &&
                    data[o + 8] == newRaw[8] && data[o + 9] == newRaw[9] && data[o + 10] == newRaw[10] && data[o + 11] == newRaw[11] &&
                    data[o + 12] == newRaw[12] && data[o + 13] == newRaw[13] && data[o + 14] == newRaw[14] && data[o + 15] == newRaw[15]) {
                // Overwrite the entire transaction with new raw data
                System.arraycopy(newRaw, 0, data, o, AckedTransaction.RAW_SIZE);
                break;
            }
        }
    }
}
