package com.flippingcopilot.ui;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.manager.PriceGraphConfigManager;
import com.flippingcopilot.model.Suggestion;
import com.flippingcopilot.model.SuggestionManager;
import com.flippingcopilot.ui.graph.DataManager;
import com.flippingcopilot.ui.graph.GraphPanel;
import com.flippingcopilot.ui.graph.model.Data;
import com.flippingcopilot.ui.graph.model.PriceLine;
import net.runelite.client.ui.ColorScheme;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;

@Singleton
public class SidebarGraphPanel extends JPanel {

    private static final int SIDEBAR_GRAPH_HEIGHT = 200;

    private final SuggestionManager suggestionManager;
    private final FlippingCopilotConfig config;
    private final GraphPanel graphPanel;
    private final JPanel contentPanel;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel loadingPanel;
    private final JPanel emptyPanel;

    private static final String CARD_GRAPH = "graph";
    private static final String CARD_LOADING = "loading";
    private static final String CARD_EMPTY = "empty";

    /** Cached so we only create a new DataManager when graph data or suggestion actually changed. */
    private int lastGraphDataItemId = -1;
    private Integer lastSuggestionPrice = null;
    private Boolean lastSuggestionWasBuy = null;

    @Inject
    public SidebarGraphPanel(SuggestionManager suggestionManager,
                             PriceGraphConfigManager configManager,
                             FlippingCopilotConfig config) {
        this.suggestionManager = suggestionManager;
        this.config = config;
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARKER_GRAY_COLOR);

        graphPanel = new GraphPanel(configManager, true);
        graphPanel.setPreferredSize(new Dimension(MainPanel.CONTENT_WIDTH, SIDEBAR_GRAPH_HEIGHT));
        graphPanel.setMinimumSize(new Dimension(MainPanel.CONTENT_WIDTH, SIDEBAR_GRAPH_HEIGHT));
        graphPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, SIDEBAR_GRAPH_HEIGHT));

        contentPanel = new JPanel(cardLayout);
        contentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        contentPanel.setPreferredSize(new Dimension(MainPanel.CONTENT_WIDTH, SIDEBAR_GRAPH_HEIGHT));
        contentPanel.setMinimumSize(new Dimension(MainPanel.CONTENT_WIDTH, SIDEBAR_GRAPH_HEIGHT));

        JPanel graphWrapper = new JPanel(new BorderLayout());
        graphWrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        graphWrapper.setOpaque(true);
        graphWrapper.add(graphPanel, BorderLayout.CENTER);
        contentPanel.add(graphWrapper, CARD_GRAPH);

        loadingPanel = buildLoadingPanel();
        contentPanel.add(loadingPanel, CARD_LOADING);

        emptyPanel = buildEmptyPanel();
        contentPanel.add(emptyPanel, CARD_EMPTY);

        add(contentPanel, BorderLayout.CENTER);
        cardLayout.show(contentPanel, CARD_EMPTY);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension pref = super.getPreferredSize();
        Container parent = getParent();
        if (parent != null && parent.getWidth() > 0) {
            int w = Math.max(parent.getWidth(), MainPanel.CONTENT_WIDTH);
            pref = new Dimension(w, pref.height);
        }
        return pref;
    }

    private JPanel buildLoadingPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setPreferredSize(new Dimension(MainPanel.CONTENT_WIDTH, SIDEBAR_GRAPH_HEIGHT));
        JLabel label = new JLabel("Loading price graph...");
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        Spinner spinner = new Spinner();
        spinner.show();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 8, 0);
        panel.add(spinner, gbc);
        gbc.gridy = 1;
        panel.add(label, gbc);
        return panel;
    }

    private JPanel buildEmptyPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setPreferredSize(new Dimension(MainPanel.CONTENT_WIDTH, SIDEBAR_GRAPH_HEIGHT));
        JLabel label = new JLabel("<html><center>Price graph for the<br>current suggestion<br>appears here</center></html>");
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(label);
        return panel;
    }

    private static PriceLine buildPriceLine(Suggestion suggestion) {
        if (suggestion == null) {
            return null;
        }
        if (suggestion.isBuySuggestion()) {
            return new PriceLine(
                    suggestion.getPrice(),
                    "Suggested buy price",
                    false
            );
        }
        if (suggestion.isSellSuggestion()) {
            return new PriceLine(
                    suggestion.getPrice(),
                    "Suggested sell price",
                    true
            );
        }
        return null;
    }

    public void refresh() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::refresh);
            return;
        }

        graphPanel.setSidebarTimeRange(config.sidebarGraphMinutesBefore(), config.sidebarGraphMinutesAfter());

        Suggestion suggestion = suggestionManager.getSuggestion();
        Data graphData = suggestionManager.getSuggestionGraphData();

        if (suggestionManager.isGraphDataReadingInProgress()) {
            cardLayout.show(contentPanel, CARD_LOADING);
            return;
        }

        if (suggestion != null && !suggestion.isWaitSuggestion()
                && graphData != null && graphData.itemId == suggestion.getItemId()) {
            int suggestionPrice = suggestion.getPrice();
            boolean suggestionIsBuy = suggestion.isBuySuggestion();
            boolean dataUnchanged = lastGraphDataItemId == graphData.itemId
                    && lastSuggestionPrice != null && lastSuggestionPrice == suggestionPrice
                    && lastSuggestionWasBuy != null && lastSuggestionWasBuy == suggestionIsBuy;

            if (!dataUnchanged) {
                lastGraphDataItemId = graphData.itemId;
                lastSuggestionPrice = suggestionPrice;
                lastSuggestionWasBuy = suggestionIsBuy;
                DataManager dm = new DataManager(graphData, null);
                PriceLine priceLine = buildPriceLine(suggestion);
                graphPanel.setData(dm, priceLine);
            }
            cardLayout.show(contentPanel, CARD_GRAPH);
        } else {
            lastGraphDataItemId = -1;
            lastSuggestionPrice = null;
            lastSuggestionWasBuy = null;
            cardLayout.show(contentPanel, CARD_EMPTY);
        }
    }
}
