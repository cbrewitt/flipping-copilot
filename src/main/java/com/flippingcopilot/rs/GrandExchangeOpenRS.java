package com.flippingcopilot.rs;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class GrandExchangeOpenRS extends ReactiveStateImpl<Boolean> {

    @Inject
    public GrandExchangeOpenRS(OsrsLoginRS osrsLoginRS) {
        super(false);
        registerListener((s) -> {
            log.debug("GrandExchangeOpenRS changed to {}", s);
        });
        osrsLoginRS.registerListener(state -> {
            if (!state.loggedIn) {
                set(false);
            }
        });
    }
}
