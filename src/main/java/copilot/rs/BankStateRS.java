package copilot.rs;

import java.util.*;
import copilot.controller.*;
import copilot.model.*;
import com.google.inject.*;
import lombok.extern.slf4j.*;

@Singleton
@Slf4j
public class BankStateRS extends ReactiveStateImpl<BankState> {
    private final Items items;
    private final GameLogin gameLogin;

    @Inject
    public BankStateRS(Items items, GameLogin gameLogin) {
        super(BankState.empty());
        this.items = items; this.gameLogin = gameLogin;
        gameLogin.registerListener(state -> {
            if (state == null) {
                set(BankState.empty());
                return;
            }

            if (!state.loggedIn) { return; }

            Long accountHash = state.accountHash, loadedAccountHash = get().loadedAccountHash;
            if (loadedAccountHash != null && !Objects.equals(loadedAccountHash, accountHash)) {
                set(BankState.empty());
            }
        });
    }

    public void onGameTick() {
        if (!gameLogin.get().loggedIn) { return; }
        var bankInventory = items.getRunliteBankInventory();
        if (bankInventory == null) { return; }
        set(new BankState(true, bankInventory, gameLogin.get().accountHash));
    }
}
