package com.flippingcopilot.ui.flipsdialog;

import javax.swing.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.List;

public class DailyProfitBarGraphPanel extends JPanel {

    // Get DPI scale factor
    private static final double DPI_SCALE = Toolkit.getDefaultToolkit().getScreenResolution() / 96.0;

    // Layout constants (base values that will be scaled)
    private static final int BASE_PADDING_LEFT = 80;
    private static final int BASE_PADDING_RIGHT = 30;
    private static final int BASE_PADDING_TOP = 20;
    private static final int BASE_PADDING_BOTTOM = 50;

    // Scaled layout values
    private final int PADDING_LEFT = scale(BASE_PADDING_LEFT);
    private final int PADDING_RIGHT = scale(BASE_PADDING_RIGHT);
    private final int PADDING_TOP = scale(BASE_PADDING_TOP);
    private final int PADDING_BOTTOM = scale(BASE_PADDING_BOTTOM);

    // Visual constants
    private static final Color BACKGROUND_COLOR = new Color(43, 43, 43);
    private static final Color PLOT_AREA_COLOR = new Color(51, 51, 51);
    private static final Color GRID_COLOR = new Color(85, 85, 85, 90);
    private static final Color AXIS_COLOR = new Color(150, 150, 150);
    private static final Color TEXT_COLOR = new Color(225, 225, 225);
    private static final Color ZERO_LINE_COLOR = new Color(200, 200, 200);

    // Base font size that will be scaled
    private static final float BASE_FONT_SIZE = 14f;

    // Scaled strokes
    private final Stroke GRID_STROKE = new BasicStroke(
            scale(0.8f), BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{scale(3f)}, 0
    );
    private final Stroke AXIS_STROKE = new BasicStroke(scale(1.0f));
    private final Stroke ZERO_LINE_STROKE = new BasicStroke(scale(1.5f));

    private final Color profitColor;
    private final Color lossColor;

    // Data
    private Map<LocalDate, Long> dailyProfits = new TreeMap<>();
    private long minProfit = 0;
    private long maxProfit = 0;
    private LocalDate startDate;
    private LocalDate endDate;

    // Helper method to scale values based on DPI
    private static int scale(int value) {
        return (int) Math.round(value * DPI_SCALE);
    }

    private static float scale(float value) {
        return (float) (value * DPI_SCALE);
    }


    public DailyProfitBarGraphPanel(Color profitColor, Color lossColor) {
        this.profitColor = profitColor;
        this.lossColor = lossColor;

        setBackground(BACKGROUND_COLOR);
        setPreferredSize(new Dimension(scale(600), scale(200)));
        setBorder(BorderFactory.createEmptyBorder(scale(5), scale(10), scale(10), scale(10)));
    }

