package copilot.controller;

import javax.inject.Inject;
import com.google.inject.Singleton;
import lombok.*;
import copilot.model.*;
import copilot.rs.*;
import copilot.ui.*;
import com.google.inject.*;
import lombok.extern.slf4j.*;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.widgets.*;

import javax.inject.*;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class GrandExchangeCollectHandler {

    // dependencies
    private final PlayerLogin login;
    private final Uncollected geUncollected;
    private final Suggestions suggestions;
    private final Client client;
    private final HeldItemSyncStateRS heldItemSyncStateRS;

    @Setter
    private SuggestionPanel suggestionPanel;

    public void handleCollect(MenuOptionClicked event, int slot) {
        String option = event.getMenuOption();
        Widget widget = event.getWidget();
        if (widget == null) return;
        int id = widget.getId();
        switch (id) {
            case 30474246:
                if (option.equals("Collect to inventory") || option.equals("Collect to bank")) clearAll();
                refreshPanel();
                break;
            case 30474264:
                collectSlot(option, slot);
                break;
            case 26345476:
            case 26345475:
                String action = id == 26345476 ? "Collect to bank" : "Collect to inventory";
                if (option.equals(action)) {
                    clearAll();
                    refreshPanel();
                }
                break;
            default:
                int collectionSlot = id - 26345477;
                if (collectionSlot >= 0 && collectionSlot <= 7) collectSlot(option, collectionSlot);
        }
        if (option.equals("Modify offer")) {
            int modifiedSlot = widget.getId() - 30474247;
            log.debug("modify offer clicked (tick {}) on slot {}", client.getTickCount(), modifiedSlot);
            suggestions.suggestionsDelayedUntil = client.getTickCount() + 3;
            geUncollected.clearSlotUncollected(login.getAccountHash(), modifiedSlot);
        }
    }

    private void clearAll() {
        geUncollected.clearAllUncollected(login.getAccountHash());
        heldItemSyncStateRS.delayForTicks(client.getTickCount(), 3);
    }

    private void collectSlot(String option, int slot) {
        if (option.contains("Collect") || option.contains("Bank")) {
            geUncollected.clearSlotUncollected(login.getAccountHash(), slot);
            heldItemSyncStateRS.delayForTicks(client.getTickCount(), 3);
        }
        refreshPanel();
    }

    protected void refreshPanel() { suggestionPanel.refresh(); }
}
