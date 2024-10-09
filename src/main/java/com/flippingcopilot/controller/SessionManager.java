package com.flippingcopilot.controller;

import com.flippingcopilot.model.SessionData;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;

public class SessionManager {

    private final Runnable sessionStatsChangedCallback;
    private final SessionData data;
    @Getter private final String displayName;
    private Instant lastSessionUpdateTime;

    public SessionManager(String displayName, Runnable sessionStatsChangedCallback) {
        this.displayName = displayName;
        data = Persistance.loadSessionData(displayName);
        this.sessionStatsChangedCallback = sessionStatsChangedCallback;
    }

    public synchronized SessionData getData() {
        return new SessionData(data.startTime,  data.durationMillis, data.averageCash);
    }

    public synchronized void resetSession() {
        data.startTime = (int) Instant.now().getEpochSecond();
        data.averageCash = 0;
        data.durationMillis = 0;
        Persistance.storeSessionData(data, displayName);
        sessionStatsChangedCallback.run();
    }

    public synchronized void updateSessionStats(boolean currentlyFlipping, long cashStack) {
        if (!currentlyFlipping) {
            lastSessionUpdateTime = null;
        } else if (lastSessionUpdateTime == null) {
            lastSessionUpdateTime = Instant.now();
        } else {
            Instant now = Instant.now();
            long duration = Duration.between(lastSessionUpdateTime, now).toMillis();
            long newAverageCashStack = (cashStack * duration + data.durationMillis * data.averageCash) / (data.durationMillis + duration);
            data.durationMillis = data.durationMillis + duration;
            lastSessionUpdateTime = now;
            data.averageCash = newAverageCashStack;
            Persistance.storeSessionData(data, displayName);
            sessionStatsChangedCallback.run();
        }
    }
}
