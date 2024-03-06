package com.flippingcopilot.ui;

import com.flippingcopilot.controller.FlippingCopilotPlugin;
import net.runelite.client.ui.PluginPanel;
import javax.swing.*;
import java.awt.*;


public class CopilotPanel extends JPanel {
    public final SuggestionPanel suggestionPanel = new SuggestionPanel();
    private final ControlPanel controlPanel = new ControlPanel();
    public final StatsPanel statsPanel = new StatsPanel();


public CopilotPanel() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    add(suggestionPanel);
    add(Box.createRigidArea(new Dimension(0, 5)));
    add(controlPanel);
    add(Box.createRigidArea(new Dimension(0, 5)));
    add(statsPanel);
}
    public void init(FlippingCopilotPlugin plugin) {
        controlPanel.init(plugin);
        suggestionPanel.init(plugin);
    }
}
