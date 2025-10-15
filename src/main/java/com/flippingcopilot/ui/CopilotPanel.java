package com.flippingcopilot.ui;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;

@Singleton
public class CopilotPanel extends JPanel {

    public final SuggestionPanel suggestionPanel;
    public final StatsPanelV2 statsPanel;
    public final ControlPanel controlPanel;

    @Inject
    public CopilotPanel(SuggestionPanel suggestionPanel,
                        StatsPanelV2 statsPanel,
                        ControlPanel controlPanel) {
        this.statsPanel = statsPanel;
        this.suggestionPanel = suggestionPanel;
        this.controlPanel = controlPanel;

        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.add(suggestionPanel);
        topPanel.add(Box.createRigidArea(new Dimension(MainPanel.CONTENT_WIDTH, 5)));
        topPanel.add(controlPanel);
        topPanel.add(Box.createRigidArea(new Dimension(MainPanel.CONTENT_WIDTH, 5)));

        add(topPanel, BorderLayout.NORTH);
        add(statsPanel, BorderLayout.CENTER);
    }

    public void refresh() {
        if(!SwingUtilities.isEventDispatchThread()) {
            // we always execute this in the Swing EDT thread
            SwingUtilities.invokeLater(this::refresh);
            return;
        }
        suggestionPanel.refresh();
        controlPanel.refresh();
    }
}
