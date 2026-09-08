package copilot.ui.graph;

import copilot.manager.GraphSettings;
import copilot.ui.graph.model.Config;
import org.junit.Test;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class ConfigPanelTest {
    @Test public void controlsInitializeWithoutSavingAndApplyAllBoundValues() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Config config = new Config();
            AtomicInteger saves = new AtomicInteger(), callbacks = new AtomicInteger();
            GraphSettings manager = new GraphSettings(null, null) {
                @Override public Config getConfig() { return config; }
                @Override public void setConfig(Config value) {
                    assertSame(config, value);
                    saves.incrementAndGet();
                }
            };
            ConfigPanel panel = new ConfigPanel(manager, callbacks::incrementAndGet);
            List<Component> controls = new ArrayList<>();
            collect(panel, controls);
            List<JCheckBox> checks = new ArrayList<>();
            List<JPanel> swatches = new ArrayList<>();
            JButton apply = null;
            for (Component control : controls) {
                if (control instanceof JCheckBox) checks.add((JCheckBox) control);
                if (control instanceof JPanel && control.getPreferredSize().equals(new Dimension(30, 20))) {
                    swatches.add((JPanel) control);
                }
                if (control instanceof JButton && "Apply".equals(((JButton) control).getText())) apply = (JButton) control;
            }
            assertEquals(2, checks.size());
            assertEquals(9, swatches.size());
            assertEquals(config.connectPoints, checks.get(0).isSelected());
            assertEquals(config.showSuggestedPriceLines, checks.get(1).isSelected());
            assertEquals(0, saves.get());
            boolean connect = !config.connectPoints, prices = !config.showSuggestedPriceLines;
            checks.get(0).setSelected(connect);
            checks.get(1).setSelected(prices);
            for (int i = 0; i < swatches.size(); i++) swatches.get(i).setBackground(new Color(i * 10, 20, 30));
            assertNotNull(apply);
            apply.doClick();
            assertEquals(connect, config.connectPoints);
            assertEquals(prices, config.showSuggestedPriceLines);
            Color[] actual = {config.lowColor, config.highColor, config.lowShadeColor, config.highShadeColor,
                    config.backgroundColor, config.plotAreaColor, config.textColor, config.axisColor, config.gridColor};
            for (int i = 0; i < actual.length; i++) assertEquals(new Color(i * 10, 20, 30), actual[i]);
            assertEquals(1, saves.get());
            assertEquals(1, callbacks.get());
        });
    }

    private static void collect(Container parent, List<Component> result) {
        for (Component child : parent.getComponents()) {
            result.add(child);
            if (child instanceof Container) collect((Container) child, result);
        }
    }
}
