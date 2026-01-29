package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.controller.ApiRequestHandler;
import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.manager.PriceGraphConfigManager;
import com.flippingcopilot.model.FlipV2;
import com.flippingcopilot.model.VisualizeFlipResponse;
import com.flippingcopilot.ui.Spinner;
import com.flippingcopilot.ui.graph.DataManager;
import com.flippingcopilot.ui.graph.FlipStatsPanel;
import com.flippingcopilot.ui.graph.GraphPanel;
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


        add(buildLandingCard(), Cards.LANDING_CARD.name());
        add(buildLoadingCard(), Cards.LOADING_CARD.name());
        add(buildGraphCard(), Cards.GRAPH_CARD.name());
        add(buildErrorCard(), Cards.ERROR_CARD.name());


        errorLabel.setForeground(Color.RED);
        errorLabel.setFont(errorLabel.getFont().deriveFont(14f));
        errorLabel.setHorizontalAlignment(SwingConstants.CENTER);
        contentCardLayout.show(this, Cards.LANDING_CARD.name());
    }

    public void showFlipVisualization(FlipV2 flip) {
        if (flip == null) {
            return;
        }
        currentFlip = flip;
        contentCardLayout.show(this, Cards.LOADING_CARD.name());
        Consumer<VisualizeFlipResponse> onSuccess = (VisualizeFlipResponse d) -> {
            d.graphData.clearPredictionData();
            SwingUtilities.invokeLater(() -> {
                showGraphCard(new DataManager(d.getGraphData(), d), flip);
            });
        };
        Consumer<String> onFailure = (String errorMessage) -> {
            SwingUtilities.invokeLater(() -> showErrorCard(errorMessage));
        };
        apiRequestHandler.asyncGetVisualizeFlipData(flip.getId(), "FlipCopilot", onSuccess, onFailure);
    }

    private JPanel buildLandingCard() {
        JPanel landingCard = new JPanel(new GridBagLayout());
        landingCard.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JLabel emptyLabel = new JLabel("Right click on a flip in the flips tab and select 'Visualize flip' option.");
        emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        emptyLabel.setFont(emptyLabel.getFont().deriveFont(16f));
        landingCard.add(emptyLabel);
        return landingCard;
    }

    private JPanel buildLoadingCard() {
        JLabel loadingLabel = new JLabel("Loading price data...");
        Spinner spinner = new Spinner();
        loadingLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        loadingLabel.setFont(loadingLabel.getFont().deriveFont(14f));
        JPanel loadingPanel = new JPanel(new GridBagLayout());
        loadingPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(10, 10, 10, 10);
        spinner.show();
        loadingPanel.add(spinner, gbc);
        gbc.gridy = 1;
        loadingPanel.add(loadingLabel, gbc);
        return loadingPanel;
    }

    private JPanel buildErrorCard() {
        JPanel errorPanel = new JPanel(new GridBagLayout());
        errorPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(10, 10, 10, 10);
        errorPanel.add(errorLabel, gbc);
        gbc.gridy = 1;
        gbc.insets = new Insets(20, 10, 10, 10);
        JButton retryButton = new JButton("Retry");
        retryButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        retryButton.setFocusable(false);
        retryButton.addActionListener(e -> {
            if (currentFlip != null) {
                showFlipVisualization(currentFlip);
            }
        });
        errorPanel.add(retryButton, gbc);
        return errorPanel;
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

    private JSplitPane buildGraphCard() {
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
        splitPane.setLeftComponent(graphPanel);
        splitPane.setRightComponent(statsPanel);
        splitPane.setResizeWeight(0.95); // Graph gets 75% of space
        splitPane.setDividerLocation(0.95);
        splitPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        return splitPane;
    }

    enum Cards {
        LANDING_CARD,
        GRAPH_CARD,
        LOADING_CARD,
        ERROR_CARD
    }
}