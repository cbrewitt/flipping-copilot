package copilot.rs;

import copilot.model.*;
import com.google.inject.*;
import lombok.extern.slf4j.*;

@Singleton
@Slf4j
public class GameLogin extends ReactiveStateImpl<OsrsLoginState> {

    @Inject
    public GameLogin() {
        super(new OsrsLoginState());
        registerListener((s) -> {
            log.debug("OsrsLoginRS changed to {}", s);
        });
    }
}
