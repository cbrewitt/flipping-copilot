package com.flippingcopilot.manager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class AccountsManager {

    private final ConcurrentMap<String,Integer> displayNameToAccountId = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, String> accountIdToDisplayName = new ConcurrentHashMap<>();

    public boolean exists(Integer accountId){
        return accountIdToDisplayName.containsKey(accountId);
    }

    public synchronized void removeAccount(Integer accountId) {
        String displayName = accountIdToDisplayName.get(accountId);
        accountIdToDisplayName.remove(accountId);
        if(displayName != null ){
            displayNameToAccountId.remove(displayName);
        }
    }
    public synchronized void add(Integer accountId, String displayName) {
        displayNameToAccountId.put(displayName, accountId);
        accountIdToDisplayName.put(accountId, displayName);
    }

    public synchronized void clear() {
        displayNameToAccountId.clear();
        accountIdToDisplayName.clear();
    }
    public synchronized Map<String, Integer> displayNameToAccountIdMap() {
        return new HashMap<>(displayNameToAccountId);
    }

    public synchronized Map<Integer, String> accountIDToDisplayNameMap() {
        return new HashMap<>(accountIdToDisplayName);
    }

    public synchronized Integer getAccountId(String displayName) {
        if(displayName == null) {
            return null;
        }
        return displayNameToAccountId.getOrDefault(displayName, -1);
    }

    public synchronized String getDisplayName(Integer accountId) {
        if(accountId == null){
            return null;
        }
        return accountIdToDisplayName.getOrDefault(accountId, "Unknown");
    }

    public Set<Integer> accountIds() {
        return new HashSet<>(accountIdToDisplayName.keySet());
    }
}
