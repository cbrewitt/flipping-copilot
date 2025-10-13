package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.controller.ApiRequestHandler;
import com.flippingcopilot.controller.FlippingCopilotConfig;
import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.manager.PriceGraphConfigManager;
import com.flippingcopilot.model.ItemPrice;
import com.flippingcopilot.ui.Spinner;
import com.flippingcopilot.ui.components.ItemSearchBox;
import com.flippingcopilot.ui.graph.DataManager;
import com.flippingcopilot.ui.graph.GraphPanel;
import com.flippingcopilot.ui.graph.StatsPanel;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.Consumer;

import static org.apache.commons.lang3.ObjectUtils.firstNonNull;

@Slf4j
public class PriceGraphPanel extends JPanel {

    // Dependencies
    private final ItemController itemController;
    private final ApiRequestHandler apiRequestHandler;

    // UI Components
    private final ItemSearchBox searchBox;
    private final JPanel contentPanel;
    private final JLabel errorLabel = new JLabel();
    private final GraphPanel graphPanel;
    private final StatsPanel statsPanel;
    private final CardLayout contentCardLayout = new CardLayout();

    // State
    private volatile int currentItemId;

    public PriceGraphPanel(ItemController itemController,
                           PriceGraphConfigManager configManager,
                           FlippingCopilotConfig copilotConfig,
                           ApiRequestHandler apiRequestHandler) {
        this.itemController = itemController;
        this.apiRequestHandler = apiRequestHandler;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        searchPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel searchLabel = new JLabel("Search item:");
        searchLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        searchPanel.add(searchLabel);

        searchBox = new ItemSearchBox(
                (searchText, ignoredSet) -> itemController.search(searchText, itemController.allItemIds()),
                this::onItemSelected
        );
        searchBox.setPreferredSize(new Dimension(300, 30));
        searchPanel.add(searchBox);


        topPanel.add(searchPanel, BorderLayout.WEST);
        add(topPanel, BorderLayout.NORTH);

        graphPanel = new GraphPanel(configManager);
        statsPanel = new StatsPanel(configManager, copilotConfig);
        statsPanel.setBackground(configManager.getConfig().backgroundColor);
        statsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        contentPanel = new JPanel(contentCardLayout);
        contentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        contentPanel.add(buildLandingCard(), Cards.LANDING_CARD.name());
        contentPanel.add(buildLoadingCard(), Cards.LOADING_CARD.name());
        contentPanel.add(buildGraphCard(), Cards.GRAPH_CARD.name());
        contentPanel.add(buildErrorCard(), Cards.ERROR_CARD.name());


        errorLabel.setForeground(Color.RED);
        errorLabel.setFont(errorLabel.getFont().deriveFont(14f));
        errorLabel.setHorizontalAlignment(SwingConstants.CENTER);

        add(contentPanel, BorderLayout.CENTER);

        contentCardLayout.show(contentPanel, Cards.LANDING_CARD.name());
    }

    private void onItemSelected(Integer itemId) {
        if (itemId == null) {
            return;
        }
        currentItemId = itemId;
        log.debug("Loading price graph for item: {}", itemId);
        contentCardLayout.show(contentPanel, Cards.LOADING_CARD.name());
        Consumer<ItemPrice> consumer = (ItemPrice itemPrice) -> {
            SwingUtilities.invokeLater(() -> {
                String errorMessage = firstNonNull(itemPrice.getMessage(), "");
                if (!errorMessage.isEmpty()) {
                    showErrorCard(errorMessage);
                } else {
                    showGraphCard(new DataManager(itemPrice.getGraphData(), null));
                }
            });
        };
        apiRequestHandler.asyncGetItemPriceWithGraphData(itemId, "FlipCopilot", consumer, true);
    }

    private JPanel buildLandingCard() {
        JPanel landingCard = new JPanel(new GridBagLayout());
        landingCard.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JLabel emptyLabel = new JLabel("Search for an item to view its price graph");
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
            if (currentItemId > 0) {
                onItemSelected(currentItemId);
            }
        });
        errorPanel.add(retryButton, gbc);
        return errorPanel;
    }

    private void showErrorCard(String errorMessage) {
        errorLabel.setText("<html><center>" + errorMessage + "</center></html>");
        contentCardLayout.show(contentPanel, Cards.ERROR_CARD.name());
    }

    private void showGraphCard(DataManager dm) {
        graphPanel.setData(dm);
        contentCardLayout.show(contentPanel, Cards.GRAPH_CARD.name());
        statsPanel.populate(dm, itemController);
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

    public void onPanelShown() {
        SwingUtilities.invokeLater(searchBox::requestFocusInWindow);
    }

    enum Cards {
        LANDING_CARD,
        GRAPH_CARD,
        LOADING_CARD,
        ERROR_CARD
    }
}