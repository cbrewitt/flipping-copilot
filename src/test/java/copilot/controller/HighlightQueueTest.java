package copilot.controller;

import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Test;
import javax.swing.SwingUtilities;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import static org.junit.Assert.*;

public class HighlightQueueTest {
    @Test public void staleOrDisabledWorkNeverConstructsAnOverlay() throws Exception {
        Method enqueue = HighlightController.class.getDeclaredMethod("enqueueOverlay", Supplier.class);
        enqueue.setAccessible(true);
        // A real, uninitialized manager is sufficient: these scenarios must never register an overlay.
        java.lang.reflect.Constructor<?> overlayCtor = OverlayManager.class.getDeclaredConstructors()[0];
        overlayCtor.setAccessible(true);
        OverlayManager manager = (OverlayManager) overlayCtor.newInstance(new Object[overlayCtor.getParameterCount()]);
        for (int scenario = 0; scenario < 3; scenario++) {
            HighlightController controller = new HighlightController(null, null, null, null, null, null,
                    null, manager, null, null, null, null, null);
            AtomicInteger constructions = new AtomicInteger();
            Supplier<Overlay> factory = () -> {
                constructions.incrementAndGet();
                throw new AssertionError("Stale overlay factory must not run");
            };
            int action = scenario;
            SwingUtilities.invokeAndWait(() -> {
                try {
                    if (action == 2) controller.deactivateAndRemoveAll();
                    enqueue.invoke(controller, factory);
                    if (action == 0) controller.removeAll();
                    if (action == 1) controller.deactivateAndRemoveAll();
                    if (action == 2) controller.activate();
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            });
            SwingUtilities.invokeAndWait(() -> {}); // Drain the queued add/clear work.
            assertEquals("Scenario " + action, 0, constructions.get());
        }
    }
}
