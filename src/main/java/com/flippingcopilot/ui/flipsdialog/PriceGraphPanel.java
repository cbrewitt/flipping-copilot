package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.controller.ApiRequestHandler;
import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.manager.PriceGraphConfigManager;
import com.flippingcopilot.model.*;
import com.flippingcopilot.ui.UIUtilities;
import com.flippingcopilot.ui.components.ItemSearchBox;
import com.flippingcopilot.ui.components.TrackingCardLayout;
import com.flippingcopilot.ui.graph.*;
import com.flippingcopilot.ui.graph.model.Data;
import com.flippingcopilot.ui.graph.model.PriceLine;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

import static org.apache.commons.lang3.ObjectUtils.firstNonNull;

@Slf4j
public class PriceGraphPanel extends JPanel {

    // Dependencies
    private final ItemController itemController;
    private final ApiRequestHandler apiRequestHandler;
    private final OsrsLoginManager osrsLoginManager;
    private final PriceGraphConfigManager priceGraphConfigManager;
    private final SuggestionManager suggestionManager;

    // UI Components
    public final ItemSearchBox searchBox;
    private final JPanel contentPanel;
    private final JLabel errorLabel = new JLabel();
    private final GraphPanel graphPanel;
    private final StatsPanel statsPanel;
    private final TrackingCardLayout contentCardLayout = new TrackingCardLayout();
    private final JButton showSuggestionButton;

    // State
    private volatile int currentItemId;
    public volatile PriceLine offerPriceLine;


    // when isShowingSuggestionPriceData, the graph will auto update with the latest suggestion
    public volatile boolean isShowingSuggestionPriceData;
    public volatile Data suggestionPriceData;
    public volatile PriceLine suggestedPriceLine;


