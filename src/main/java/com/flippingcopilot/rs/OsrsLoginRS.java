package com.flippingcopilot.rs;

import com.flippingcopilot.model.OsrsLoginState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class OsrsLoginRS extends ReactiveStateImpl<OsrsLoginState> {

    @Inject
    public OsrsLoginRS() {
        super(new OsrsLoginState());
        registerListener((s) -> {
            log.debug("OsrsLoginRS changed to {}", s);
        });
    }
}
