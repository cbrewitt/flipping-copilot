package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.manager.CopilotLoginManager;
import com.flippingcopilot.model.FlipManager;
import com.flippingcopilot.model.FlipV2;
import com.flippingcopilot.model.SessionManager;
import com.flippingcopilot.ui.components.AccountDropdown;
import com.flippingcopilot.ui.components.IntervalDropdown;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

import javax.inject.Named;
import javax.swing.*;
import java.awt.*;
import java.time.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

@Slf4j
public class ProfitPanel extends JPanel {

    // dependencies
    private final FlipManager flipManager;
    private final ExecutorService executorService;

    private final ProfitGraphPanel graphPanel;
    private final AccountDropdown accountDropdown;
    private final IntervalDropdown intervalDropdown;

    // State
    private int cachedIntervalStartTime = 1; // Default to ALL
    private Integer cachedAccountId = null;
    private List<Datapoint> cachedDatapoints = new ArrayList<>();

    public ProfitPanel(FlipManager flipManager,
                       @Named("copilotExecutor") ExecutorService executorService,
                       SessionManager sessionManager,
                       CopilotLoginManager copilotLoginManager,
                       FlippingCopilotConfig config) {
        this.flipManager = flipManager;
        this.executorService = executorService;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // Create top panel with controls
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Create left panel with dropdowns
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        intervalDropdown = new IntervalDropdown((units, value) -> refreshGraph(false), IntervalDropdown.ALL_TIME, false);
        intervalDropdown.setPreferredSize(new Dimension(150, intervalDropdown.getPreferredSize().height));
        intervalDropdown.setToolTipText("Select time interval");

        accountDropdown = new AccountDropdown(
                copilotLoginManager::displayNameToAccountIdMap,
                accountId -> refreshGraph(false),
                AccountDropdown.ALL_ACCOUNTS_DROPDOWN_OPTION
        );
        accountDropdown.setPreferredSize(new Dimension(120, accountDropdown.getPreferredSize().height));
        accountDropdown.setToolTipText("Select account");
        accountDropdown.refresh();

        leftPanel.add(intervalDropdown);
        leftPanel.add(Box.createRigidArea(new Dimension(3, 0)));
        leftPanel.add(accountDropdown);

        topPanel.add(leftPanel, BorderLayout.WEST);
        add(topPanel, BorderLayout.NORTH);

        // Create a panel to hold both graphs
        JPanel graphsPanel = new JPanel();
        graphsPanel.setLayout(new BoxLayout(graphsPanel, BoxLayout.Y_AXIS));
        graphsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // Create graph panels
        graphPanel = new ProfitGraphPanel(config.profitAmountColor(), config.lossAmountColor());
        graphsPanel.add(graphPanel);

        // Add the graphs panel to a scroll pane in case they don't fit
        JScrollPane scrollPane = new JScrollPane(graphsPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);

        add(scrollPane, BorderLayout.CENTER);
    }

    public void refreshGraph(boolean forceRecalculate) {
        executorService.submit(() -> {
            try {
                // not fully initialised
                if(accountDropdown == null || intervalDropdown == null) {
                    return;
                }
                accountDropdown.refresh();
                // Check if we need to regenerate the data
                Integer accountId = accountDropdown.getSelectedAccountId();
                int startTime = (int) IntervalDropdown.calculateStartTime(intervalDropdown.getSelectedIntervalTimeUnit(), intervalDropdown.getSelectedIntervalValue(), 0);
                boolean needsRegeneration = forceRecalculate || cachedDatapoints.isEmpty() ||
                        !Objects.equals(cachedAccountId, accountId) ||
                        cachedIntervalStartTime != startTime;

                if (needsRegeneration) {
                    log.debug("Regenerating profit data points");
                    ProfitAggregator aggregator = new ProfitAggregator();
                    cachedIntervalStartTime = startTime;
                    cachedAccountId = accountId;
                    flipManager.aggregateFlips(cachedIntervalStartTime, cachedAccountId, false, aggregator);
                    cachedDatapoints = aggregator.generateProfitDataPoints();
                    log.debug("Generated {} profit data points and {} daily profits", cachedDatapoints.size(), cachedDatapoints.size());
                }

                SwingUtilities.invokeLater(() -> {
                    graphPanel.setData(cachedDatapoints);
                    graphPanel.repaint();
                });
            } catch (Exception e) {
                log.error("Error refreshing profit graph", e);
            }
        });
    }

    @NoArgsConstructor
    private static class ProfitAggregator implements Consumer<FlipV2> {
        private final Map<LocalDate, Long> dailyProfits = new TreeMap<>();
        private final ZoneId zoneId = ZoneId.systemDefault();

        @Override
        public void accept(FlipV2 flip) {
            if (flip.getClosedQuantity() > 0) {
                LocalDate flipDate = LocalDate.ofInstant(Instant.ofEpochSecond(flip.getClosedTime()), zoneId);
                dailyProfits.merge(flipDate, flip.getProfit(), Long::sum);
            }
        }

        public List<Datapoint> generateProfitDataPoints() {
            List<Datapoint> dataPoints = new ArrayList<>();
            if (dailyProfits.isEmpty()) {
                dataPoints.add(new Datapoint(LocalDate.now(zoneId), 0L, 0L));
            }
            long cumulativeProfit = 0;
            for (Map.Entry<LocalDate, Long> entry : dailyProfits.entrySet()) {
                long dailyProfit = entry.getValue();
                cumulativeProfit += dailyProfit;
                dataPoints.add(new Datapoint(entry.getKey(), cumulativeProfit, dailyProfit));
            }
            return dataPoints;
        }
    }
}