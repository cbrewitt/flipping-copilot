package com.flippingcopilot.ui.flipsdialog;

import com.flippingcopilot.config.FlippingCopilotConfig;
import com.flippingcopilot.controller.ItemController;
import com.flippingcopilot.model.PortfolioItemCardData;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.Locale;

public class PortfolioItemCard extends JPanel {
    private static final NumberFormat GP_FORMAT = NumberFormat.getNumberInstance(Locale.US);
    private static final int LEFT_COLUMN_WIDTH = 260;
    private static final int MIDDLE_COLUMN_WIDTH = 180;
    private static final int RIGHT_COLUMN_WIDTH = 220;

    public PortfolioItemCard(PortfolioItemCardData data, ItemController itemController, FlippingCopilotConfig config) {
        setLayout(new BorderLayout(20, 0));
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setBorder(new EmptyBorder(12, 12, 12, 12));
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 92));

        JPanel contentPanel = new JPanel();
        contentPanel.setOpaque(false);
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.X_AXIS));

        JPanel leftPanel = buildLeftPanel(data, itemController);
        leftPanel.setPreferredSize(new Dimension(LEFT_COLUMN_WIDTH, 52));
        leftPanel.setMinimumSize(new Dimension(LEFT_COLUMN_WIDTH, 52));
        leftPanel.setMaximumSize(new Dimension(LEFT_COLUMN_WIDTH, Integer.MAX_VALUE));

        JPanel detailsPanel = buildDetailsPanel(data);
        detailsPanel.setPreferredSize(new Dimension(MIDDLE_COLUMN_WIDTH, 52));
        detailsPanel.setMinimumSize(new Dimension(MIDDLE_COLUMN_WIDTH, 52));
        detailsPanel.setMaximumSize(new Dimension(MIDDLE_COLUMN_WIDTH, Integer.MAX_VALUE));

        JPanel metricsPanel = buildMetricsPanel(data, config);
        metricsPanel.setPreferredSize(new Dimension(RIGHT_COLUMN_WIDTH, 52));
        metricsPanel.setMinimumSize(new Dimension(RIGHT_COLUMN_WIDTH, 52));
        metricsPanel.setMaximumSize(new Dimension(RIGHT_COLUMN_WIDTH, Integer.MAX_VALUE));

        contentPanel.add(leftPanel);
        contentPanel.add(Box.createHorizontalStrut(20));
        contentPanel.add(detailsPanel);
        contentPanel.add(Box.createHorizontalGlue());
        contentPanel.add(metricsPanel);

        add(contentPanel, BorderLayout.CENTER);
    }

    private JPanel buildLeftPanel(PortfolioItemCardData data, ItemController itemController) {
        JPanel leftPanel = new JPanel(new BorderLayout(12, 0));
        leftPanel.setOpaque(false);

        JPanel iconPanel = new JPanel(new GridBagLayout());
        iconPanel.setOpaque(false);
        iconPanel.setPreferredSize(new Dimension(52, 52));
        JLabel iconLabel = new JLabel();
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setPreferredSize(new Dimension(42, 42));
        itemController.loadImage(data.getItemId(), image -> setIcon(iconLabel, image));
        iconPanel.add(iconLabel);

        JPanel namePanel = new JPanel(new GridBagLayout());
        namePanel.setOpaque(false);
        namePanel.add(createPrimaryLabel(data.getItemName()));

        leftPanel.add(iconPanel, BorderLayout.WEST);
        leftPanel.add(namePanel, BorderLayout.CENTER);
        return leftPanel;
    }

    private void setIcon(JLabel iconLabel, AsyncBufferedImage image) {
        if (image == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> image.addTo(iconLabel));
    }

    private JPanel buildDetailsPanel(PortfolioItemCardData data) {
        JPanel detailsPanel = new JPanel();
        detailsPanel.setOpaque(false);
        detailsPanel.setLayout(new BoxLayout(detailsPanel, BoxLayout.Y_AXIS));
        detailsPanel.setAlignmentY(Component.CENTER_ALIGNMENT);

        JLabel quantityLabel = createSecondaryLabel("Quantity: " + GP_FORMAT.format(data.getOpenFlipsQuantity()));
        JLabel openFlipsLabel = createSecondaryLabel("Open flips: " + data.getOpenFlipsCount());

        detailsPanel.add(Box.createVerticalGlue());
        detailsPanel.add(quantityLabel);
        detailsPanel.add(Box.createVerticalStrut(6));
        detailsPanel.add(openFlipsLabel);
        detailsPanel.add(Box.createVerticalGlue());
        return detailsPanel;
    }

    private JPanel buildMetricsPanel(PortfolioItemCardData data, FlippingCopilotConfig config) {
        JPanel metricsPanel = new JPanel();
        metricsPanel.setOpaque(false);
        metricsPanel.setLayout(new BoxLayout(metricsPanel, BoxLayout.Y_AXIS));
        metricsPanel.setAlignmentY(Component.CENTER_ALIGNMENT);

        JLabel timeHeldLabel = createSecondaryLabel("Time held: " + formatDuration(data.getHeldMinutes()));
        long flipsUnrealizedPnl = data.flipsUnrealizedPNL();
        JLabel pnlLabel = createSecondaryLabel("Unrealized PNL: " + formatSignedGp(flipsUnrealizedPnl));
        pnlLabel.setForeground(getPnlColor(data.getUnrealizedUnitPNL(), config));

        metricsPanel.add(Box.createVerticalGlue());
        metricsPanel.add(timeHeldLabel);
        metricsPanel.add(Box.createVerticalStrut(6));
        metricsPanel.add(pnlLabel);
        metricsPanel.add(Box.createVerticalGlue());
        return metricsPanel;
    }

    private JLabel createPrimaryLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Color.WHITE);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 16f));
        return label;
    }

    private JLabel createSecondaryLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setFont(label.getFont().deriveFont(17f));
        return label;
    }

    private Color getPnlColor(Long pnl, FlippingCopilotConfig config) {
        if (pnl == null) {
            return ColorScheme.LIGHT_GRAY_COLOR;
        }
        if (pnl > 0) {
            return config.profitAmountColor();
        }
        if (pnl < 0) {
            return config.lossAmountColor();
        }
        return ColorScheme.LIGHT_GRAY_COLOR;
    }

    private String formatSignedGp(long amount) {
        String prefix = amount > 0 ? "+" : "";
        return prefix + GP_FORMAT.format(amount) + " gp";
    }

    private String formatDuration(int heldMinutes) {
        Duration duration = Duration.ofMinutes(Math.max(0, heldMinutes));
        long days = duration.toDays();
        long hours = duration.minusDays(days).toHours();
        long minutes = duration.minusDays(days).minusHours(hours).toMinutes();

        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return Math.max(1, minutes) + "m";
    }
}