    public PriceGraphPanel(ItemController itemController,
                           PriceGraphConfigManager configManager,
                           FlippingCopilotConfig copilotConfig,
                           ApiRequestHandler apiRequestHandler,
                           OsrsLoginManager osrsLoginManager, SuggestionManager suggestionManager) {
        this.itemController = itemController;
        this.apiRequestHandler = apiRequestHandler;
        this.osrsLoginManager = osrsLoginManager;
        this.priceGraphConfigManager = configManager;
        this.suggestionManager = suggestionManager;

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

        // Add the suggestion button to the right side
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        showSuggestionButton = new JButton("Switch to suggested item");
        showSuggestionButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        showSuggestionButton.setFocusable(false);
        showSuggestionButton.setVisible(!isShowingSuggestionPriceData);
        showSuggestionButton.addActionListener(e -> {
            searchBox.clear();
            showSuggestionPriceGraph();
        });
        rightPanel.add(showSuggestionButton);

        contentPanel = new JPanel(contentCardLayout);

        try {
            JLabel gearButton = UIUtilities.gearButton("Graph Settings", ()-> {
                if(contentCardLayout.getCurrentCard().equals(Cards.SETTINGS_CARD.name())) {
                    contentCardLayout.showPrevious(contentPanel);
                } else {
                    contentCardLayout.show(contentPanel, Cards.SETTINGS_CARD.name());
                }
            });
            rightPanel.add(gearButton, BorderLayout.EAST);
        } catch (Exception e) {
            log.error("error creating graph settings button", e);
        }

        topPanel.add(rightPanel, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

        graphPanel = new GraphPanel(configManager);
        statsPanel = new StatsPanel(configManager, copilotConfig);
        statsPanel.setBackground(configManager.getConfig().backgroundColor);
        statsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        contentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        contentPanel.add(DialogUi.centeredMessage("Search for item to see price graph", ColorScheme.DARK_GRAY_COLOR, true, 16f), Cards.LANDING_CARD.name());
        contentPanel.add(DialogUi.centeredMessage("Log into game to use price graphs.", ColorScheme.DARK_GRAY_COLOR, true, 16f), Cards.LOGIN_PROMPT.name());
        contentPanel.add(DialogUi.loadingCard("Loading price data...", ColorScheme.DARK_GRAY_COLOR), Cards.LOADING_CARD.name());
        contentPanel.add(DialogUi.splitGraphCard(graphPanel, statsPanel), Cards.GRAPH_CARD.name());
        contentPanel.add(DialogUi.errorCard(errorLabel, () -> {
            if (currentItemId > 0) {
                onItemSelected(currentItemId);
            }
        }), Cards.ERROR_CARD.name());
        contentPanel.add(new ConfigPanel(priceGraphConfigManager, () -> contentCardLayout.showPrevious(contentPanel)), Cards.SETTINGS_CARD.name());

        add(contentPanel, BorderLayout.CENTER);

        contentCardLayout.show(contentPanel, Cards.LANDING_CARD.name());
    }

    private void onItemSelected(Integer itemId) {
        if (itemId == null) {
            return;
        }
        if(osrsLoginManager.getPlayerDisplayName() == null) {
            contentCardLayout.show(contentPanel, Cards.LOGIN_PROMPT.name());
            return;
        }
        offerPriceLine = null;
        isShowingSuggestionPriceData = false;
        showSuggestionButton.setVisible(suggestionPriceData != null || suggestionManager.isGraphDataReadingInProgress());
        currentItemId = itemId;
        log.debug("Loading price graph for item: {}", itemId);
        contentCardLayout.show(contentPanel, Cards.LOADING_CARD.name());
        Consumer<ItemPrice> consumer = (ItemPrice itemPrice) -> {
            SwingUtilities.invokeLater(() -> {
                String errorMessage = firstNonNull(itemPrice.getMessage(), "");
                if (!errorMessage.isEmpty()) {
                    showErrorCard(errorMessage);
                } else {
                    showGraphCard(new DataManager(itemPrice.getGraphData(), null), offerPriceLine);
                }
            });
        };
        apiRequestHandler.asyncGetItemPriceWithGraphData(itemId, "FlipCopilot", consumer, true);
    }

    public void setLoadingCard() {
        contentCardLayout.show(contentPanel, Cards.LOADING_CARD.name());
    }

    public void setSuggestionPriceData(Data d) {
        suggestionPriceData = d;
        if (isShowingSuggestionPriceData) {
            if (d == null || d.lowLatestTimes == null) {
                showLandingCard();
                return;
            }
            DataManager dm = new DataManager(d, null);
            showGraphCard(dm, suggestedPriceLine);
        }
    }

    private void showErrorCard(String errorMessage) {
        showSuggestionButton.setVisible(false);
        errorLabel.setText("<html><center>" + errorMessage + "</center></html>");
        contentCardLayout.show(contentPanel, Cards.ERROR_CARD.name());
    }

    private void showGraphCard(DataManager dm, PriceLine suggestedPriceLine) {
        showSuggestionButton.setVisible(true);
        graphPanel.setData(dm, suggestedPriceLine);
        contentCardLayout.show(contentPanel, Cards.GRAPH_CARD.name());
        statsPanel.populate(dm, itemController);
    }

    public void showLandingCard() {
        showSuggestionButton.setVisible(false);
        if(osrsLoginManager.getPlayerDisplayName() == null) {
            contentCardLayout.show(contentPanel, Cards.LOGIN_PROMPT.name());
            return;
        }
        contentCardLayout.show(contentPanel, Cards.LANDING_CARD.name());
    }

    public void onTabShown() {
        String currentCard = contentCardLayout.getCurrentCard();
        if(currentCard.equals(Cards.LANDING_CARD.name()) || currentCard.equals(Cards.LOGIN_PROMPT.name())) {
            showLandingCard();
        }
    }

    public void showSuggestionPriceGraph() {
        isShowingSuggestionPriceData = true;
        showSuggestionButton.setVisible(false);
        if (suggestionPriceData != null) {
            setLoadingCard();
            setSuggestionPriceData(suggestionPriceData);
        }
    }

    public void newSuggestedItemId(int itemId, PriceLine suggestedPriceLine) {
        this.suggestedPriceLine = suggestedPriceLine;
        if (suggestionPriceData != null && suggestionPriceData.itemId != itemId) {
            suggestionPriceData = null;
            if (isShowingSuggestionPriceData) {
                showSuggestionPriceGraph();
            }
        }
    }

    enum Cards {
        LANDING_CARD,
        LOGIN_PROMPT,
        GRAPH_CARD,
        LOADING_CARD,
        ERROR_CARD,
        SETTINGS_CARD,
    }
}
