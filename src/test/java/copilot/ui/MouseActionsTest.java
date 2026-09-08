package copilot.ui;

import org.junit.Test;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class MouseActionsTest {
    private static void event(Component component, int id) {
        component.dispatchEvent(new MouseEvent(component, id, 0, 0, 1, 1, 1, false));
    }

    @Test public void compositeControlsShareClickAndHoverWithoutPressActions() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel parent = new JPanel();
            JLabel child = new JLabel();
            parent.add(child);
            AtomicInteger clicks = new AtomicInteger();
            ArrayList<Boolean> hover = new ArrayList<>();
            UIUtilities.addMouseActions(clicks::incrementAndGet, hover::add, parent, child);
            for (Component component : new Component[]{parent, child}) {
                event(component, MouseEvent.MOUSE_ENTERED);
                event(component, MouseEvent.MOUSE_PRESSED);
                event(component, MouseEvent.MOUSE_RELEASED);
                assertEquals(component == parent ? 0 : 1, clicks.get());
                event(component, MouseEvent.MOUSE_CLICKED);
                event(component, MouseEvent.MOUSE_EXITED);
            }
            assertEquals(2, clicks.get());
            assertEquals(Arrays.asList(true, false, true, false), hover);
        });
    }

    @Test public void iconButtonRetainsHoverCursorAndSwallowsClickExceptions() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JLabel button = UIUtilities.buildButton(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB),
                    "tip", () -> { throw new IllegalStateException("click"); });
            Icon normal = button.getIcon();
            event(button, MouseEvent.MOUSE_ENTERED);
            assertNotSame(normal, button.getIcon());
            assertEquals(Cursor.HAND_CURSOR, button.getCursor().getType());
            event(button, MouseEvent.MOUSE_CLICKED);
            event(button, MouseEvent.MOUSE_EXITED);
            assertSame(normal, button.getIcon());
            assertEquals(Cursor.HAND_CURSOR, button.getCursor().getType());
            assertEquals("tip", button.getToolTipText());
        });
    }
}
