package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.model.FlipManager;
import com.flippingcopilot.model.FlipV2;
import com.flippingcopilot.model.SessionManager;
import com.flippingcopilot.ui.components.DisplayNameDropdown;
import com.flippingcopilot.ui.components.IntervalDropdown;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

import javax.inject.Named;
import javax.swing.*;
import java.awt.*;
import java.time.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Slf4j
public class ProfitPanel extends JPanel {

    // dependencies
    private final FlipManager flipManager;
    private final ExecutorService executorService;

    // UI components
    private IntervalDropdown timeIntervalDropdown;
    private DisplayNameDropdown displayNameDropdown;
    private final ProfitGraphPanel graphPanel;

    // State
    private int cachedIntervalStartTime = -999; // Default to ALL
    private String cachedDisplayName = "";

    public ProfitPanel(FlipManager flipManager, @Named("copilotExecutor")  ExecutorService executorService, SessionManager sessionManager) {
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

        // Setup time interval dropdown
        timeIntervalDropdown = new IntervalDropdown((units, value) -> {
            int s = (int) IntervalDropdown.calculateStartTime(units, value, sessionManager.getCachedSessionData().startTime);
            if (s != cachedIntervalStartTime) {
                cachedIntervalStartTime = s;
                refreshGraph();
            }
        });
        timeIntervalDropdown.setPreferredSize(new Dimension(150, timeIntervalDropdown.getPreferredSize().height));
        timeIntervalDropdown.setToolTipText("Select time interval");

        // Setup display name dropdown
        displayNameDropdown = new DisplayNameDropdown(
                flipManager::getDisplayNameOptions,
                newDisplayName -> {
                    if (!Objects.equals(newDisplayName, cachedDisplayName)) {
                        cachedDisplayName = newDisplayName;
                        refreshGraph();
                    }
                }
        );
        displayNameDropdown.setPreferredSize(new Dimension(120, displayNameDropdown.getPreferredSize().height));
        displayNameDropdown.setToolTipText("Select account");
        displayNameDropdown.refresh();

        leftPanel.add(timeIntervalDropdown);
        leftPanel.add(Box.createRigidArea(new Dimension(3, 0)));
        leftPanel.add(displayNameDropdown);

        topPanel.add(leftPanel, BorderLayout.WEST);
        add(topPanel, BorderLayout.NORTH);

        // Create graph panel with initial empty data
        graphPanel = new ProfitGraphPanel(new ArrayList<>());
        add(graphPanel, BorderLayout.CENTER);
    }

    private void refreshGraph() {
        executorService.submit(() -> {
            try {
                List<FlipV2> flips = flipManager.getPageFlips(1, 1_000_000_000, cachedIntervalStartTime, cachedDisplayName, false);
                List<ProfitGraphPanel.ProfitDataPoint> dataPoints = generateProfitDataPoints(flips);
                SwingUtilities.invokeLater(() -> {
                    graphPanel.setData(dataPoints);
                    graphPanel.repaint();
                });
            } catch (Exception e) {
                log.error("Error refreshing profit graph", e);
            }
        });
    }

    private List<ProfitGraphPanel.ProfitDataPoint> generateProfitDataPoints(List<FlipV2> flips) {
        if (flips.isEmpty()) {
            return new ArrayList<>();
        }

        // Filter out flips without closed time and sort by closed time
        List<FlipV2> closedFlips = flips.stream()
                .filter(f -> f.getClosedTime() > 0)
                .sorted(Comparator.comparing(FlipV2::getClosedTime))
                .collect(Collectors.toList());

        if (closedFlips.isEmpty()) {
            return new ArrayList<>();
        }

        List<ProfitGraphPanel.ProfitDataPoint> dataPoints = new ArrayList<>();

        // Track cumulative profit
        long cumulativeProfit = 0;

        // Get the system's default time zone
        ZoneId zoneId = ZoneId.systemDefault();

        // Convert first flip's closed time to LocalDate
        int currentFlipTime = closedFlips.get(0).getClosedTime();
        LocalDate currentDate = LocalDate.ofInstant(
                Instant.ofEpochSecond(currentFlipTime),
                zoneId
        );

        // Add starting point at beginning of first day with 0 profit
        long startOfFirstDay = currentDate.atStartOfDay(zoneId).toEpochSecond();
        dataPoints.add(new ProfitGraphPanel.ProfitDataPoint(startOfFirstDay, 0));

        // Process each flip
        for (FlipV2 flip : closedFlips) {
            LocalDate flipDate = LocalDate.ofInstant(
                    Instant.ofEpochSecond(flip.getClosedTime()),
                    zoneId
            );

            // If we've crossed a day boundary, create a data point for the end of the previous day
            if (!flipDate.equals(currentDate)) {
                // Add data point at end of current day
                long endOfDay = currentDate.atTime(23, 59, 59).atZone(zoneId).toEpochSecond();
                dataPoints.add(new ProfitGraphPanel.ProfitDataPoint(endOfDay, cumulativeProfit));

                // Move to the new date
                currentDate = flipDate;

                // Add data point at start of new day with same profit (to create continuous line)
                long startOfDay = currentDate.atStartOfDay(zoneId).toEpochSecond();
                dataPoints.add(new ProfitGraphPanel.ProfitDataPoint(startOfDay, cumulativeProfit));
            }

            // Add this flip's profit to cumulative total
            cumulativeProfit += flip.getProfit();
        }

        // Add final data point at the last flip's time
        dataPoints.add(new ProfitGraphPanel.ProfitDataPoint(
                closedFlips.get(closedFlips.size() - 1).getClosedTime(),
                cumulativeProfit
        ));

        return dataPoints;
    }
}