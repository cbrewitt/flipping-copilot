package copilot.ui.flipsdialog;

import copilot.controller.*;
import copilot.model.*;
import static net.runelite.client.ui.ColorScheme.*;
import copilot.config.*;
import copilot.manager.*;
import copilot.ui.graph.*;
import lombok.extern.slf4j.*;

import javax.swing.*;
import java.awt.*;
import java.util.function.*;

@Slf4j
public class VisualizeFlipPanel extends JPanel {

    // Dependencies
    private final Items items;
    private final ApiClient api;

    // UI Components
    private final JLabel errorLabel = new JLabel();
    private final GraphPanel graphPanel;
    private final FlipStatsPanel statsPanel;
    private final CardLayout contentCardLayout = new CardLayout();

    // State
    private volatile Flip currentFlip;

    public VisualizeFlipPanel(Items items,
                              GraphSettings configManager,
                              CopilotConfig copilotConfig,
                              ApiClient api) {
        this.items = items; this.api = api;

        setLayout(contentCardLayout);
        setBackground(DARK_GRAY_COLOR);

        graphPanel = new GraphPanel(configManager);
        statsPanel = new FlipStatsPanel(configManager, copilotConfig);
        statsPanel.setBackground(configManager.getConfig().backgroundColor);
        statsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(DialogUi.centeredMessage("Right click on a flip in the flips tab and select 'Visualize flip' option.", DARK_GRAY_COLOR, true, 16f), Cards.LANDING_CARD.name());
        add(DialogUi.loadingCard("Loading price data...", DARK_GRAY_COLOR), Cards.LOADING_CARD.name());
        add(DialogUi.splitGraphCard(graphPanel, statsPanel), Cards.GRAPH_CARD.name());
        add(DialogUi.errorCard(errorLabel, () -> {
            if (currentFlip != null) { showFlipVisualization(currentFlip); }
        }), Cards.ERROR_CARD.name());

        contentCardLayout.show(this, Cards.LANDING_CARD.name());
    }

    public void showFlipVisualization(Flip flip) {
        if (flip == null) { return; }
        currentFlip = flip;
        contentCardLayout.show(this, Cards.LOADING_CARD.name());
        Consumer<String> onFailure = (String errorMessage) -> {
            SwingUtilities.invokeLater(() -> showErrorCard(errorMessage));
        };
        Consumer<VisualizeFlipResponse> onSuccess = (VisualizeFlipResponse d) -> {
            // the server sends a message and no graph data when it has no price history for the item
            if (d.graphData == null) {
                String message = d.message;
                onFailure.accept(message == null || message.isEmpty() ? "No price data available for this item." : message);
                return;
            }
            d.graphData.clearPredictionData();
            SwingUtilities.invokeLater(() -> {
                showGraphCard(new DataManager(d.graphData, d), flip);
            });
        };
        api.asyncGetVisualizeFlipData(flip.id, onSuccess, onFailure);
    }

    private void showErrorCard(String errorMessage) {
        errorLabel.setText("<html><center>" + errorMessage + "</center></html>");
        contentCardLayout.show(this, Cards.ERROR_CARD.name());
    }

    private void showGraphCard(DataManager dm, Flip f) {
        graphPanel.setData(dm);
        contentCardLayout.show(this, Cards.GRAPH_CARD.name());
        statsPanel.populate(f, items);
    }

    enum Cards {
        LANDING_CARD,
        GRAPH_CARD,
        LOADING_CARD,
        ERROR_CARD
    }
}
