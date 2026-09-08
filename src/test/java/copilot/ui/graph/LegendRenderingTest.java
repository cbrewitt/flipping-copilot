package copilot.ui.graph;

import copilot.ui.graph.model.Config;
import org.junit.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import static org.junit.Assert.assertArrayEquals;

public class LegendRenderingTest {
    @Test
    public void refactoredLegendMatchesOriginalPixels() {
        for (boolean predictions : new boolean[]{false, true}) {
            for (boolean connected : new boolean[]{false, true}) {
                for (int width : new int[]{500, 1000}) {
                    for (int top : new int[]{40, 41}) {
                        Config config = new Config();
                        config.connectPoints = connected;
                        Rectangle area = new Rectangle(65, top, width - 95, 250);
                        BufferedImage expected = new BufferedImage(width, 100, BufferedImage.TYPE_INT_ARGB);
                        BufferedImage actual = new BufferedImage(width, 100, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D expectedGraphics = expected.createGraphics();
                        Graphics2D actualGraphics = actual.createGraphics();
                        try {
                            drawOriginalLegend(expectedGraphics, config, area, predictions);
                            new GraphRenderer(actualGraphics, config, area, null).drawLegend(predictions);
                            assertArrayEquals(expected.getRGB(0, 0, width, 100, null, 0, width),
                                    actual.getRGB(0, 0, width, 100, null, 0, width));
                        } finally {
                            expectedGraphics.dispose();
                            actualGraphics.dispose();
                        }
                    }
                }
            }
        }
    }

    // Reference rendering retained to detect layout, stroke, color, and centering regressions.
    private void drawOriginalLegend(Graphics2D g2, Config config, Rectangle pa, boolean addPredictionLabels) {
        int xMid = pa.x + pa.width / 2;
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, Config.FONT_SIZE));
        FontMetrics metrics = g2.getFontMetrics();

        // Legend item text labels
        String[] labels = addPredictionLabels
                ? new String[]{"Lows (insta-sell)", "Highs (insta-buy)", "Low prediction", "High prediction", "Low IQR", "High IQR"}
                : new String[]{"Lows (insta-sell)", "Highs (insta-buy)"};

        // Calculate legend position - above the plot area
        int legendY = pa.y / 2;
        int lineLength = 20;
        int itemHeight = 15;
        int itemPadding = 30; // Space between text end and next item start

        // Calculate widths for each legend item based on text length
        int[] itemWidths = new int[labels.length];
        int totalWidth = 0;

        for (int i = 0; i < labels.length; i++) {
            itemWidths[i] = lineLength + 5 + metrics.stringWidth(labels[i]);
            totalWidth += itemWidths[i];
        }

        totalWidth += itemPadding * (labels.length - 1);

        int legendStartX = xMid - totalWidth / 2;
        int currentX = legendStartX;

        // Low prices
        currentX = drawLegendLineItem(g2, config, currentX, legendY, lineLength, itemHeight,
                labels[0], itemWidths[0], itemPadding, config.lowColor, Config.NORMAL_STROKE, config.connectPoints, true);

        // High prices
        currentX = drawLegendLineItem(g2, config, currentX, legendY, lineLength, itemHeight,
                labels[1], itemWidths[1], itemPadding, config.highColor, Config.NORMAL_STROKE, config.connectPoints, true);

        if (addPredictionLabels) {
            // Low prediction
            currentX = drawLegendLineItem(g2, config, currentX, legendY, lineLength, itemHeight,
                    labels[2], itemWidths[2], itemPadding, config.lowColor, Config.DOTTED_STROKE, true, false);

            // High prediction
            currentX = drawLegendLineItem(g2, config, currentX, legendY, lineLength, itemHeight,
                    labels[3], itemWidths[3], itemPadding, config.highColor, Config.DOTTED_STROKE, true, false);

            // Low IQR
            currentX = drawLegendShadeItem(g2, config, currentX, legendY, lineLength, itemHeight,
                    labels[4], itemWidths[4], itemPadding, config.lowShadeColor);

            // High IQR
            drawLegendShadeItem(g2, config, currentX, legendY, lineLength, itemHeight,
                    labels[5], itemWidths[5], itemPadding, config.highShadeColor);
        }
    }

    private int drawLegendLineItem(Graphics2D g2,
                                   Config config,
                                   int x,
                                   int y,
                                   int lineLength,
                                   int itemHeight,
                                   String label,
                                   int itemWidth,
                                   int itemPadding,
                                   Color color,
                                   Stroke stroke,
                                   boolean drawLine,
                                   boolean drawPoint) {
        int midY = y + itemHeight / 2;
        g2.setColor(color);
        g2.setStroke(stroke);
        if (drawLine) {
            g2.drawLine(x, midY, x + lineLength, midY);
        }
        if (drawPoint) {
            g2.fillOval(x + lineLength / 2 - 2, midY - 2, 5, 5);
        }
        drawLegendLabel(g2, config, x, midY, lineLength, label);
        return x + itemWidth + itemPadding;
    }

    private int drawLegendShadeItem(Graphics2D g2,
                                    Config config,
                                    int x,
                                    int y,
                                    int lineLength,
                                    int itemHeight,
                                    String label,
                                    int itemWidth,
                                    int itemPadding,
                                    Color color) {
        int midY = y + itemHeight / 2;
        g2.setColor(color);
        g2.fillRect(x, midY - 5, lineLength, 10);
        drawLegendLabel(g2, config, x, midY, lineLength, label);
        return x + itemWidth + itemPadding;
    }

    private void drawLegendLabel(Graphics2D g2, Config config, int x, int midY, int lineLength, String label) {
        g2.setColor(config.textColor);
        g2.drawString(label, x + lineLength + 5, midY + 4);
    }

}
