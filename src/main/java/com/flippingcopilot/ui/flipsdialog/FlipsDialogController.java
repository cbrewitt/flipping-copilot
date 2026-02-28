package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.controller.ApiRequestHandler;
import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.manager.PriceGraphConfigManager;
import com.flippingcopilot.model.*;
import com.flippingcopilot.rs.CopilotLoginRS;
import com.flippingcopilot.rs.OsrsLoginRS;
import com.flippingcopilot.ui.graph.model.PriceLine;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@Singleton
public class FlipsDialogController {

    private final ItemController itemController;
    private final FlipManager flipsManager;
    private final ExecutorService executorService;
    private final SessionManager sessionManager;
    private final CopilotLoginRS copilotLoginRS;
    private final FlippingCopilotConfig config;
    private final ApiRequestHandler apiRequestHandler;
    private final PriceGraphConfigManager priceGraphConfigManager;
    private final OsrsLoginManager osrsLoginManager;
    private final SuggestionManager suggestionManager;
    private final OsrsLoginRS osrsLoginRS;

    public PriceGraphPanel priceGraphPanel;
    private JTabbedPane tabbedPane;
    private JDialog dialog;
    private FlipsPanel flipsPanel;

    @Inject
    public FlipsDialogController(
            @Named("copilotExecutor") ScheduledExecutorService executorService,
            ItemController itemController,
            FlipManager flipsManager,
            SessionManager sessionManager,
            CopilotLoginRS copilotLoginRS,
            FlippingCopilotConfig config,
            ApiRequestHandler apiRequestHandler,
            PriceGraphConfigManager priceGraphConfigManager, OsrsLoginManager osrsLoginManager, SuggestionManager suggestionManager, OsrsLoginRS osrsLoginRS) {
        this.itemController = itemController;
        this.flipsManager = flipsManager;
        this.executorService = executorService;
        this.sessionManager = sessionManager;
        this.copilotLoginRS = copilotLoginRS;
        this.config = config;
        this.apiRequestHandler = apiRequestHandler;
        this.priceGraphConfigManager = priceGraphConfigManager;
        this.osrsLoginManager = osrsLoginManager;
        this.suggestionManager = suggestionManager;
        this.osrsLoginRS = osrsLoginRS;
    }

    public void initDialog(Window windowAncestor) {
        SwingUtilities.invokeLater(() -> {
            tabbedPane = new JTabbedPane();
            tabbedPane.setBackground(ColorScheme.DARK_GRAY_COLOR);

            VisualizeFlipPanel visualizeFlipPanel = new VisualizeFlipPanel(
                    itemController,
                    priceGraphConfigManager,
                    config,
                    apiRequestHandler
            );
            flipsPanel = new FlipsPanel(osrsLoginRS, flipsManager, itemController, copilotLoginRS,
                    executorService, config, apiRequestHandler, (f) -> {
                visualizeFlipPanel.showFlipVisualization(f);
                tabbedPane.setSelectedIndex(6);
            });
            ItemAggregatePanel itemsPanel = new ItemAggregatePanel(flipsManager, itemController,
                    copilotLoginRS, executorService, config);
            AccountsAggregatePanel accountsPanel = new AccountsAggregatePanel(flipsManager, copilotLoginRS,
                    executorService, config, apiRequestHandler, flipsManager);
            ProfitPanel profitPanel = new ProfitPanel(flipsManager, executorService, sessionManager,
                    copilotLoginRS, config);
            TransactionsPanel transactionsPanel = new TransactionsPanel(copilotLoginRS, itemController,
                    executorService, apiRequestHandler, config, flipsManager);
            priceGraphPanel = new PriceGraphPanel(
                    itemController,
                    priceGraphConfigManager,
                    config,
                    apiRequestHandler,
                    osrsLoginManager,
                    priceGraphConfigManager,
                    suggestionManager
            );
            tabbedPane.addTab("Flips", flipsPanel);
            tabbedPane.addTab("Items", itemsPanel);
            tabbedPane.addTab("Accounts", accountsPanel);
            tabbedPane.addTab("Profit graph", profitPanel);
            tabbedPane.addTab("Transactions", transactionsPanel);
            tabbedPane.addTab("Price graph", priceGraphPanel);
            tabbedPane.addTab("Visualize flip", visualizeFlipPanel);


            JDialog dialog = new JDialog(windowAncestor);
            dialog.setTitle("Flipping Copilot");
            dialog.setResizable(true);
            dialog.setMinimumSize(new Dimension(800, 600));


            tabbedPane.addChangeListener(e -> {
                int selectedIndex = tabbedPane.getSelectedIndex();
                switch (selectedIndex) {
                    case 0:
                        flipsPanel.onTabShown();
                        break;
                    case 1:
                        itemsPanel.onTabShown();
                        break;
                    case 2:
                        accountsPanel.onTabShown();
                        break;
                    case 3:
                        profitPanel.refreshGraph(true);
                    case 4:
                        transactionsPanel.loadTransactionsIfNeeded();
                        break;
                    case 5:
                        priceGraphPanel.onTabShown();
                        break;
                }
            });
            dialog.setContentPane(tabbedPane);

            GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
            Rectangle bounds = env.getMaximumWindowBounds(); // Excludes taskbar
            dialog.setSize(bounds.width, bounds.height);
            dialog.setLocation(bounds.x, bounds.y);

            this.dialog = dialog;
            dialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
            dialog.setModalityType(Dialog.ModalityType.MODELESS);
            dialog.setVisible(false);
        });
    }

    public void showPriceGraphTab(Integer openOnPriceGraphItemId, boolean suggestionPriceGraph, PriceLine priceLine) {
        tabbedPane.setSelectedIndex(5);
        if(openOnPriceGraphItemId != null) {
            priceGraphPanel.isShowingSuggestionPriceData = false;
            priceGraphPanel.searchBox.setItem(new ItemIdName(openOnPriceGraphItemId, itemController.getItemName(openOnPriceGraphItemId)));
            priceGraphPanel.offerPriceLine = priceLine;
        } else if (suggestionPriceGraph)  {
            priceGraphPanel.showSuggestionPriceGraph();
        } else {
            priceGraphPanel.showLandingCard();
        }
        dialog.setVisible(true);
    }


    public void showFlipsTab() {
        tabbedPane.setSelectedIndex(0);
        dialog.setVisible(true);
        flipsPanel.onTabShown();
    }
}