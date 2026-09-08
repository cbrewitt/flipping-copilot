package copilot.controller;

import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InterfaceID;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.Assert.*;

public class GrandExchangeWidgetsTest {
    @Test public void buttonLookupsPreserveParentAndChildIdsAndHandleMissingParents() {
        List<Integer> parents = new ArrayList<>(), children = new ArrayList<>();
        Widget leaf = (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[]{Widget.class},
                (proxy, method, args) -> null);
        Widget parent = (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[]{Widget.class},
                (proxy, method, args) -> {
                    assertEquals("getChild", method.getName());
                    children.add((Integer) args[0]);
                    return leaf;
                });
        for (boolean present : new boolean[]{true, false}) {
            parents.clear();
            children.clear();
            Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
                    (proxy, method, args) -> {
                        assertEquals("getWidget", method.getName());
                        assertEquals(InterfaceID.GE_OFFERS, args[0]);
                        parents.add((Integer) args[1]);
                        return present ? parent : null;
                    });
            GrandExchange exchange = new GrandExchange(client);
            List<Supplier<Widget>> lookups = Arrays.asList(() -> exchange.getBuyButton(3), exchange::getCollectButton,
                    exchange::getConfirmButton, exchange::getSetQuantityButton, exchange::getSetPriceButton,
                    exchange::getSetQuantityAllButton);
            for (Supplier<Widget> lookup : lookups) assertSame(present ? leaf : null, lookup.get());
            assertEquals(Arrays.asList(10, 6, 26, 26, 26, 26), parents);
            assertEquals(present ? Arrays.asList(0, 2, 58, 51, 54, 50) : Collections.emptyList(), children);
        }
    }
}
