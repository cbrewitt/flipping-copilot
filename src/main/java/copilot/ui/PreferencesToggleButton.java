package copilot.ui;
import static net.runelite.client.util.ImageUtil.*;

import net.runelite.client.util.*;
import javax.swing.*;

import java.awt.*;

class PreferencesToggleButton extends JToggleButton {
    private static final ImageIcon ON_SWITCHER, OFF_SWITCHER;

    public PreferencesToggleButton(String selectedToolTipText, String unSelectedToolTipText) {
        super(OFF_SWITCHER);
        setSelectedIcon(ON_SWITCHER);
        SwingUtil.removeButtonDecorations(this);
        setPreferredSize(new Dimension(25, 25));
        SwingUtil.addModalTooltip(this, selectedToolTipText, unSelectedToolTipText);
    }

    static {
        var onSwitcher = loadImageResource(CopilotPanel.class, "/switcher_on.png");
        ON_SWITCHER = new ImageIcon(onSwitcher);
        OFF_SWITCHER = new ImageIcon(flipImage(luminanceScale( grayscaleImage(onSwitcher), 0.61f ), true, false ));
    }
}