    public void setData(Map<LocalDate, Long> dailyProfits, LocalDate startDate, LocalDate endDate) {
        this.dailyProfits = new TreeMap<>(dailyProfits);
        this.startDate = startDate;
        this.endDate = endDate;

        // Calculate min and max profits for scaling
        minProfit = 0;
        maxProfit = 0;
        for (Long profit : dailyProfits.values()) {
            minProfit = Math.min(minProfit, profit);
            maxProfit = Math.max(maxProfit, profit);
        }

        // Add some padding to the max/min values
        if (maxProfit > 0) {
            maxProfit = (long)(maxProfit * 1.1);
        }
        if (minProfit < 0) {
            minProfit = (long)(minProfit * 1.1);
        }

        // Ensure we have some range even if all values are 0
        if (minProfit == 0 && maxProfit == 0) {
            maxProfit = 1_000_000;
            minProfit = -1_000_000;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        int plotWidth = width - PADDING_LEFT - PADDING_RIGHT;
        int plotHeight = height - PADDING_TOP - PADDING_BOTTOM;

        g2.setColor(PLOT_AREA_COLOR);
        g2.fillRect(PADDING_LEFT, PADDING_TOP, plotWidth, plotHeight);

        drawGrid(g2, plotWidth, plotHeight);
        drawAxes(g2, plotWidth, plotHeight);

        if (!dailyProfits.isEmpty() && startDate != null && endDate != null) {
            drawBars(g2, plotWidth, plotHeight);
        }

        drawAxisLabels(g2, plotWidth, plotHeight);
    }

    private void drawGrid(Graphics2D g2, int plotWidth, int plotHeight) {
        g2.setColor(GRID_COLOR);
        g2.setStroke(GRID_STROKE);

        // Horizontal grid lines
        List<Long> yTickValues = calculateYAxisTicks();
        for (Long tickValue : yTickValues) {
            int y = profitToY(tickValue, plotHeight);
            if (y >= PADDING_TOP && y <= PADDING_TOP + plotHeight) {
                g2.drawLine(PADDING_LEFT, y, PADDING_LEFT + plotWidth, y);
            }
        }

        // Highlight zero line
        g2.setColor(ZERO_LINE_COLOR);
        g2.setStroke(ZERO_LINE_STROKE);
        int zeroY = profitToY(0, plotHeight);
        if (zeroY >= PADDING_TOP && zeroY <= PADDING_TOP + plotHeight) {
            g2.drawLine(PADDING_LEFT, zeroY, PADDING_LEFT + plotWidth, zeroY);
        }
    }

    private void drawAxes(Graphics2D g2, int plotWidth, int plotHeight) {
        g2.setColor(AXIS_COLOR);
        g2.setStroke(AXIS_STROKE);

        // X-axis at zero line (or bottom if all positive/negative)
        int zeroY = profitToY(0, plotHeight);
        if (zeroY < PADDING_TOP) {
            zeroY = PADDING_TOP;
        } else if (zeroY > PADDING_TOP + plotHeight) {
            zeroY = PADDING_TOP + plotHeight;
        }

        g2.drawLine(PADDING_LEFT, zeroY, PADDING_LEFT + plotWidth, zeroY);

        // Y-axis
        g2.drawLine(PADDING_LEFT, PADDING_TOP, PADDING_LEFT, PADDING_TOP + plotHeight);
    }

    private void drawBars(Graphics2D g2, int plotWidth, int plotHeight) {
        if (startDate == null || endDate == null) return;

        // Calculate total number of days
        long totalDays = endDate.toEpochDay() - startDate.toEpochDay() + 1;
        if (totalDays <= 0) return;

        // Calculate bar width with gaps
        double availableWidth = plotWidth;
        double barWidth = availableWidth / totalDays;
        double barGap = barWidth * 0.1; // 10% gap between bars
        double actualBarWidth = barWidth - barGap;

        // Limit bar width for readability
        if (actualBarWidth > scale(50)) {
            actualBarWidth = scale(50);
            barGap = barWidth - actualBarWidth;
        }

        int zeroY = profitToY(0, plotHeight);

        // Draw bars for each day
        LocalDate currentDate = startDate;
        int dayIndex = 0;

        while (!currentDate.isAfter(endDate)) {
            Long profit = dailyProfits.get(currentDate);

            if (profit != null && profit != 0) {
                int x = PADDING_LEFT + (int)(dayIndex * barWidth + barGap / 2);
                int barY = profitToY(profit, plotHeight);

                // Set color based on profit/loss
                g2.setColor(profit > 0 ? profitColor : lossColor);

                // Draw bar
                if (profit > 0) {
                    // Positive bar goes up from zero line
                    int barHeight = zeroY - barY;
                    g2.fillRect(x, barY, (int)actualBarWidth, barHeight);
                } else {
                    // Negative bar goes down from zero line
                    int barHeight = barY - zeroY;
                    g2.fillRect(x, zeroY, (int)actualBarWidth, barHeight);
                }

                // Draw bar outline for clarity
                g2.setColor(new Color(0, 0, 0, 50));
                g2.setStroke(new BasicStroke(scale(1f)));
                if (profit > 0) {
                    g2.drawRect(x, barY, (int)actualBarWidth, zeroY - barY);
                } else {
                    g2.drawRect(x, zeroY, (int)actualBarWidth, barY - zeroY);
                }
            }

            currentDate = currentDate.plusDays(1);
            dayIndex++;
        }
    }

    private List<Long> calculateYAxisTicks() {
        List<Long> ticks = new ArrayList<>();

        long range = maxProfit - minProfit;
        if (range == 0) {
            ticks.add(0L);
            return ticks;
        }

        long tickSpacing = calculateNiceTickSpacing(range);

        // Start from a round number below minProfit
        long startTick = (minProfit / tickSpacing) * tickSpacing;
        if (startTick > minProfit) {
            startTick -= tickSpacing;
        }

        for (long tick = startTick; tick <= maxProfit; tick += tickSpacing) {
            ticks.add(tick);
        }

        // Ensure zero is included
        if (minProfit <= 0 && maxProfit >= 0 && !ticks.contains(0L)) {
            ticks.add(0L);
            ticks.sort(Long::compare);
        }

        return ticks;
    }

    private long calculateNiceTickSpacing(long range) {
        long roughSpacing = range / 5; // Aim for about 5 ticks
        if (roughSpacing <= 0) {
            return 1;
        }

        long magnitude = 1;
        while (magnitude * 10 <= roughSpacing) {
            magnitude *= 10;
        }

        if (roughSpacing <= magnitude) {
            return magnitude;
        } else if (roughSpacing <= 2 * magnitude) {
            return 2 * magnitude;
        } else if (roughSpacing <= 5 * magnitude) {
            return 5 * magnitude;
        } else {
            return 10 * magnitude;
        }
    }

    private void drawAxisLabels(Graphics2D g2, int plotWidth, int plotHeight) {
        Font scaledFont = g2.getFont().deriveFont(scale(BASE_FONT_SIZE));
        g2.setFont(scaledFont);
        g2.setColor(TEXT_COLOR);
        FontMetrics fm = g2.getFontMetrics();

        // X-axis labels (dates) - show fewer labels to avoid crowding
        if (startDate != null && endDate != null) {
            long totalDays = endDate.toEpochDay() - startDate.toEpochDay() + 1;

            // Determine label frequency based on total days
            int labelFrequency = calculateLabelFrequency(totalDays);

            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d");
            LocalDate currentDate = startDate;
            int dayIndex = 0;

            while (!currentDate.isAfter(endDate)) {
                if (dayIndex % labelFrequency == 0) {
                    double barWidth = (double)plotWidth / totalDays;
                    int x = PADDING_LEFT + (int)(dayIndex * barWidth + barWidth / 2);

                    String label = dateFormat.format(java.sql.Date.valueOf(currentDate));
                    int labelWidth = fm.stringWidth(label);

                    // Only draw if it fits
                    if (x - labelWidth / 2 >= PADDING_LEFT &&
                            x + labelWidth / 2 <= PADDING_LEFT + plotWidth) {
                        g2.drawString(label, x - labelWidth / 2,
                                PADDING_TOP + plotHeight + scale(20));
                    }
                }
                currentDate = currentDate.plusDays(1);
                dayIndex++;
            }
        }

        // Y-axis labels (profit)
        Font boldFont = scaledFont.deriveFont(Font.BOLD);
        g2.setFont(boldFont);
        FontMetrics boldFm = g2.getFontMetrics();

        List<Long> yTickValues = calculateYAxisTicks();
        for (Long profit : yTickValues) {
            int y = profitToY(profit, plotHeight);
            String label = formatProfit(profit);
            int labelWidth = boldFm.stringWidth(label);

            if (y >= PADDING_TOP && y <= PADDING_TOP + plotHeight) {
                g2.drawString(label, PADDING_LEFT - labelWidth - scale(10),
                        y + boldFm.getHeight() / 3);
            }
        }
    }

    private int calculateLabelFrequency(long totalDays) {
        if (totalDays <= 7) return 1;        // Show every day for a week
        if (totalDays <= 14) return 2;       // Every 2 days for 2 weeks
        if (totalDays <= 30) return 5;       // Every 5 days for a month
        if (totalDays <= 90) return 7;       // Weekly for 3 months
        if (totalDays <= 180) return 14;     // Bi-weekly for 6 months
        if (totalDays <= 365) return 30;     // Monthly for a year
        return 60;                            // Every 2 months for longer
    }

    private String formatProfit(long profit) {
        String sign = profit < 0 ? "-" : "";
        long absProfit = Math.abs(profit);

        if (absProfit >= 1_000_000_000) {
            return sign + String.format("%.1fB", absProfit / 1_000_000_000.0);
        } else if (absProfit >= 1_000_000) {
            return sign + String.format("%.1fM", absProfit / 1_000_000.0);
        } else if (absProfit >= 1_000) {
            return sign + String.format("%.1fK", absProfit / 1_000.0);
        } else {
            return sign + absProfit;
        }
    }

    private int profitToY(long profit, int plotHeight) {
        long range = maxProfit - minProfit;
        if (range == 0) return PADDING_TOP + plotHeight / 2;

        double normalized = (double)(profit - minProfit) / range;
        return PADDING_TOP + plotHeight - (int)(normalized * plotHeight);
    }
}