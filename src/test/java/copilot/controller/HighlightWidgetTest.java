package copilot.controller;

import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InterfaceID;
import org.junit.Test;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;

public class HighlightWidgetTest {
    @Test public void closeButtonsUseCorrectFramesAndHandleMissingChildren() throws Exception {
        Widget close = (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[]{Widget.class},
                (proxy, method, args) -> null);
        Map<String, Integer> frames = Map.of("getBankCloseButton", InterfaceID.Bankmain.FRAME,
                "getGrandExchangeCloseButton", InterfaceID.GeOffers.FRAME);
        for (Map.Entry<String, Integer> entry : frames.entrySet()) {
            for (int scenario = 0; scenario < 4; scenario++) {
                Widget[] children = scenario < 2 ? null : new Widget[scenario == 2 ? 11 : 12];
                if (scenario == 3) children[11] = close;
                Widget frame = (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[]{Widget.class},
                        (proxy, method, args) -> {
                            assertEquals("getDynamicChildren", method.getName());
                            return children;
                        });
                boolean missingFrame = scenario == 0;
                Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
                        (proxy, method, args) -> {
                            assertEquals("getWidget", method.getName());
                            assertEquals(entry.getValue(), args[0]);
                            return missingFrame ? null : frame;
                        });
                HighlightController controller = new HighlightController(null, null, null, null, null, client,
                        null, null, null, null, null, null, null);
                Method lookup = HighlightController.class.getDeclaredMethod(entry.getKey());
                lookup.setAccessible(true);
                assertSame(scenario == 3 ? close : null, lookup.invoke(controller));
            }
        }
    }
}
