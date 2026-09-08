package copilot.rs;

import copilot.config.*;
import com.google.inject.*;
import lombok.extern.slf4j.*;

@Singleton
@Slf4j
public class ConfigState extends ReactiveStateImpl<CopilotConfig> {

    @Inject
    public ConfigState(CopilotConfig config) {
        super(config);
        registerListener(current -> log.debug("FlippingCopilotConfigRS changed"));
    }
}
