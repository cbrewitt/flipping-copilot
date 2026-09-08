package copilot.ui.flipsdialog;

import copilot.ui.components.*;
import copilot.model.*;
import static copilot.ui.UIUtilities.*;
import static net.runelite.client.ui.ColorScheme.*;
import copilot.config.*;
import copilot.rs.*;
import lombok.*;
import lombok.extern.slf4j.*;

import javax.inject.*;
import javax.swing.*;
import java.awt.*;
import java.time.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.*;

@Slf4j
public class ProfitPanel extends JPanel {

    // dependencies
    private final FlipManager flips;
    private final ExecutorService executor;

    private final ProfitGraphPanel graphPanel;
    private final AccountDropdown accountDropdown;
    private final IntervalDropdown intervalDropdown;

    // State
    private int cachedIntervalStartTime = 1; // Default to ALL
    private Integer cachedAccountId = null;
    private List<Datapoint> cachedDatapoints = new ArrayList<>();

    public ProfitPanel(FlipManager flips,
                       @Named("copilotExecutor") ExecutorService executor,
                       CopilotLogin copilotLoginRS,
                       CopilotConfig config) {
        this.flips = flips; this.executor = executor;

        setLayout(new BorderLayout());
        setBackground(DARKER_GRAY_COLOR);

        // Create top panel with controls
        var topPanel = darkPanel(new BorderLayout(), DARK_GRAY_COLOR);
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Create left panel with dropdowns
        var leftPanel = darkPanel(new FlowLayout(FlowLayout.LEFT, 0, 0), DARK_GRAY_COLOR);

        intervalDropdown = DialogUi.intervalDropdown((units, value) -> refreshGraph(false));

        accountDropdown = DialogUi.accountDropdown(() -> copilotLoginRS.get().displayNameToAccountId, accountId -> refreshGraph(false));
        accountDropdown.refresh();

        leftPanel.add(intervalDropdown);
        addHorizontalGap(leftPanel, 3);
        leftPanel.add(accountDropdown);

        topPanel.add(leftPanel, BorderLayout.WEST);
        add(topPanel, BorderLayout.NORTH);

        // Create a panel to hold both graphs
        var graphsPanel = new JPanel();
        graphsPanel.setLayout(new BoxLayout(graphsPanel, BoxLayout.Y_AXIS));
        graphsPanel.setBackground(DARKER_GRAY_COLOR);

        // Create graph panels
        graphPanel = new ProfitGraphPanel(config.profitAmountColor(), config.lossAmountColor());
        graphsPanel.add(graphPanel);

        // Add the graphs panel to a scroll pane in case they don't fit
        var scrollPane = new JScrollPane(graphsPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER); scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(DARKER_GRAY_COLOR);

        add(scrollPane, BorderLayout.CENTER);
    }

    public void refreshGraph(boolean forceRecalculate) {
        executor.submit(() -> {
            try {
                // not fully initialised
                if (accountDropdown == null || intervalDropdown == null) { return; }
                accountDropdown.refresh();
                // Check if we need to regenerate the data
                Integer accountId = accountDropdown.getSelectedAccountId();
                int startTime = (int) IntervalDropdown.calculateStartTime(intervalDropdown.getSelectedIntervalTimeUnit(), intervalDropdown.getSelectedIntervalValue(), 0);
                boolean needsRegeneration = forceRecalculate || cachedDatapoints.isEmpty() ||
                        !Objects.equals(cachedAccountId, accountId) ||
                        cachedIntervalStartTime != startTime;

                if (needsRegeneration) {
                    var aggregator = new ProfitAggregator();
                    cachedIntervalStartTime = startTime;
                    cachedAccountId = accountId;
                    flips.aggregateFlips(cachedIntervalStartTime, cachedAccountId, false, aggregator);
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
    private static class ProfitAggregator implements Consumer<Flip> {
        private final Map<LocalDate, Long> dailyProfits = new TreeMap<>();
        private final ZoneId zoneId = ZoneId.systemDefault();

        @Override
        public void accept(Flip flip) {
            if (flip.closedQuantity > 0) {
                var flipDate = LocalDate.ofInstant(Instant.ofEpochSecond(flip.closedTime), zoneId);
                dailyProfits.merge(flipDate, flip.profit, Long::sum);
            }
        }

        public List<Datapoint> generateProfitDataPoints() {
            List<Datapoint> dataPoints = new ArrayList<>();
            if (dailyProfits.isEmpty()) { dataPoints.add(new Datapoint(LocalDate.now(zoneId), 0L, 0L)); }
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