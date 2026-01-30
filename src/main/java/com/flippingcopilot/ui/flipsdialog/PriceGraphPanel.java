package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.controller.ApiRequestHandler;
import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.manager.PriceGraphConfigManager;
import com.flippingcopilot.model.ItemPrice;
import com.flippingcopilot.model.OsrsLoginManager;
import com.flippingcopilot.ui.Spinner;
import com.flippingcopilot.ui.UIUtilities;
import com.flippingcopilot.ui.components.ItemSearchBox;
import com.flippingcopilot.ui.components.TrackingCardLayout;
import com.flippingcopilot.ui.graph.ConfigPanel;
import com.flippingcopilot.ui.graph.DataManager;
import com.flippingcopilot.ui.graph.GraphPanel;
import com.flippingcopilot.ui.graph.StatsPanel;
import com.flippingcopilot.ui.graph.model.Data;
import com.flippingcopilot.ui.graph.model.PriceLine;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

import static org.apache.commons.lang3.ObjectUtils.firstNonNull;

@Slf4j
public class PriceGraphPanel extends JPanel {

    // Dependencies
    private final ItemController itemController;
    private final ApiRequestHandler apiRequestHandler;
    private final OsrsLoginManager osrsLoginManager;
    private final PriceGraphConfigManager priceGraphConfigManager;

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
                           OsrsLoginManager osrsLoginManager, PriceGraphConfigManager priceGraphConfigManager) {
        this.itemController = itemController;
        this.apiRequestHandler = apiRequestHandler;
        this.osrsLoginManager = osrsLoginManager;
        this.priceGraphConfigManager = priceGraphConfigManager;

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
            BufferedImage gearIcon = ImageUtil.loadImageResource(getClass(), "/preferences-icon.png");
            gearIcon = ImageUtil.resizeImage(gearIcon, 20, 20);
            BufferedImage recoloredIcon = ImageUtil.recolorImage(gearIcon, ColorScheme.LIGHT_GRAY_COLOR);
            JLabel gearButton = UIUtilities.buildButton(recoloredIcon, "Graph Settings", ()-> {
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

        contentPanel.add(buildLandingCard(), Cards.LANDING_CARD.name());
        contentPanel.add(buildLogIntoGameCard(), Cards.LOGIN_PROMPT.name());
        contentPanel.add(buildLoadingCard(), Cards.LOADING_CARD.name());
        contentPanel.add(buildGraphCard(), Cards.GRAPH_CARD.name());
        contentPanel.add(buildErrorCard(), Cards.ERROR_CARD.name());
        contentPanel.add(buildSettingsCard(), Cards.SETTINGS_CARD.name());


        errorLabel.setForeground(Color.RED);
        errorLabel.setFont(errorLabel.getFont().deriveFont(14f));
        errorLabel.setHorizontalAlignment(SwingConstants.CENTER);

        add(contentPanel, BorderLayout.CENTER);

        contentCardLayout.show(contentPanel, Cards.LANDING_CARD.name());
    }

    private JPanel buildSettingsCard() {
        return new ConfigPanel(priceGraphConfigManager, () -> {
            contentCardLayout.showPrevious(contentPanel);
        });
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
        showSuggestionButton.setVisible(true);
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
            DataManager dm = new DataManager(d, null);
            showGraphCard(dm, suggestedPriceLine);
        }
    }

    private JPanel buildLogIntoGameCard() {
        JPanel landingCard = new JPanel(new GridBagLayout());
        landingCard.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JLabel emptyLabel = new JLabel("Log into game to use price graphs.");
        emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        emptyLabel.setFont(emptyLabel.getFont().deriveFont(16f));
        landingCard.add(emptyLabel);
        return landingCard;
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

    private JSplitPane buildGraphCard() {
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
        splitPane.setLeftComponent(graphPanel);
        splitPane.setRightComponent(statsPanel);
        splitPane.setResizeWeight(0.95); // Graph gets 75% of space
        splitPane.setDividerLocation(0.95);
        splitPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        return splitPane;
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
        setLoadingCard();
        if (suggestionPriceData != null) {
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