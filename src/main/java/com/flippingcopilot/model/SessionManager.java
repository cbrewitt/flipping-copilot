package com.flippingcopilot.model;

import com.flippingcopilot.controller.Persistance;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Singleton
@Slf4j
public class SessionManager {

    private final OsrsLoginManager osrsLoginManager;
    private final Map<String, SessionData> cachedSessionData =  new HashMap<>();
    private Instant lastSessionUpdateTime;

    @Inject
    public SessionManager(OsrsLoginManager osrsLoginManager) {
        this.osrsLoginManager = osrsLoginManager;
    }

    public synchronized SessionData getCachedSessionData() {
        SessionData sd = getSessionData(osrsLoginManager.getPlayerDisplayName());
        return new SessionData(sd.startTime,  sd.durationMillis, sd.averageCash);
    }

    public synchronized void resetSession() {
        String displayName = osrsLoginManager.getPlayerDisplayName();
        SessionData sd = getSessionData(displayName);
        sd.startTime = (int) Instant.now().getEpochSecond();
        sd.averageCash = 0;
        sd.durationMillis = 0;
        Persistance.storeSessionData(sd, displayName);
    }

    public synchronized void updateSessionStats(boolean currentlyFlipping, long cashStack) {
        String displayName = osrsLoginManager.getPlayerDisplayName();
        if (!currentlyFlipping || displayName == null) {
            lastSessionUpdateTime = null;
        } else if (lastSessionUpdateTime == null) {
            lastSessionUpdateTime = Instant.now();
        } else {
            SessionData sd = getSessionData(displayName);
            Instant now = Instant.now();
            long duration = Duration.between(lastSessionUpdateTime, now).toMillis();
            long newAverageCashStack = (cashStack * duration + sd.durationMillis * sd.averageCash) / (sd.durationMillis + duration);
            sd.durationMillis = sd.durationMillis + duration;
            lastSessionUpdateTime = now;
            sd.averageCash = newAverageCashStack;
            Persistance.storeSessionData(sd, displayName);
        }
    }

    private SessionData getSessionData(String displayName) {
         return cachedSessionData.computeIfAbsent(displayName, Persistance::loadSessionData);
    }

    public synchronized void reset() {
        lastSessionUpdateTime = null;
    }
}
