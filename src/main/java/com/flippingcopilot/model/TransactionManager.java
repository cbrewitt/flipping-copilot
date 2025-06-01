package com.flippingcopilot.model;

import com.flippingcopilot.controller.ApiRequestHandler;
import com.flippingcopilot.controller.Persistance;
import com.flippingcopilot.util.MutableReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class TransactionManager {

    // dependencies
    private final FlipManager flipManager;
    private final ScheduledExecutorService executorService;
    private final ApiRequestHandler api;
    private final LoginResponseManager loginResponseManager;
    private final OsrsLoginManager osrsLoginManager;

    // state
    private final ConcurrentMap<String, List<Transaction>> cachedUnAckedTransactions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicBoolean> transactionSyncScheduled = new ConcurrentHashMap<>();

    public synchronized List<Transaction> getFlipTransactions(UUID flipId) {
        List<Transaction> flipTransactions = new ArrayList<>();
        Random random = new Random(flipId.hashCode()); // Use flipId for consistent data per flip

        String[] itemNames = {"Abyssal whip", "Dragon scimitar", "Rune platebody", "Shark", "Prayer potion(4)",
                "Super restore(4)", "Saradomin brew(4)", "Dragon bones", "Cannonball", "Nature rune"};
        int[] itemIds = {4151, 4587, 1127, 385, 2434, 3024, 6685, 536, 2, 561};

        // Select a random item for this flip
        int itemIndex = random.nextInt(itemNames.length);
        int itemId = itemIds[itemIndex];

        // Generate 2-6 transactions for this flip (mix of buys and sells)
        int numTransactions = 2 + random.nextInt(5); // 2-6 transactions

        // Track total quantities to ensure they balance out
        int totalBuyQuantity = 0;
        int totalSellQuantity = 0;

        // Base timestamp - start from a few hours ago
        long baseTime = Instant.now().getEpochSecond() - (4 * 60 * 60); // 4 hours ago
        long currentTimestamp = baseTime;

        // Generate buy transactions first (earlier timestamps)
        int numBuys = 1 + random.nextInt(3); // 1-3 buy transactions
        for (int i = 0; i < numBuys; i++) {
            Transaction buyTransaction = new Transaction();

            buyTransaction.setId(UUID.randomUUID());
            buyTransaction.setType(OfferStatus.BUY);
            buyTransaction.setItemId(itemId);

            // Random quantity for this buy
            int quantity = 1 + random.nextInt(100);
            totalBuyQuantity += quantity;
            buyTransaction.setQuantity(quantity);

            // Random buy price
            int buyPrice = 100000 + random.nextInt(1000000); // 100k-1.1M gp
            buyTransaction.setPrice(buyPrice);
            buyTransaction.setAmountSpent(buyPrice * quantity);

            // Set timestamp (buys happen first)
            buyTransaction.setTimestamp(Instant.ofEpochSecond(currentTimestamp));
            currentTimestamp += random.nextInt(1800) + 300; // 5-35 minutes between transactions

            // Set other properties
            buyTransaction.setBoxId(random.nextInt(8));
            buyTransaction.setCopilotPriceUsed(random.nextBoolean());
            buyTransaction.setWasCopilotSuggestion(random.nextInt(10) < 3);
            buyTransaction.setOfferTotalQuantity(quantity + random.nextInt(50));
            buyTransaction.setLogin(false);
            buyTransaction.setConsistent(random.nextBoolean());

            flipTransactions.add(buyTransaction);
        }

        // Generate sell transactions (later timestamps)
        // Make sure total sell quantity equals total buy quantity
        int remainingSellQuantity = totalBuyQuantity;
        int numSells = Math.max(1, numTransactions - numBuys);

        for (int i = 0; i < numSells; i++) {
            Transaction sellTransaction = new Transaction();

            sellTransaction.setId(UUID.randomUUID());
            sellTransaction.setType(OfferStatus.SELL);
            sellTransaction.setItemId(itemId);

            // Distribute remaining quantity across sell transactions
            int quantity;
            if (i == numSells - 1) {
                // Last sell transaction gets all remaining quantity
                quantity = remainingSellQuantity;
            } else {
                // Random portion of remaining quantity (but leave some for other sells)
                quantity = Math.min(remainingSellQuantity - (numSells - i - 1),
                        1 + random.nextInt(Math.max(1, remainingSellQuantity / 2)));
            }

            remainingSellQuantity -= quantity;
            totalSellQuantity += quantity;
            sellTransaction.setQuantity(quantity);

            // Sell price should be higher than buy price for profit
            int sellPrice = 120000 + random.nextInt(1200000); // 120k-1.32M gp (higher than buy)
            sellTransaction.setPrice(sellPrice);
            sellTransaction.setAmountSpent(sellPrice * quantity);

            // Set timestamp (sells happen after buys)
            sellTransaction.setTimestamp(Instant.ofEpochSecond(currentTimestamp));
            currentTimestamp += random.nextInt(1800) + 300; // 5-35 minutes between transactions

            // Set other properties
            sellTransaction.setBoxId(random.nextInt(8));
            sellTransaction.setCopilotPriceUsed(random.nextBoolean());
            sellTransaction.setWasCopilotSuggestion(random.nextInt(10) < 3);
            sellTransaction.setOfferTotalQuantity(quantity + random.nextInt(50));
            sellTransaction.setLogin(false);
            sellTransaction.setConsistent(random.nextBoolean());

            flipTransactions.add(sellTransaction);
        }

        // Sort by timestamp ascending (chronological order: buys first, then sells)
        flipTransactions.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));

        // Verify quantities balance out (for debugging)
        assert totalBuyQuantity == totalSellQuantity :
                String.format("Quantities don't balance: bought %d, sold %d", totalBuyQuantity, totalSellQuantity);

        return flipTransactions;
    }

    public synchronized List<Transaction> getPageTransactions(int page, int pageSize) {
        List<Transaction> dummyTransactions = new ArrayList<>();

        // Generate dummy data
        Random random = new Random(42); // Fixed seed for consistent data
        String[] itemNames = {"Abyssal whip", "Dragon scimitar", "Rune platebody", "Shark", "Prayer potion(4)",
                "Super restore(4)", "Saradomin brew(4)", "Dragon bones", "Cannonball", "Nature rune"};
        int[] itemIds = {4151, 4587, 1127, 385, 2434, 3024, 6685, 536, 2, 561};

        // Total dummy transactions
        int totalDummyTransactions = 237; // Arbitrary number for testing

        // Calculate start and end indices for this page
        int startIndex = page * pageSize;
        int endIndex = Math.min(startIndex + pageSize, totalDummyTransactions);

        // Generate transactions for this page
        for (int i = startIndex; i < endIndex; i++) {
            Transaction transaction = new Transaction();

            // Set UUID
            transaction.setId(UUID.randomUUID());

            // Alternate between BUY and SELL
            transaction.setType(i % 3 == 0 ? OfferStatus.SELL : OfferStatus.BUY);

            // Random item from our list
            int itemIndex = random.nextInt(itemNames.length);
            transaction.setItemId(itemIds[itemIndex]);

            // Random quantity between 1 and 10000
            int quantity = random.nextInt(10000) + 1;
            transaction.setQuantity(quantity);

            // Random price per item
            int pricePerItem = random.nextInt(5000000) + 100; // 100gp to 5M gp
            transaction.setPrice(pricePerItem);

            // Calculate amount spent
            transaction.setAmountSpent(pricePerItem * quantity);

            // Random box ID (0-7 for GE slots)
            transaction.setBoxId(random.nextInt(8));

            // Timestamp - transactions from last 30 days
            long currentTime = Instant.now().getEpochSecond();
            long thirtyDaysAgo = currentTime - (30L * 24 * 60 * 60);
            long randomTime = thirtyDaysAgo + random.nextInt((int)(currentTime - thirtyDaysAgo));
            transaction.setTimestamp(Instant.ofEpochSecond(randomTime));

            // Set some flags
            transaction.setCopilotPriceUsed(random.nextBoolean());
            transaction.setWasCopilotSuggestion(random.nextInt(10) < 3); // 30% chance
            transaction.setOfferTotalQuantity(quantity + random.nextInt(1000));
            transaction.setLogin(false);
            transaction.setConsistent(random.nextBoolean());

            dummyTransactions.add(transaction);
        }

        // Sort by timestamp descending (most recent first)
        dummyTransactions.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));

        return dummyTransactions;
    }

    public int getTotalTransactions() {
        return 237; // Match the total in getPageTransactions
    }

    public void syncUnAckedTransactions(String displayName) {

        long s = System.nanoTime();
        List<Transaction> toSend;
        synchronized (this) {
            toSend = new ArrayList<>(getUnAckedTransactions(displayName));
            if(toSend.isEmpty()) {
                transactionSyncScheduled.get(displayName).set(false);
                return;
            }
        }

        Consumer<List<FlipV2>> onSuccess = (flips) -> {
            for (FlipV2 f : flips) {
                log.debug("server updated flip for {} closed qty {}, profit {}", f.getItemName(), f.getClosedQuantity(), f.getProfit());
            }
            flipManager.mergeFlips(flips, displayName);
            log.info("sending {} transactions took {}ms", toSend.size(), (System.nanoTime() - s) / 1000_000);
            synchronized (this) {
                List<Transaction> unAckedTransactions  = getUnAckedTransactions(displayName);
                transactionSyncScheduled.get(displayName).set(false);
                toSend.forEach(unAckedTransactions::remove);
                if(!unAckedTransactions.isEmpty()) {
                    scheduleSyncIn(0, displayName);
                }
            }
        };

        Consumer<HttpResponseException> onFailure = (e) -> {
            synchronized (this) {
                transactionSyncScheduled.get(displayName).set(false);
            }
            String currentDisplayName = osrsLoginManager.getPlayerDisplayName();
            if (loginResponseManager.isLoggedIn() && (currentDisplayName == null || currentDisplayName.equals(displayName))) {
                log.warn("failed to send transactions to copilot server {}", e.getMessage(), e);
                scheduleSyncIn(10, displayName);
            }
        };
        api.sendTransactionsAsync(toSend, displayName, onSuccess, onFailure);
    }

    public long addTransaction(Transaction transaction, String displayName) {
        synchronized (this) {
            List<Transaction> unAckedTransactions = getUnAckedTransactions(displayName);
            unAckedTransactions.add(transaction);
            Persistance.storeUnAckedTransactions(unAckedTransactions, displayName);
        }
        MutableReference<Long> profit = new MutableReference<>(0L);
        if (OfferStatus.SELL.equals(transaction.getType())) {
            profit.setValue(flipManager.estimateTransactionProfit(displayName, transaction));
        }
        if (loginResponseManager.isLoggedIn()) {
            scheduleSyncIn(0, displayName);
        }
        return profit.getValue();
    }

    private List<Transaction> getUnAckedTransactions(String displayName) {
        return cachedUnAckedTransactions.computeIfAbsent(displayName, (k) -> Persistance.loadUnAckedTransactions(displayName));
    }

    public synchronized void scheduleSyncIn(int seconds, String displayName) {
        AtomicBoolean scheduled = transactionSyncScheduled.computeIfAbsent(displayName, k -> new AtomicBoolean(false));
        if(scheduled.compareAndSet(false, true)) {
            log.info("scheduling {} attempt to sync {} transactions in {}s", displayName, getUnAckedTransactions(displayName).size(), seconds);
            executorService.schedule(() ->  {
                this.syncUnAckedTransactions(displayName);
            }, seconds, TimeUnit.SECONDS);
        } else {
            log.debug("skipping scheduling sync as already scheduled");
        }
    }
}
