package com.flippingcopilot.model;

import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.util.Constants;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

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

    public static final Comparator<FlipV2> FLIP_STATUS_TIME_COMPARATOR =
                Comparator.comparing(FlipV2::isClosed).reversed().thenComparing(f -> f.getClosedTime() > 0 ? f.getClosedTime() : f.getOpenedTime());
    public static final Comparator<FlipV2> TIME_DESC_COMPARATOR = Comparator.comparing(FlipV2::lastTransactionTime).reversed();

    // dependencies
    private final ItemController itemController;

    @Setter
    private Runnable flipsChangedCallback = () -> {};

    // state
    @Setter
    private volatile int copilotUserId;
    private Integer intervalAccount;
    private int intervalStartTime;
    private Stats intervalStats = new Stats();

    final Map<Integer, Map<Integer, FlipV2>> lastOpenFlipByItemId = new HashMap<>();
    final Map<UUID, Integer> existingCloseTimes = new HashMap<>();
    final List<WeekAggregate> weeks = new ArrayList<>(365*5);


    public synchronized Integer getIntervalAccount() {
        return intervalAccount;
    }

    public synchronized Long estimateTransactionProfit(Integer accountId, Transaction t) {
        if (accountId != null && lastOpenFlipByItemId.containsKey(accountId)) {
            FlipV2 flip = lastOpenFlipByItemId.get(accountId).get(t.getItemId());
            if(flip != null) {
                return flip.calculateProfit(t);
            }
        }
        return null;
    }

    public synchronized FlipV2 getLastFlipByItemId(Integer accountId, int itemId) {
        if (accountId != null && lastOpenFlipByItemId.containsKey(accountId)) {
            Map<Integer, FlipV2> flips = lastOpenFlipByItemId.get(accountId);
            FlipV2 flip = flips.get(itemId);
            if (flip != null) {
                flip.setCachedItemName(itemController.getItemName(flip.getItemId()));
                return flip;
            }
        }
        return null;
    }


    public synchronized boolean mergeFlips(List<FlipV2> flips, int copilotUserId) {
        if (copilotUserId != this.copilotUserId) {
            return false;
        }
        flips.sort(FLIP_STATUS_TIME_COMPARATOR);
        flips.forEach(this::mergeFlip_);
        SwingUtilities.invokeLater(flipsChangedCallback);
        return true;
    }

    public synchronized Stats getIntervalStats() {
        return intervalStats.copy();
    }

    public synchronized Stats calculateStats(int startTime, Integer accountId) {
        if(accountId == null) {
            return calculateStatsAllAccounts(startTime);
        } else {
            return calculateStatsForAccount(startTime, accountId);
        }
    }

    public synchronized void setIntervalAccount(Integer account) {
        if (Objects.equals(account, intervalAccount)) {
            return;
        }
        intervalAccount = account;
        recalculateIntervalStats();
    }

    public synchronized void setIntervalStartTime(int startTime) {
        log.debug("time interval start set to: {}", Instant.ofEpochSecond(startTime));
        if (startTime == intervalStartTime) {
            return;
        }
        intervalStartTime = startTime;
        recalculateIntervalStats();
    }

    private void recalculateIntervalStats() {
        if(intervalAccount == null) {
            intervalStats = calculateStatsAllAccounts(intervalStartTime);
        } else {
            intervalStats = calculateStatsForAccount(intervalStartTime, intervalAccount);
        }
        log.debug("interval flips updated to {}, interval profit updated to {}", intervalStats.flipsMade, intervalStats.profit);
        SwingUtilities.invokeLater(flipsChangedCallback);
    }

    private Stats calculateStatsAllAccounts(int startTime) {
        Stats stats = new Stats();
        WeekAggregate w = getOrInitWeek(startTime);
        for (FlipV2 f : w.flipsAfter(startTime, false)) {
            stats.addFlip(f);
        }
        for(int i=w.pos+1; i < weeks.size(); i++) {
            stats.add(weeks.get(i).allStats);
        }
        return stats;
    }

    private Stats calculateStatsForAccount(int startTime, int accountId) {
        Stats stats = new Stats();
        WeekAggregate w = getOrInitWeek(startTime);
        for (FlipV2 f : w.flipsAfterForAccount(startTime, accountId)) {
            stats.addFlip(f);
        }
        for(int i=w.pos+1; i < weeks.size(); i++) {
            stats.add(weeks.get(i).accountIdToStats.get(accountId));
        }
        return stats;
    }

    public List<FlipV2> getPageFlips(int page, int pageSize) {
        return getPageFlips(page, pageSize,  intervalStartTime, intervalAccount);
    }

    public synchronized void aggregateFlips(int intervalStartTime, Integer accountId, boolean includeBuyingFlips, Consumer<FlipV2> c) {
        if (Objects.equals(accountId,-1)) {
            return;
        }

        // todo: buying flips also exist in the special Week with closed_time = 0, if arg intervalStartTime <= 0 then we
        //  can end up with duplicates pushed to the consumer because the BUYING flips gets added here and in the later section
        //  this is confusing and can lead to bugs - need to clean this up
        if(includeBuyingFlips) {
            WeekAggregate w = getOrInitWeek(0);
            List<FlipV2> f = accountId == null ? w.flipsAfter(-1, false) : w.flipsAfterForAccount(-1, accountId);
            f.stream().filter(i -> i.getOpenedTime() > intervalStartTime).forEach(c);
        }
        WeekAggregate intervalWeek = getOrInitWeek(intervalStartTime);
        for(int i=weeks.size()-1; i >= intervalWeek.pos; i--) {
            if (weeks.get(i).weekEnd <= intervalStartTime) {
                break;
            }
            WeekAggregate w = weeks.get(i);
            List<FlipV2> weekFlips = accountId == null ? w.flipsAfter(intervalStartTime, true) : w.flipsAfterForAccount(intervalStartTime, accountId);
            int n = weekFlips.size();
            // note: weekFlips are ascending order but we consume in descending order
            for(int ii=n-1; ii >= 0; ii--) {
                FlipV2 f = weekFlips.get(ii);
                c.accept(f);
            }
        }
    }

    public synchronized List<FlipV2> getPageFlips(int page, int pageSize, int intervalStartTime, Integer accountId) {
        if (Objects.equals(accountId,-1)) {
            return new ArrayList<>();
        }

        int toSkip = (page -1) * pageSize;
        WeekAggregate intervalWeek = getOrInitWeek(intervalStartTime);
        List<FlipV2> pageFlips = new ArrayList<>(pageSize == Integer.MAX_VALUE ? 0 : pageSize);
        for(int i=weeks.size()-1; i >= intervalWeek.pos; i--) {
            if (weeks.get(i).weekEnd <= intervalStartTime || pageFlips.size() == pageSize) {
                break;
            }
            WeekAggregate w = weeks.get(i);
            List<FlipV2> weekFlips = accountId == null ? w.flipsAfter(intervalStartTime, true) : w.flipsAfterForAccount(intervalStartTime, accountId);
            int n = weekFlips.size();
            if (n > toSkip) {
                // note: weekFlips are ascending order but we return pages of descending order
                int end = n - toSkip;
                int start = Math.max(0, end - (pageSize - pageFlips.size()));
                for(int ii=end-1; ii >= start; ii--) {
                    pageFlips.add(weekFlips.get(ii));
                }
                toSkip = 0;
            } else {
                toSkip -= n;
            }
        }
        pageFlips.forEach(flip -> flip.setCachedItemName(itemController.getItemName(flip.getItemId())));
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
    }

    private void mergeFlip_(FlipV2 flip) {
        Integer existingCloseTime = existingCloseTimes.get(flip.getId());

        if(existingCloseTime != null) {
            WeekAggregate wa = getOrInitWeek(existingCloseTime);
            FlipV2 removed = wa.removeFlipIfUpdatedBefore(existingCloseTime, flip);
            if (removed == null) {
                // the flip we are merging is an out of date instance of the same flip
                return;
            }
            if(isInInterval(removed)) {
                intervalStats.subtractFlip(removed);
            }
        }
        if(flip.isDeleted()) {
            existingCloseTimes.remove(flip.getId());
            return;
        }
        WeekAggregate wa = getOrInitWeek(flip.getClosedTime());
        wa.addFlip(flip);
        if(isInInterval(flip)) {
            intervalStats.addFlip(flip);
        }

        if(!flip.getStatus().equals(FlipStatus.FINISHED)) {
            lastOpenFlipByItemId.computeIfAbsent(flip.getAccountId(), (k) -> new HashMap<>()).put(flip.getItemId(), flip);
        } else {
            lastOpenFlipByItemId.computeIfAbsent(flip.getAccountId(), (k) -> new HashMap<>()).remove(flip.getItemId());
        }

        existingCloseTimes.put(flip.getId(), flip.getClosedTime());
    }

    private boolean isInInterval(FlipV2 flip) {
        return flip.getClosedTime() >= intervalStartTime && (intervalAccount == null || flip.getAccountId() == intervalAccount);
    }

    private WeekAggregate getOrInitWeek(int closeTime) {
        int ws = closeTime - (closeTime % WEEK_SECS);
        int i = bisect(weeks.size(), (a) ->  Integer.compare(weeks.get(a).weekStart, ws));
        if (i >= 0){
            WeekAggregate w = weeks.get(i);
            w.pos = i;
            return w;
        }
        WeekAggregate wf = new WeekAggregate();
        wf.weekStart = ws;
        wf.weekEnd = ws + WEEK_SECS;
        wf.pos = -i-1;
        weeks.add(wf.pos, wf);
        return wf;
    }

    public synchronized void deleteAccount(int accountId) {
        for (WeekAggregate week : weeks) {
            week.deleteAccountFlips(accountId);
        }
        if (intervalAccount != null && intervalAccount == accountId) {
            // change the intervalAccount if it is the one being deleted
            intervalAccount = null;
            recalculateIntervalStats();
        } else if (intervalAccount == null) {
            recalculateIntervalStats();
        }
        lastOpenFlipByItemId.remove(accountId);
        SwingUtilities.invokeLater(flipsChangedCallback);
    }

    class WeekAggregate {

        int pos; // note: only correct when returned by getOrInitWeek
        int weekStart;
        int weekEnd;

        Stats allStats = new Stats();
        Map<Integer, Stats> accountIdToStats = new HashMap<>(20);
        Map<Integer, List<FlipV2>> accountIdToFlips = new HashMap<>(20);

        void addFlip(FlipV2 flip) {
            int accountId = flip.getAccountId();
            allStats.addFlip(flip);
            accountIdToStats.computeIfAbsent(accountId, (k) -> new Stats()).addFlip(flip);
            List<FlipV2> flips = accountIdToFlips.computeIfAbsent(accountId, (k) -> new ArrayList<>());
            int i = bisect(flips.size(), closedTimeCmp(flips, flip.getId(), flip.getClosedTime()));
            flips.add(-i -1, flip);
        }

        FlipV2 removeFlipIfUpdatedBefore(int existingCloseTime, FlipV2 updatedFlip) {
            List<FlipV2> flips = accountIdToFlips.computeIfAbsent(updatedFlip.getAccountId(), (k) -> new ArrayList<>());
            int i = bisect(flips.size(), closedTimeCmp(flips, updatedFlip.getId(), existingCloseTime));
            FlipV2 flip = flips.get(i);
            // if the existing instance of the flip is updated more recently return null
            if (flip.isNewer(updatedFlip)) {
                return null;
            }
            allStats.subtractFlip(flip);
            flips.remove(i);
            accountIdToStats.get(updatedFlip.getAccountId()).subtractFlip(flip);
            return flip;
        }

        public List<FlipV2> flipsAfterForAccount(int time, int accountId) {
            if (weekEnd <= time) {
                return Collections.emptyList();
            }
            List<FlipV2> flips = accountIdToFlips.computeIfAbsent(accountId, (k) -> new ArrayList<>());
            if (time <= weekStart) {
                return flips;
            }
            int cut = -bisect(flips.size(), closedTimeCmp(flips, Constants.MAX_UUID, time)) - 1;
            return flips.subList(cut, flips.size());
        }

        public List<FlipV2> flipsAfter(int time, boolean requireSorted) {
            if (weekEnd <= time) {
                return Collections.emptyList();
            }
            List<FlipV2> combinedFlips = new ArrayList<>(allStats.flipsMade);
            accountIdToFlips.keySet().forEach(i -> combinedFlips.addAll(flipsAfterForAccount(time, i)));
            if (requireSorted) {
                combinedFlips.sort(Comparator.comparing(FlipV2::getClosedTime).thenComparing(FlipV2::getId));
            }
            return combinedFlips;
        }
        public void deleteAccountFlips(int accountId) {
            accountIdToFlips.computeIfAbsent(accountId, (k) -> new ArrayList<>()).forEach((FlipV2 f) -> {
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

    private Function<Integer, Integer> closedTimeCmp(List<FlipV2> flips, UUID id, int time) {
        return (a) -> {
            // sorts time ascending with id as tie-breaker
            int c = Integer.compare(flips.get(a).getClosedTime(), time);
            return c != 0 ? c : id.compareTo(flips.get(a).getId());
        };
    }

    private int bisect(int size, Function<Integer, Integer> cmpFunc) {
        int high = size -1;
        int low = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int cmp = cmpFunc.apply(mid);
            if (cmp < 0)
                low = mid + 1;
            else if (cmp > 0)
                high = mid - 1;
            else
                return mid; // key found
        }
        return -(low + 1);  // key not found (low = insertion point)
    }
}
