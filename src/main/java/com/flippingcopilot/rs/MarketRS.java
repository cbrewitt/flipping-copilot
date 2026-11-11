package com.flippingcopilot.rs;

import com.flippingcopilot.model.Market;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Market detected from the current world, retained while logged out. */
@Singleton
public class MarketRS {
    private final ReactiveStateImpl<Market> market;
    private final AtomicInteger revision = new AtomicInteger();

    @Inject public MarketRS(OsrsLoginRS login) {
        market = new ReactiveStateImpl<>(login.get().market);
        login.registerListener(s -> {
            revision.incrementAndGet();
            market.set(s.market);
        });
    }
    public Market get() { return market.get(); }
    public Runnable registerListener(Consumer<Market> listener) { return market.registerListener(listener); }
    public BooleanSupplier guard() {
        int expected = revision.get();
        return () -> revision.get() == expected;
    }
}
