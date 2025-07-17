package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.controller.FlippingCopilotConfig;
import com.flippingcopilot.manager.CopilotLoginManager;
import com.flippingcopilot.model.FlipManager;
import com.flippingcopilot.model.FlipV2;
import com.flippingcopilot.model.SessionManager;
import com.flippingcopilot.ui.components.AccountDropdown;
import com.flippingcopilot.ui.components.IntervalDropdown;
import com.flippingcopilot.ui.graph.model.Bounds;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import org.apache.commons.lang3.tuple.Pair;

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
    private int cachedIntervalStartTime = -1; // Default to ALL
    private Integer cachedAccountId = null;
    private List<ProfitGraphPanel.ProfitDataPoint> cachedDataPoints = new ArrayList<>();
    private Bounds cachedBounds = new Bounds(0, 0, 0, 0);

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

        intervalDropdown = new IntervalDropdown((units, value) -> refreshGraph(), IntervalDropdown.ALL_TIME, false);
        intervalDropdown.setPreferredSize(new Dimension(150, intervalDropdown.getPreferredSize().height));
        intervalDropdown.setToolTipText("Select time interval");

        accountDropdown = new AccountDropdown(
                copilotLoginManager::displayNameToAccountIdMap,
                accountId -> refreshGraph(),
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

        // Create graph panel with initial empty data
        graphPanel = new ProfitGraphPanel(config.profitAmountColor(), config.lossAmountColor());
        add(graphPanel, BorderLayout.CENTER);
        refreshGraph();
    }

    private void refreshGraph() {
        executorService.submit(() -> {
            try {
                // not fully initialised
                if(accountDropdown == null || intervalDropdown == null) {
                    return;
                }
                // Check if we need to regenerate the data
                Integer accountId = accountDropdown.getSelectedAccountId();
                int startTime = (int) IntervalDropdown.calculateStartTime(intervalDropdown.getSelectedIntervalTimeUnit(), intervalDropdown.getSelectedIntervalValue(), 0);
                boolean needsRegeneration = cachedDataPoints.isEmpty() ||
                        !Objects.equals(cachedAccountId, accountId) ||
                        cachedIntervalStartTime != startTime;

                if (needsRegeneration) {
                    log.debug("Regenerating profit data points");
                    ProfitAggregator aggregator = new ProfitAggregator();
                    cachedIntervalStartTime = startTime;
                    cachedAccountId = accountId;
                    flipManager.aggregateFlips(cachedIntervalStartTime, cachedAccountId, false, aggregator);
                    Pair<List<ProfitGraphPanel.ProfitDataPoint>, Bounds> data = aggregator.generateProfitDataPoints();
                    cachedDataPoints = data.getLeft();
                    cachedBounds = data.getRight();
                    log.debug("Generated {} profit data points", cachedDataPoints.size());
                }

                SwingUtilities.invokeLater(() -> {
                    graphPanel.setData(cachedDataPoints, cachedBounds);
                    graphPanel.repaint();
                });
            } catch (Exception e) {
                log.error("Error refreshing profit graph", e);
            }
        });
    }

    @AllArgsConstructor
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

        public Pair<List<ProfitGraphPanel.ProfitDataPoint>, Bounds> generateProfitDataPoints() {
            long now = Instant.now().getEpochSecond();
            List<ProfitGraphPanel.ProfitDataPoint> dataPoints = new ArrayList<>();

            if (dailyProfits.isEmpty()) {
                Bounds bounds = new Bounds((int) now, (int) now, 0, 1_000_000);
                dataPoints.add(new ProfitGraphPanel.ProfitDataPoint(now, 0));
                return Pair.of(dataPoints, bounds);
            }

            // Initialize bounds
            LocalDate firstDate = dailyProfits.keySet().iterator().next();
            long firstTimestamp = firstDate.atStartOfDay(zoneId).toEpochSecond();
            Bounds bounds = new Bounds((int) firstTimestamp, (int) now, 0, 0);

            long cumulativeProfit = 0;

            dataPoints.add(new ProfitGraphPanel.ProfitDataPoint(firstTimestamp, 0));

            for (Map.Entry<LocalDate, Long> entry : dailyProfits.entrySet()) {
                LocalDate date = entry.getKey();
                long dailyProfit = entry.getValue();

                long startOfDay = date.atStartOfDay(zoneId).toEpochSecond();
                dataPoints.add(new ProfitGraphPanel.ProfitDataPoint(startOfDay, cumulativeProfit));

                cumulativeProfit += dailyProfit;

                long endOfDay = date.atTime(23, 59, 59).atZone(zoneId).toEpochSecond();
                dataPoints.add(new ProfitGraphPanel.ProfitDataPoint(endOfDay, cumulativeProfit));

                bounds.yMin = Math.min(bounds.yMin, cumulativeProfit);
                bounds.yMax = Math.max(bounds.yMax, cumulativeProfit);
            }

            // todo: something weird going on with last timestamp > now - probably time zones issue
            ProfitGraphPanel.ProfitDataPoint last = dataPoints.get(dataPoints.size()-1);
            if(last.timestamp < now) {
                dataPoints.add(new ProfitGraphPanel.ProfitDataPoint(now, cumulativeProfit));
            } else {
                bounds.xMax = (int) last.timestamp;
            }
            bounds.yMax += (long) (0.1d * bounds.yDelta());
            if (bounds.yMax == 0) {
                bounds.yMax = 1_000_000;
            }
            return Pair.of(dataPoints, bounds);
        }
    }
}