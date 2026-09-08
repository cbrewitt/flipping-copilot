package copilot.ui;

import javax.inject.*;
import javax.swing.*;
import java.awt.*;

@Singleton
public class CopilotPanel extends JPanel {

    public final SuggestionPanel suggestionPanel;
    public final StatsPanel statsPanel;
    public final ControlPanel controlPanel;

    @Inject
    public CopilotPanel(SuggestionPanel suggestionPanel, StatsPanel statsPanel, ControlPanel controlPanel) {
        this.statsPanel = statsPanel; this.suggestionPanel = suggestionPanel; this.controlPanel = controlPanel;

        setLayout(new BorderLayout());

        var topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.add(suggestionPanel);
        topPanel.add(Box.createRigidArea(new Dimension(MainPanel.CONTENT_WIDTH, 5)));
        topPanel.add(controlPanel);
        topPanel.add(Box.createRigidArea(new Dimension(MainPanel.CONTENT_WIDTH, 5)));

        add(topPanel, BorderLayout.NORTH);
        add(statsPanel, BorderLayout.CENTER);
    }

    public void refresh() {
        if (!UIUtilities.ensureEdt(this::refresh)) return;
        suggestionPanel.refresh();
        controlPanel.refresh();
    }
}
