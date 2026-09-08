package copilot.ui;

import org.junit.Test;
import javax.swing.*;
import java.awt.*;
import static org.junit.Assert.*;

public class PanelFactoriesTest {
    @Test public void darkPanelConstructsFreshPanelsWithRequestedLayoutAndColor() {
        BorderLayout layout = new BorderLayout(3, 5);
        JPanel panel = UIUtilities.darkPanel(layout, Color.RED);
        assertSame(layout, panel.getLayout());
        assertEquals(Color.RED, panel.getBackground());
        assertTrue(panel.isOpaque());
        assertNotSame(panel, UIUtilities.darkPanel(layout, Color.RED));
        assertEquals(0, panel.getComponentCount());
    }

    @Test public void transparentPanelRetainsLayoutAndAcceptsChildren() {
        GridLayout layout = new GridLayout(1, 2);
        JPanel panel = UIUtilities.transparentPanel(layout);
        assertSame(layout, panel.getLayout());
        assertFalse(panel.isOpaque());
        JLabel child = new JLabel("Test");
        panel.add(child);
        assertSame(child, panel.getComponent(0));
        assertNotSame(panel, UIUtilities.transparentPanel(layout));
    }
}
