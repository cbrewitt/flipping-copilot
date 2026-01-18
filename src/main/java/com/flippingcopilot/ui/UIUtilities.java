package com.flippingcopilot.ui;

import com.flippingcopilot.config.FlippingCopilotConfig;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

public class UIUtilities {
    public static final String redditIcon = "/reddit-icon.png";
    public static final String discordIcon = "/discord.png";
    public static final String githubIcon = "/github.png";
    public static final String logoutIcon = "/logout.png";
    public static final String internetIcon = "/internet.png";
    public static final String graphIcon = "/graph.png";

    public static final float BUTTON_HOVER_LUMINANCE = 0.65f;
    public static final Color OUTDATED_COLOR = new Color(250, 74, 75);
    public static final Color BLUE_HIGHLIGHT_COLOR =  new Color(27, 173, 255, 79);
    public static final Color RED_HIGHLIGHT_COLOR = new Color(255, 20, 20, 79);
    public static final Color TOMATO = new Color(255,99,71);
    public static final Color DARK_GRAY = new Color(27, 27, 27);

    private static final NumberFormat PRECISE_DECIMAL_FORMATTER = new DecimalFormat(
            "#,###.###",
            DecimalFormatSymbols.getInstance(Locale.ENGLISH)
    );
    private static final NumberFormat DECIMAL_FORMATTER = new DecimalFormat(
            "#,###.#",
            DecimalFormatSymbols.getInstance(Locale.ENGLISH)
    );

    public static synchronized String quantityToRSDecimalStack(long quantity, boolean precise)
    {
        String sign = quantity > 0 ? "" : "-";
        quantity = Math.abs(quantity);
        if (Long.toString(quantity).length() <= 4)
        {
            return QuantityFormatter.formatNumber(quantity);
        }

        long power = (long) Math.log10(quantity);

        // Output thousandths for values above a million
        NumberFormat format = precise && power >= 6
                ? PRECISE_DECIMAL_FORMATTER
                : DECIMAL_FORMATTER;

        return sign+format.format(quantity / Math.pow(10, (Long.divideUnsigned(power, 3)) * 3))
                + new String[] {"", "K", "M", "B", "T"}[(int) (power / 3)];
    }

    public static Color getProfitColor(long profit, FlippingCopilotConfig config) {
        if (profit > 0) {
            return config.profitAmountColor();
        } else if (profit < 0) {
            return config.lossAmountColor();
        } else {
            return Color.WHITE;
        }
    }

    public static String formatProfit(long profit) {
        return quantityToRSDecimalStack(profit, true) + " gp";
    }

    public static String formatProfitWithoutGp(long profit) {
        return quantityToRSDecimalStack(profit, true);
    }

    public static String truncateString(String string, int length) {
        if (string.length() > length) {
            return string.substring(0, length) + "...";
        }
        return string;
    }

    public static JLabel buildButton(BufferedImage icon, String tooltip, Runnable onClick) {
        JLabel label = new JLabel();
        label.setToolTipText(tooltip);
        label.setHorizontalAlignment(JLabel.CENTER);
        ImageIcon iconOff = new ImageIcon(icon);
        ImageIcon iconOn = new ImageIcon(ImageUtil.luminanceScale(icon, BUTTON_HOVER_LUMINANCE));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    onClick.run();
                } catch (Exception error) {}
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                label.setCursor(new Cursor(Cursor.HAND_CURSOR));
                label.setIcon(iconOn);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                label.setIcon(iconOff);
            }
        });
        label.setIcon(iconOff);
        return label;
    }

    static JLabel buildUriButton(BufferedImage icon, String tooltip, String uriString) {
        return buildButton(icon, tooltip, () -> {
            try {
                Desktop desktop = Desktop.getDesktop();
                URI uri = new URI(uriString);
                desktop.browse(uri);
            } catch (Exception error) {}
        });
    }

    public static JPanel newVerticalBoxLayoutJPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }
}
