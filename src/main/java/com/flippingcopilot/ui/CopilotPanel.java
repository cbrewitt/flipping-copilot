package com.flippingcopilot.ui;


import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;

@Singleton
public class CopilotPanel extends JPanel {

    public final SuggestionPanel suggestionPanel;
    public final StatsPanelV2 statsPanel;

    @Inject
    public CopilotPanel(SuggestionPanel suggestionPanel,
                        StatsPanelV2 statsPanel) {
        this.statsPanel = statsPanel;
        this.suggestionPanel = suggestionPanel;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        suggestionPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, suggestionPanel.getPreferredSize().height));
        suggestionPanel.setMinimumSize(new Dimension(Integer.MIN_VALUE, suggestionPanel.getPreferredSize().height));
        add(suggestionPanel);
        add(Box.createRigidArea(new Dimension(0, 5)));
        add(Box.createVerticalGlue());
        add(statsPanel);
    }

    public void refresh() {
        suggestionPanel.refresh();
    }
}
