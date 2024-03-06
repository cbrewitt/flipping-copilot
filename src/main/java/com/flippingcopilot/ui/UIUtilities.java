package com.flippingcopilot.ui;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.QuantityFormatter;

import java.awt.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

public class UIUtilities {
    public static final String discordIcon = "/discord.png";
    public static final String githubIcon = "/github.png";
    public static final String logoutIcon = "/logout.png";
    public static final String iconSmall = "/icon-small.png";
    public static final String internetIcon = "/internet.png";

    public static final Color OUTDATED_COLOR = new Color(250, 74, 75);
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
        if (Long.toString(quantity).length() <= 4)
        {
            return QuantityFormatter.formatNumber(quantity);
        }

        long power = (long) Math.log10(quantity);

        // Output thousandths for values above a million
        NumberFormat format = precise && power >= 6
                ? PRECISE_DECIMAL_FORMATTER
                : DECIMAL_FORMATTER;

        return format.format(quantity / Math.pow(10, (Long.divideUnsigned(power, 3)) * 3))
                + new String[] {"", "K", "M", "B", "T"}[(int) (power / 3)];
    }

    public static Color getProfitColor(long profit) {
        if (profit > 0) {
            return ColorScheme.GRAND_EXCHANGE_PRICE;
        } else if (profit < 0) {
            return UIUtilities.OUTDATED_COLOR;
        } else {
            return Color.WHITE;
        }
    }

    public static String formatProfit(long profit) {
        return (profit >= 0 ? "" : "-") + quantityToRSDecimalStack(Math.abs(profit), true) + " gp";
    }

    public static String truncateString(String string, int length) {
        if (string.length() > length) {
            return string.substring(0, length) + "...";
        }
        return string;
    }
}
