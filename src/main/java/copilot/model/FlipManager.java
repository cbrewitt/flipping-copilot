package copilot.model;
import static java.util.Collections.*;

import java.util.function.*;
import lombok.*;
import copilot.controller.*;
import copilot.util.Constants;
import lombok.extern.slf4j.*;

import javax.inject.*;
import javax.swing.*;
import java.time.*;
import java.util.*;

/**
 * This class is essentially a cache of user flips that facilitates efficient access to the flips and statistics for
 * any time range and rs account(s) combination. Since after several years a (very) active user could have hundreds of
 * thousands of flips, it would be too slow to filter and re-calculate flips/statistics from scratch every time.
 * A bucketed aggregation strategy is used where we keep pre-computed weekly buckets of statistics and flips. For any
 * time range we can efficiently combine the weekly buckets and only have to re-calculate statistics for the partial
 * weeks on the boundaries of the time range. Have tested the UI experience with >100k flips.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class FlipManager {

    private static final int WEEK_SECS = 7 * 24 * 60 * 60;

    public static final Comparator<Flip> FLIP_STATUS_TIME_COMPARATOR =
                Comparator.comparing(Flip::isClosed).reversed().thenComparing(f -> f.closedTime > 0 ? f.closedTime : f.openedTime);

    // dependencies
    private final Items items;

    @Setter
    private Runnable flipsChangedCallback = () -> {};

    // state
    @Setter
    private volatile int copilotUserId;
    private Integer intervalAccount;
    private int intervalStartTime;
    private Stats intervalStats = new Stats();

    final Map<Integer, Map<Integer, Flip>> lastOpenFlipByItemId = new HashMap<>();
    final Map<UUID, Integer> existingCloseTimes = new HashMap<>();
    final List<WeekAggregate> weeks = new ArrayList<>(365*5);
    // Non-deleted flips with portfolio_id in {-1, -2, -3, -4} (ghost + disappeared buckets),
    // kept separately from week aggregates because mergeFlip_ excludes them via isInPortfolio.
    final Map<Integer, Map<UUID, Flip>> missedFlipsByAccount = new HashMap<>();

    public synchronized Integer getIntervalAccount() { return intervalAccount; }

    public synchronized Long estimateTransactionProfit(Integer accountId, Transaction t) {
        if (accountId != null && lastOpenFlipByItemId.containsKey(accountId)) {
            Flip flip = lastOpenFlipByItemId.get(accountId).get(t.itemId);
            if (flip != null) { return flip.calculateProfit(t); }
        }
        return null;
    }

    public synchronized boolean mergeFlips(List<Flip> flips, int copilotUserId) {
        if (copilotUserId != this.copilotUserId) { return false; }
        flips.sort(FLIP_STATUS_TIME_COMPARATOR);
        flips.forEach(this::mergeFlip_);
        SwingUtilities.invokeLater(flipsChangedCallback);
        return true;
    }

    public synchronized Stats getIntervalStats() { return intervalStats.copy(); }

    public synchronized Stats calculateStats(int startTime, Integer accountId) {
        if(accountId == null) { return calculateStatsAllAccounts(startTime); } else {
            return calculateStatsForAccount(startTime, accountId);
        }
    }

    public synchronized void setIntervalAccount(Integer account) {
        if (Objects.equals(account, intervalAccount)) { return; }
        intervalAccount = account;
        recalculateIntervalStats();
    }

    public synchronized void setIntervalStartTime(int startTime) {
        log.debug("time interval start set to: {}", Instant.ofEpochSecond(startTime));
        if (startTime == intervalStartTime) { return; }
        intervalStartTime = startTime;
        recalculateIntervalStats();
    }

    private void recalculateIntervalStats() {
        if(intervalAccount == null) { intervalStats = calculateStatsAllAccounts(intervalStartTime); } else {
            intervalStats = calculateStatsForAccount(intervalStartTime, intervalAccount);
        }
        log.debug("interval flips updated to {}, interval profit updated to {}", intervalStats.flipsMade, intervalStats.profit);
        SwingUtilities.invokeLater(flipsChangedCallback);
    }

    private Stats calculateStatsAllAccounts(int startTime) {
        var stats = new Stats();
        var w = getOrInitWeek(startTime);
        for (Flip f : w.flipsAfter(startTime, false)) { stats.addFlip(f); }
        for(int i=w.pos+1; i < weeks.size(); i++) {
            stats.add(weeks.get(i).allStats);
        }
        return stats;
    }

    private Stats calculateStatsForAccount(int startTime, int accountId) {
        var stats = new Stats();
        var w = getOrInitWeek(startTime);
        for (Flip f : w.flipsAfterForAccount(startTime, accountId)) { stats.addFlip(f); }
        for(int i=w.pos+1; i < weeks.size(); i++) {
            stats.add(weeks.get(i).accountIdToStats.get(accountId));
        }
        return stats;
    }

    public List<Flip> getPageFlips(int page, int pageSize) {
        return getPageFlips(page, pageSize,  intervalStartTime, intervalAccount);
    }

    public synchronized void aggregateFlips(int intervalStartTime, Integer accountId, boolean includeBuyingFlips, Consumer<Flip> c) {
        if (Objects.equals(accountId,-1)) { return; }

        // todo: buying flips also exist in the special Week with closed_time = 0, if arg intervalStartTime <= 0 then we
        //  can end up with duplicates pushed to the consumer because the BUYING flips gets added here and in the later section
        //  this is confusing and can lead to bugs - need to clean this up
        if(includeBuyingFlips) {
            var w = getOrInitWeek(0);
            List<Flip> f = accountId == null ? w.flipsAfter(-1, false) : w.flipsAfterForAccount(-1, accountId);
            f.stream()
                    .filter(i -> i.openedTime > intervalStartTime)
                    .filter(this::isTrackedFlip)
                    .forEach(c);
        }
        var intervalWeek = getOrInitWeek(intervalStartTime);
        for(int i=weeks.size()-1; i >= intervalWeek.pos; i--) {
            if (weeks.get(i).weekEnd <= intervalStartTime) { break; }
            var w = weeks.get(i);
            List<Flip> weekFlips = accountId == null ? w.flipsAfter(intervalStartTime, true) : w.flipsAfterForAccount(intervalStartTime, accountId);
            int n = weekFlips.size();
            // note: weekFlips are ascending order but we consume in descending order
            for(int ii=n-1; ii >= 0; ii--) {
                Flip f = weekFlips.get(ii);
                if (isTrackedFlip(f)) { c.accept(f); }
            }
        }
    }

    public synchronized List<Flip> getPageFlips(int page, int pageSize, int intervalStartTime, Integer accountId) {
        if (Objects.equals(accountId,-1)) { return new ArrayList<>(); }

        int toSkip = (page -1) * pageSize;
        var intervalWeek = getOrInitWeek(intervalStartTime);
        List<Flip> pageFlips = new ArrayList<>(pageSize == Integer.MAX_VALUE ? 0 : pageSize);
        for(int i=weeks.size()-1; i >= intervalWeek.pos; i--) {
            if (weeks.get(i).weekEnd <= intervalStartTime || pageFlips.size() == pageSize) { break; }
            var w = weeks.get(i);
            List<Flip> weekFlips = accountId == null ? w.flipsAfter(intervalStartTime, true) : w.flipsAfterForAccount(intervalStartTime, accountId);
            int n = weekFlips.size();
            for(int ii=n-1; ii >= 0 && pageFlips.size() < pageSize; ii--) {
                Flip flip = weekFlips.get(ii);
                if (!isTrackedFlip(flip)) { continue; }
                if (toSkip > 0) {
                    toSkip -= 1;
                    continue;
                }
                pageFlips.add(flip);
            }
        }
        if (items != null) { pageFlips.forEach(flip -> flip.setCachedItemName(items.getItemName(flip.itemId))); }
        return pageFlips;
    }

    public synchronized void reset() {
        intervalAccount = null;
        intervalStartTime = 0;
        copilotUserId = 0;
        intervalStats = new Stats();
        lastOpenFlipByItemId.clear();
        existingCloseTimes.clear();
        weeks.clear();
        missedFlipsByAccount.clear();
    }

    public synchronized boolean isGhostFlip(int accountId, UUID flipId) {
        if (flipId == null) { return false; }
        var byId = missedFlipsByAccount.get(accountId);
        if (byId == null) { return false; }
        Flip f = byId.get(flipId);
        return f != null && f.portfolioId == PortfolioId.GHOST;
    }

    public synchronized List<Flip> getMissedFlipsForAccount(Integer accountId) {
        if (accountId == null) { return emptyList(); }
        var byId = missedFlipsByAccount.get(accountId);
        if (byId == null || byId.isEmpty()) { return emptyList(); }
        List<Flip> result = new ArrayList<>(byId.values());
        if (items != null) { result.forEach(f -> f.setCachedItemName(items.getItemName(f.itemId))); }
        return result;
    }

    private void mergeFlip_(Flip flip) {
        Integer existingCloseTime = existingCloseTimes.get(flip.id);

        if(existingCloseTime != null) {
            var wa = getOrInitWeek(existingCloseTime);
            Flip removed = wa.removeFlipIfUpdatedBefore(existingCloseTime, flip);
            if (removed == null) {
                // the flip we are merging is an out of date instance of the same flip
                return;
            }
            if(isInInterval(removed)) { intervalStats.subtractFlip(removed); }
        }
        if(flip.deleted || !PortfolioId.isInPortfolio(flip.portfolioId)) {
            existingCloseTimes.remove(flip.id);
            var openByItem = lastOpenFlipByItemId.get(flip.accountId);
            if (openByItem != null) {
                Flip lastOpen = openByItem.get(flip.itemId);
                if (lastOpen != null && lastOpen.id.equals(flip.id)) { openByItem.remove(flip.itemId); }
            }
            updateMissedFlip(flip);
            return;
        }
        var wa = getOrInitWeek(flip.closedTime);
        wa.addFlip(flip);
        if(isInInterval(flip)) { intervalStats.addFlip(flip); }

        if(!flip.status.equals(FlipStatus.FINISHED)) {
            lastOpenFlipByItemId.computeIfAbsent(flip.accountId, (k) -> new HashMap<>()).put(flip.itemId, flip);
        } else {
            lastOpenFlipByItemId.computeIfAbsent(flip.accountId, (k) -> new HashMap<>()).remove(flip.itemId);
        }

        existingCloseTimes.put(flip.id, flip.closedTime);
        updateMissedFlip(flip);
    }

    private void updateMissedFlip(Flip flip) {
        if (flip.portfolioId < 0) {
            log.debug("missed-flip candidate: account={} item={} id={} portfolio_id={} deleted={} status={}",
                    flip.accountId, flip.itemId, flip.id,
                    flip.portfolioId, flip.deleted, flip.status);
        }
        boolean isMissed = !flip.deleted && PortfolioId.isMissed(flip.portfolioId);
        var byId = missedFlipsByAccount.get(flip.accountId);
        if (isMissed) {
            if (byId == null) {
                byId = new HashMap<>();
                missedFlipsByAccount.put(flip.accountId, byId);
            }
            Flip existing = byId.get(flip.id);
            if (existing == null || flip.isNewer(existing)) { byId.put(flip.id, flip); }
        } else if (byId != null) {
            byId.remove(flip.id);
        }
    }

    private boolean isInInterval(Flip flip) {
        return flip.closedTime >= intervalStartTime && (intervalAccount == null || flip.accountId == intervalAccount);
    }

    private WeekAggregate getOrInitWeek(int closeTime) {
        int ws = closeTime - (closeTime % WEEK_SECS);
        int i = bisect(weeks.size(), (a) ->  Integer.compare(weeks.get(a).weekStart, ws));
        if (i >= 0){
            var w = weeks.get(i);
            w.pos = i;
            return w;
        }
        var wf = new WeekAggregate();
        wf.weekStart = ws; wf.weekEnd = ws + WEEK_SECS; wf.pos = -i-1;
        weeks.add(wf.pos, wf);
        return wf;
    }

    public synchronized void deleteAccount(int accountId) {
        for (WeekAggregate week : weeks) { week.deleteAccountFlips(accountId); }
        if (intervalAccount != null && intervalAccount == accountId) {
            // change the intervalAccount if it is the one being deleted
            intervalAccount = null;
            recalculateIntervalStats();
        } else if (intervalAccount == null) {
            recalculateIntervalStats();
        }
        lastOpenFlipByItemId.remove(accountId);
        missedFlipsByAccount.remove(accountId);
        SwingUtilities.invokeLater(flipsChangedCallback);
    }

    class WeekAggregate {

        int pos; // note: only correct when returned by getOrInitWeek
        int weekStart, weekEnd;

        Stats allStats = new Stats();
        Map<Integer, Stats> accountIdToStats = new HashMap<>(20);
        Map<Integer, List<Flip>> accountIdToFlips = new HashMap<>(20);

        void addFlip(Flip flip) {
            int accountId = flip.accountId;
            allStats.addFlip(flip);
            accountIdToStats.computeIfAbsent(accountId, (k) -> new Stats()).addFlip(flip);
            var flips = accountIdToFlips.computeIfAbsent(accountId, (k) -> new ArrayList<>());
            int i = bisect(flips.size(), closedTimeCmp(flips, flip.id, flip.closedTime));
            flips.add(-i -1, flip);
        }

        Flip removeFlipIfUpdatedBefore(int existingCloseTime, Flip updatedFlip) {
            var flips = accountIdToFlips.computeIfAbsent(updatedFlip.accountId, (k) -> new ArrayList<>());
            int i = bisect(flips.size(), closedTimeCmp(flips, updatedFlip.id, existingCloseTime));
            Flip flip = flips.get(i);
            // if the existing instance of the flip is updated more recently return null
            if (flip.isNewer(updatedFlip)) { return null; }
            allStats.subtractFlip(flip);
            flips.remove(i);
            accountIdToStats.get(updatedFlip.accountId).subtractFlip(flip);
            return flip;
        }

        public List<Flip> flipsAfterForAccount(int time, int accountId) {
            if (weekEnd <= time) { return emptyList(); }
            var flips = accountIdToFlips.computeIfAbsent(accountId, (k) -> new ArrayList<>());
            if (time <= weekStart) { return flips; }
            int cut = -bisect(flips.size(), closedTimeCmp(flips, Constants.MAX_UUID, time)) - 1;
            return flips.subList(cut, flips.size());
        }

        public List<Flip> flipsAfter(int time, boolean requireSorted) {
            if (weekEnd <= time) { return emptyList(); }
            List<Flip> combinedFlips = new ArrayList<>(allStats.flipsMade);
            accountIdToFlips.keySet().forEach(i -> combinedFlips.addAll(flipsAfterForAccount(time, i)));
            if (requireSorted) {
                combinedFlips.sort(Comparator.comparing(Flip::getClosedTime).thenComparing(Flip::getId));
            }
            return combinedFlips;
        }
        public void deleteAccountFlips(int accountId) {
            accountIdToFlips.computeIfAbsent(accountId, (k) -> new ArrayList<>()).forEach((Flip f) -> {
                    allStats.subtractFlip(f);
                }
            );
            accountIdToFlips.remove(accountId);
            accountIdToStats.remove(accountId);
        }

        @Override
        public String toString() {
            return String.format("WeekAggregate[start=%s, flips=%d]", Instant.ofEpochSecond(weekStart), allStats.flipsMade);
        }

    }

    private Function<Integer, Integer> closedTimeCmp(List<Flip> flips, UUID id, int time) {
        return (a) -> {
            // sorts time ascending with id as tie-breaker
            int c = Integer.compare(flips.get(a).closedTime, time);
            return c != 0 ? c : id.compareTo(flips.get(a).id);
        };
    }

    private int bisect(int size, Function<Integer, Integer> cmpFunc) {
        int high = size -1, low = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1, cmp = cmpFunc.apply(mid);
            if (cmp < 0)
                low = mid + 1;
            else if (cmp > 0)
                high = mid - 1;
            else
                return mid; // key found
        }
        return -(low + 1);  // key not found (low = insertion point)
    }

    private boolean isTrackedFlip(Flip flip) {
        if (flip == null) { return false; }
        return PortfolioId.isInPortfolio(flip.portfolioId);
    }
}
