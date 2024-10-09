package com.flippingcopilot.ui;

import com.flippingcopilot.controller.*;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;


public class CopilotPanel extends JPanel {
    public final SuggestionPanel suggestionPanel;
    private final ControlPanel controlPanel;
    public final StatsPanelV2 statsPanel;


public CopilotPanel(FlippingCopilotConfig config,
                    AtomicReference<FlipManager> flipTracker,
                    AtomicReference<SessionManager> sessionManager,
                    WebHookController webHookController) {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    suggestionPanel = new SuggestionPanel(config, webHookController);
    controlPanel = new ControlPanel();
    controlPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, controlPanel.getPreferredSize().height));
    controlPanel.setMinimumSize(new Dimension(Integer.MIN_VALUE, controlPanel.getPreferredSize().height));
    suggestionPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, suggestionPanel.getPreferredSize().height));
    suggestionPanel.setMinimumSize(new Dimension(Integer.MIN_VALUE, suggestionPanel.getPreferredSize().height));
    statsPanel = new StatsPanelV2(config, flipTracker, sessionManager, webHookController);
    add(suggestionPanel);
    add(Box.createRigidArea(new Dimension(0, 5)));
    add(controlPanel);
    add(Box.createRigidArea(new Dimension(0, 5)));
    add(Box.createVerticalGlue());
    add(statsPanel);
}
    public void init(FlippingCopilotPlugin plugin) {
        controlPanel.init(plugin);
        suggestionPanel.init(plugin);
    }
}
