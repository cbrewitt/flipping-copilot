package copilot.controller;

import copilot.model.*;
import copilot.rs.*;
import net.runelite.api.Client;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class CollectionActionsTest {
    @Test
    public void allCollectionDestinationsClearAndDelayBeforeRefresh() {
        for (String option : new String[]{"Collect to inventory", "Collect to bank"}) {
            check(30474246, option, "all:99", "delay:23:3", "refresh");
        }
        check(30474246, "Examine", "refresh");
        check(26345476, "Collect to bank", "all:99", "delay:23:3", "refresh");
        check(26345475, "Collect to inventory", "all:99", "delay:23:3", "refresh");
        check(26345476, "Collect to inventory");
        check(26345475, "Collect to bank");
    }

    @Test
    public void slotCollectionsPreserveSlotAndRefreshUnrelatedActions() {
        for (String option : new String[]{"Collect", "Bank", "Collect to Bank"}) {
            check(30474264, option, "slot:99:4", "delay:23:3", "refresh");
            for (int slot = 0; slot < 8; slot++) {
                check(26345477 + slot, option, "slot:99:" + slot, "delay:23:3", "refresh");
            }
        }
        check(30474264, "Examine", "refresh");
        check(26345477, "Examine", "refresh");
        check(26345484, "Examine", "refresh");
        check(26345474, "Collect");
        check(26345485, "Collect");
        check(null, "Collect");
    }

    @Test
    public void modifyingAnOfferDelaysSuggestionsAndRunsAfterCollectionRefresh() {
        check(30474250, "Modify offer", "slot:99:3");
        check(30474264, "Modify offer", "refresh", "slot:99:17");
    }

    private void check(Integer widgetId, String option, String... expected) {
        List<String> calls = new ArrayList<>();
        Client client = (Client) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{Client.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getAccountHash")) return 99L;
                    if (method.getName().equals("getTickCount")) return 23;
                    throw new AssertionError(method);
                });
        var uncollected = new Uncollected(client) {
            @Override public synchronized void clearAllUncollected(Long account) {
                calls.add("all:" + account);
            }
            @Override public synchronized void clearSlotUncollected(Long account, int slot) {
                calls.add("slot:" + account + ":" + slot);
            }
        };
        var heldItems = new HeldItemSyncStateRS(new GameLogin()) {
            @Override public void delayForTicks(int tick, int delay) {
                calls.add("delay:" + tick + ":" + delay);
            }
        };
        var suggestions = new Suggestions();
        var handler = new GrandExchangeCollectHandler(new PlayerLogin(client), uncollected,
                suggestions, client, heldItems) {
            @Override protected void refreshPanel() { calls.add("refresh"); }
        };
        Widget widget = widgetId == null ? null : (Widget) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{Widget.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getId")) return widgetId;
                    throw new AssertionError(method);
                });
        handler.handleCollect(new MenuOptionClicked(null) {
            @Override public String getMenuOption() { return option; }
            @Override public Widget getWidget() { return widget; }
        }, 4);
        assertEquals(widgetId + " / " + option, Arrays.asList(expected), calls);
        assertEquals(option.equals("Modify offer") ? 26 : 0, suggestions.suggestionsDelayedUntil);
    }
}
