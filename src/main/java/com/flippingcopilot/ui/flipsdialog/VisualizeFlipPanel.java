package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.controller.ApiRequestHandler;
import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.manager.PriceGraphConfigManager;
import com.flippingcopilot.model.FlipV2;
import com.flippingcopilot.model.VisualizeFlipResponse;
import com.flippingcopilot.ui.graph.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

@Slf4j
public class VisualizeFlipPanel extends JPanel {

    // Dependencies
    private final ItemController itemController;
    private final ApiRequestHandler apiRequestHandler;

    // UI Components
    private final JLabel errorLabel = new JLabel();
    private final GraphPanel graphPanel;
    private final FlipStatsPanel statsPanel;
    private final CardLayout contentCardLayout = new CardLayout();

    // State
    private volatile FlipV2 currentFlip;

    public VisualizeFlipPanel(ItemController itemController,
                              PriceGraphConfigManager configManager,
                              FlippingCopilotConfig copilotConfig,
                              ApiRequestHandler apiRequestHandler) {
        this.itemController = itemController;
        this.apiRequestHandler = apiRequestHandler;

        setLayout(contentCardLayout);
        setBackground(ColorScheme.DARK_GRAY_COLOR);


        graphPanel = new GraphPanel(configManager);
        statsPanel = new FlipStatsPanel(configManager, copilotConfig);
        statsPanel.setBackground(configManager.getConfig().backgroundColor);
        statsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));


        add(DialogUi.centeredMessage("Right click on a flip in the flips tab and select 'Visualize flip' option.", ColorScheme.DARK_GRAY_COLOR, true, 16f), Cards.LANDING_CARD.name());
        add(DialogUi.loadingCard("Loading price data...", ColorScheme.DARK_GRAY_COLOR), Cards.LOADING_CARD.name());
        add(DialogUi.splitGraphCard(graphPanel, statsPanel), Cards.GRAPH_CARD.name());
        add(DialogUi.errorCard(errorLabel, () -> {
            if (currentFlip != null) {
                showFlipVisualization(currentFlip);
            }
        }), Cards.ERROR_CARD.name());

        contentCardLayout.show(this, Cards.LANDING_CARD.name());
    }

    public void showFlipVisualization(FlipV2 flip) {
        if (flip == null) {
            return;
        }
        currentFlip = flip;
        contentCardLayout.show(this, Cards.LOADING_CARD.name());
        Consumer<String> onFailure = (String errorMessage) -> {
            SwingUtilities.invokeLater(() -> showErrorCard(errorMessage));
        };
        Consumer<VisualizeFlipResponse> onSuccess = (VisualizeFlipResponse d) -> {
            // the server sends a message and no graph data when it has no price history for the item
            if (d.getGraphData() == null) {
                String message = d.getMessage();
                onFailure.accept(message == null || message.isEmpty() ? "No price data available for this item." : message);
                return;
            }
            d.graphData.clearPredictionData();
            SwingUtilities.invokeLater(() -> {
                showGraphCard(new DataManager(d.getGraphData(), d), flip);
            });
        };
        apiRequestHandler.asyncGetVisualizeFlipData(flip.getId(), onSuccess, onFailure);
    }

    private void showErrorCard(String errorMessage) {
        errorLabel.setText("<html><center>" + errorMessage + "</center></html>");
        contentCardLayout.show(this, Cards.ERROR_CARD.name());
    }

    private void showGraphCard(DataManager dm, FlipV2 f) {
        graphPanel.setData(dm);
        contentCardLayout.show(this, Cards.GRAPH_CARD.name());
        statsPanel.populate(f, itemController);
    }

    enum Cards {
        LANDING_CARD,
        GRAPH_CARD,
        LOADING_CARD,
        ERROR_CARD
    }
}
