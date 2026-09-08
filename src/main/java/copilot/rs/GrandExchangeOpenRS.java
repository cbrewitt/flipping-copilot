package copilot.rs;

import com.google.inject.*;
import lombok.extern.slf4j.*;

@Singleton
@Slf4j
public class GrandExchangeOpenRS extends ReactiveStateImpl<Boolean> {

    @Inject
    public GrandExchangeOpenRS(GameLogin osrsLoginRS) {
        super(false);
        registerListener((s) -> {
            log.debug("GrandExchangeOpenRS changed to {}", s);
        });
        osrsLoginRS.registerListener(state -> {
            if (!state.loggedIn) { set(false); }
        });
    }
}
