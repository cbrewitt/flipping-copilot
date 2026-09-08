package copilot.ui.flipsdialog;

import com.google.inject.name.Named;
import java.util.concurrent.*;
import copilot.controller.*;
import copilot.config.*;
import copilot.manager.*;
import copilot.model.*;
import copilot.rs.*;
import copilot.ui.graph.model.*;
import com.google.inject.name.*;
import lombok.extern.slf4j.*;
import net.runelite.client.callback.*;
import net.runelite.client.ui.*;
import net.runelite.client.util.*;

import javax.inject.*;
import javax.swing.*;
import java.awt.*;

@Slf4j
@Singleton
public class FlipsDialogController {

    private final Items items;
    private final FlipManager flipsManager;
    private final ExecutorService executor;
    private final CopilotLogin copilotLogin;
    private final CopilotConfig config;
    private final ApiClient api;
    private final GraphSettings priceGraphConfigManager;
    private final PlayerLogin login;
    private final Suggestions suggestions;
    private final GameLogin gameLogin;
    private final PortfolioStateRS portfolio;
    private final BankStateRS bank;
    private final GeHistoryStateRS history;
    private final ClientThread clientThread;

    public PriceGraphPanel priceGraphPanel;
    private JTabbedPane tabbedPane;
    private JDialog dialog;
    private FlipsPanel flipsPanel;
    private MissedFlipsPanel missedFlipsPanel;
    private VisualizeFlipPanel visualizeFlipPanel;

    @Inject
    public FlipsDialogController(
            @Named("copilotExecutor") ScheduledExecutorService executor,
            Items items,
            FlipManager flipsManager,
            CopilotLogin copilotLogin,
            CopilotConfig config,
            ApiClient api,
            GraphSettings priceGraphConfigManager,
            PlayerLogin login,
            Suggestions suggestions,
            GameLogin gameLogin,
            PortfolioStateRS portfolio,
            BankStateRS bank,
            GeHistoryStateRS history,
            ClientThread clientThread) {
        this.items = items; this.flipsManager = flipsManager; this.executor = executor;
        this.copilotLogin = copilotLogin; this.config = config; this.api = api;
        this.priceGraphConfigManager = priceGraphConfigManager; this.login = login; this.suggestions = suggestions;
        this.gameLogin = gameLogin; this.portfolio = portfolio; this.bank = bank; this.history = history;
        this.clientThread = clientThread;
    }

