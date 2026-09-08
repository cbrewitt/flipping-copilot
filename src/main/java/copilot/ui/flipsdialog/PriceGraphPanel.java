package copilot.ui.flipsdialog;

import copilot.ui.graph.model.Data;
import copilot.ui.components.*;
import copilot.controller.*;
import static copilot.ui.UIUtilities.*;
import static net.runelite.client.ui.ColorScheme.*;
import copilot.config.*;
import copilot.manager.*;
import copilot.model.*;
import copilot.ui.graph.*;
import copilot.ui.graph.model.*;
import lombok.extern.slf4j.*;

import javax.swing.*;
import java.awt.*;
import java.util.function.*;

import static org.apache.commons.lang3.ObjectUtils.firstNonNull;

@Slf4j
public class PriceGraphPanel extends JPanel {

    // Dependencies
    private final Items items;
    private final ApiClient api;
    private final PlayerLogin login;
    private final GraphSettings priceGraphConfigManager;
    private final Suggestions suggestions;

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

    public PriceGraphPanel(Items items,
                           GraphSettings configManager,
                           CopilotConfig copilotConfig,
                           ApiClient api,
                           PlayerLogin login, Suggestions suggestions) {
        this.items = items; this.api = api; this.login = login; priceGraphConfigManager = configManager;
        this.suggestions = suggestions;

        setLayout(new BorderLayout());
        setBackground(DARK_GRAY_COLOR);

        var topPanel = darkPanel(new BorderLayout(), DARK_GRAY_COLOR);
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        var searchPanel = darkPanel(new FlowLayout(FlowLayout.LEFT, 10, 0), DARK_GRAY_COLOR);

        var searchLabel = coloredLabel("Search item:", LIGHT_GRAY_COLOR);
        searchPanel.add(searchLabel);

        searchBox = new ItemSearchBox(
                (searchText, ignoredSet) -> items.search(searchText, items.allItemIds()),
                this::onItemSelected
        );
        searchBox.setPreferredSize(new Dimension(300, 30));
        searchPanel.add(searchBox);

        topPanel.add(searchPanel, BorderLayout.WEST);

        // Add the suggestion button to the right side
        var rightPanel = darkPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0), DARK_GRAY_COLOR);

        showSuggestionButton = new JButton("Switch to suggested item");
        showSuggestionButton.setBackground(DARKER_GRAY_COLOR); showSuggestionButton.setFocusable(false);
        showSuggestionButton.setVisible(!isShowingSuggestionPriceData);
        showSuggestionButton.addActionListener(e -> {
            searchBox.clear();
            showSuggestionPriceGraph();
        });
        rightPanel.add(showSuggestionButton);

        contentPanel = new JPanel(contentCardLayout);

        try {
            var gearButton = gearButton("Graph Settings", ()-> {
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

        contentPanel.setBackground(DARK_GRAY_COLOR);

        contentPanel.add(DialogUi.centeredMessage("Search for item to see price graph", DARK_GRAY_COLOR, true, 16f), Cards.LANDING_CARD.name());
        contentPanel.add(DialogUi.centeredMessage("Log into game to use price graphs.", DARK_GRAY_COLOR, true, 16f), Cards.LOGIN_PROMPT.name());
        contentPanel.add(DialogUi.loadingCard("Loading price data...", DARK_GRAY_COLOR), Cards.LOADING_CARD.name());
        contentPanel.add(DialogUi.splitGraphCard(graphPanel, statsPanel), Cards.GRAPH_CARD.name());
        contentPanel.add(DialogUi.errorCard(errorLabel, () -> {
            if (currentItemId > 0) { onItemSelected(currentItemId); }
        }), Cards.ERROR_CARD.name());
        contentPanel.add(new ConfigPanel(priceGraphConfigManager, () -> contentCardLayout.showPrevious(contentPanel)), Cards.SETTINGS_CARD.name());

        add(contentPanel, BorderLayout.CENTER);

        contentCardLayout.show(contentPanel, Cards.LANDING_CARD.name());
    }

    private void onItemSelected(Integer itemId) {
        if (itemId == null) { return; }
        if(login.getPlayerDisplayName() == null) {
            contentCardLayout.show(contentPanel, Cards.LOGIN_PROMPT.name());
            return;
        }
        offerPriceLine = null;
        isShowingSuggestionPriceData = false;
        showSuggestionButton.setVisible(suggestionPriceData != null || suggestions.isGraphDataReadingInProgress());
        currentItemId = itemId;
        log.debug("Loading price graph for item: {}", itemId);
        contentCardLayout.show(contentPanel, Cards.LOADING_CARD.name());
        Consumer<ItemPrice> consumer = (ItemPrice itemPrice) -> {
            SwingUtilities.invokeLater(() -> {
                String errorMessage = firstNonNull(itemPrice.getMessage(), "");
                if (!errorMessage.isEmpty()) { showErrorCard(errorMessage); } else {
                    showGraphCard(new DataManager(itemPrice.getGraphData(), null), offerPriceLine);
                }
            });
        };
        api.asyncGetItemPriceWithGraphData(itemId, "FlipCopilot", consumer, true);
    }

    public void setLoadingCard() { contentCardLayout.show(contentPanel, Cards.LOADING_CARD.name()); }

    public void setSuggestionPriceData(Data d) {
        suggestionPriceData = d;
        if (isShowingSuggestionPriceData) {
            if (d == null || d.lowLatestTimes == null) {
                showLandingCard();
                return;
            }
            var dm = new DataManager(d, null);
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
        statsPanel.populate(dm, items);
    }

    public void showLandingCard() {
        showSuggestionButton.setVisible(false);
        if(login.getPlayerDisplayName() == null) {
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
            if (isShowingSuggestionPriceData) { showSuggestionPriceGraph(); }
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