    public void initDialog(Window windowAncestor) {
        SwingUtilities.invokeLater(() -> {
            tabbedPane = new JTabbedPane();
            tabbedPane.setBackground(ColorScheme.DARK_GRAY_COLOR);

            visualizeFlipPanel = new VisualizeFlipPanel(items, priceGraphConfigManager, config, api );
            flipsPanel = new FlipsPanel(flipsManager, items, copilotLogin,
                    executor, config, api, (f) -> {
                showVisualizeFlip(f);
            });
            missedFlipsPanel = new MissedFlipsPanel(gameLogin, flipsManager, items, copilotLogin,
                    executor, config, api, history);
            var itemsPanel = new ItemAggregatePanel(flipsManager, items, copilotLogin, executor, config);
            var accountsPanel = new AccountsAggregatePanel(copilotLogin, executor, config, api, flipsManager);
            var profitPanel = new ProfitPanel(flipsManager, executor, copilotLogin, config);
            var portfolioPanel = new PortfolioPanel(
                    items,
                    config,
                    api,
                    suggestions,
                    copilotLogin,
                    gameLogin,
                    portfolio,
                    bank,
                    clientThread,
                    itemId -> showPriceGraphTab(itemId, false, null)
            );
            var transactionsPanel = new TransactionsPanel(copilotLogin, items,
                    executor, api, login, config, flipsManager);
            priceGraphPanel = new PriceGraphPanel(items, priceGraphConfigManager, config, api, login, suggestions );
            tabbedPane.addTab("Portfolio", portfolioPanel);
            tabbedPane.addTab("Flips", flipsPanel);
            tabbedPane.addTab("Items", itemsPanel);
            tabbedPane.addTab("Accounts", accountsPanel);
            tabbedPane.addTab("Profit graph", profitPanel);
            tabbedPane.addTab("Transactions", transactionsPanel);
            tabbedPane.addTab("Price graph", priceGraphPanel);
            tabbedPane.addTab("Visualize flip", visualizeFlipPanel);
            tabbedPane.addTab("Missed flips", missedFlipsPanel);

            var dialog = new JDialog(windowAncestor);
            dialog.setTitle("Flipping Copilot"); dialog.setResizable(true);
            dialog.setMinimumSize(new Dimension(800, 600));

            tabbedPane.addChangeListener(e -> {
                int selectedIndex = tabbedPane.getSelectedIndex();
                switch (selectedIndex) {
                    case 0: portfolioPanel.onTabShown(); break;
                    case 1: flipsPanel.onTabShown(); break;
                    case 2: itemsPanel.onTabShown(); break;
                    case 3: accountsPanel.onTabShown(); break;
                    case 4: profitPanel.refreshGraph(true); break;
                    case 5: transactionsPanel.loadTransactionsIfNeeded(); break;
                    case 6: priceGraphPanel.onTabShown(); break;
                    case 8: missedFlipsPanel.onTabShown(); break;
                }
            });
            dialog.setContentPane(tabbedPane);

            var env = GraphicsEnvironment.getLocalGraphicsEnvironment();
            Rectangle bounds = env.getMaximumWindowBounds(); // Excludes taskbar
            dialog.setSize(bounds.width, bounds.height); dialog.setLocation(bounds.x, bounds.y);

            this.dialog = dialog;
            dialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
            dialog.setModalityType(Dialog.ModalityType.MODELESS); dialog.setVisible(false);
        });
    }

    public void showPriceGraphTab(Integer openOnPriceGraphItemId, boolean suggestionPriceGraph, PriceLine priceLine) {
        tabbedPane.setSelectedIndex(6);
        if(openOnPriceGraphItemId != null) {
            priceGraphPanel.isShowingSuggestionPriceData = false;
            priceGraphPanel.searchBox.setItem(new ItemIdName(openOnPriceGraphItemId, items.getItemName(openOnPriceGraphItemId)));
            priceGraphPanel.offerPriceLine = priceLine;
        } else if (suggestionPriceGraph)  {
            priceGraphPanel.showSuggestionPriceGraph();
        } else {
            priceGraphPanel.showLandingCard();
        }
        dialog.setVisible(true);
    }

    public void openSuggestionPriceGraph() {
        Suggestion suggestion = suggestions.getSuggestion();
        if (config.priceGraphWebsite().equals(CopilotConfig.PriceGraphWebsite.FLIPPING_COPILOT)) {
            if (isSuggestionWithoutGraphData(suggestion)) { showPriceGraphTab(suggestion.itemId, false, null); } else if (suggestion != null && !suggestion.isWaitSuggestion()) {
                showPriceGraphTab(null, true, null);
            } else {
                showPriceGraphTab(null, false, null);
            }
            return;
        }

        if (suggestion == null || suggestion.isWaitSuggestion()) { return; }
        String url = config.priceGraphWebsite().getUrl(suggestion.name, suggestion.itemId);
        LinkBrowser.browse(url);
    }

    private boolean isSuggestionWithoutGraphData(Suggestion suggestion) {
        return suggestion != null && !suggestion.isWaitSuggestion() && suggestion.isDumpAlert;
    }

    public void showPortfolioTab() {
        tabbedPane.setSelectedIndex(0);
        dialog.setVisible(true);
    }

    public void showVisualizeFlip(Flip flip) {
        if (flip == null) { return; }
        visualizeFlipPanel.showFlipVisualization(flip);
        tabbedPane.setSelectedIndex(7);
        dialog.setVisible(true);
    }
}
