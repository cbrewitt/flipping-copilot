package copilot.ui;
import static net.runelite.client.util.ImageUtil.*;

import java.awt.event.*;
import net.runelite.client.util.*;
import copilot.config.*;
import net.runelite.client.ui.*;

import javax.swing.*;
import java.awt.*;
import java.awt.image.*;
import java.text.*;
import java.util.*;
import java.util.function.*;

public class UIUtilities {
    /** Applies cell-specific styling after Swing restores selection and focus defaults. */
    public abstract static class StyledRenderer extends javax.swing.table.DefaultTableCellRenderer {
        @Override
        public final Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            style(table, value, selected, row);
            return this;
        }

        protected abstract void style(JTable table, Object value, boolean selected, int row);
    }

    public static final String redditIcon = "/reddit-icon.png", discordIcon = "/discord.png";
    public static final String logoutIcon = "/logout.png", internetIcon = "/internet.png";

    public static final float BUTTON_HOVER_LUMINANCE = 0.65f;
    public static final Color OUTDATED_COLOR = new Color(250, 74, 75), TOMATO = new Color(255,99,71);

    private static final NumberFormat PRECISE_DECIMAL_FORMATTER = new DecimalFormat(
            "#,###.###",
            DecimalFormatSymbols.getInstance(Locale.ENGLISH)
    );
    private static final NumberFormat DECIMAL_FORMATTER = new DecimalFormat(
            "#,###.#",
            DecimalFormatSymbols.getInstance(Locale.ENGLISH)
    );

    public static synchronized String quantityToRSDecimalStack(long quantity, boolean precise) {
        String sign = quantity >= 0 ? "" : "-";
        quantity = Math.abs(quantity);
        if (Long.toString(quantity).length() <= 4) { return sign + QuantityFormatter.formatNumber(quantity); }

        long power = (long) Math.log10(quantity);

        // Output thousandths for values above a million
        NumberFormat format = precise && power >= 6
                ? PRECISE_DECIMAL_FORMATTER
                : DECIMAL_FORMATTER;

        return sign+format.format(quantity / Math.pow(10, (Long.divideUnsigned(power, 3)) * 3))
                + new String[] {"", "K", "M", "B", "T"}[(int) (power / 3)];
    }

    public static Color getProfitColor(long profit, CopilotConfig config) {
        return getProfitColor((double) profit, config);
    }

    public static Color getProfitColor(double profit, CopilotConfig config) {
        if (profit > 0) { return config.profitAmountColor(); } else if (profit < 0) {
            return config.lossAmountColor();
        } else {
            return Color.WHITE;
        }
    }

    public static String formatProfit(long profit) { return quantityToRSDecimalStack(profit, true) + " gp"; }

    public static String formatProfitWithoutGp(long profit) { return quantityToRSDecimalStack(profit, true); }

    public static String formatSuggestionDuration(double durationSeconds) {
        int totalMinutes = (int) Math.round(durationSeconds / 60.0);
        totalMinutes = Math.round(totalMinutes / 5.0f) * 5;
        totalMinutes = Math.max(totalMinutes, 5);

        String formatted = formatDurationMinutes(totalMinutes);
        if (!formatted.contains("h") && !formatted.contains("d")) { return formatted.replace("m", "min"); }
        return formatted;
    }

    public static String formatDurationMinutes(int minutes) {
        int safeMinutes = Math.max(0, minutes), days = safeMinutes / (24 * 60);
        int hours = (safeMinutes % (24 * 60)) / 60, remainingMinutes = safeMinutes % 60;

        if (days > 0) { return hours == 0 ? days + "d" : days + "d " + hours + "h"; }
        if (hours > 0) { return hours + "h " + remainingMinutes + "m"; }
        return Math.max(1, remainingMinutes) + "m";
    }

    public static String truncateString(String string, int length) {
        if (string.length() > length) { return string.substring(0, length) + "..."; }
        return string;
    }

    public static JLabel buildButton(BufferedImage icon, String tooltip, Runnable onClick) {
        var label = new JLabel();
        label.setToolTipText(tooltip); label.setHorizontalAlignment(JLabel.CENTER);
        var iconOff = new ImageIcon(icon);
        var iconOn = new ImageIcon(luminanceScale(icon, BUTTON_HOVER_LUMINANCE));
        addMouseActions(() -> {
            try { onClick.run(); } catch (Exception error) {}
        }, hovered -> {
            if (hovered) label.setCursor(new Cursor(Cursor.HAND_CURSOR));
            label.setIcon(hovered ? iconOn : iconOff);
        }, label);
        label.setIcon(iconOff);
        return label;
    }

    public static JLabel gearButton(String tooltip, Runnable onClick) {
        var icon = resizeImage(loadImageResource(UIUtilities.class, "/preferences-icon.png"), 20, 20);
        return buildButton(recolorImage(icon, ColorScheme.LIGHT_GRAY_COLOR), tooltip, onClick);
    }

    public static void addHoverIcons(AbstractButton button, Supplier<Icon> normal, Supplier<Icon> hover) {
        addMouseActions(null, hovered -> button.setIcon((hovered ? hover : normal).get()), button);
    }

    /** Shares click and enter/exit callbacks across all parts of a composite control. */
    public static void addMouseActions(Runnable click, Consumer<Boolean> hover, Component... components) {
        var listener = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) { if (click != null) click.run(); }
            @Override
            public void mouseEntered(MouseEvent e) { if (hover != null) hover.accept(true); }
            @Override
            public void mouseExited(MouseEvent e) { if (hover != null) hover.accept(false); }
        };
        for (Component component : components) component.addMouseListener(listener);
    }

    /**
     * Returns true when the caller may proceed on the current (EDT) thread. Otherwise the task has
     * been scheduled via invokeLater and the caller must return immediately.
     */
    public static boolean ensureEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) { return true; }
        SwingUtilities.invokeLater(task);
        return false;
    }

    public static String colorHex(Color color) { return String.format("#%06X", (0xFFFFFF & color.getRGB())); }

    public static JPanel darkPanel(LayoutManager layout, Color background) {
        var panel = new JPanel(layout);
        panel.setBackground(background);
        return panel;
    }

    public static JPanel transparentPanel(LayoutManager layout) {
        var panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    public static JPanel verticalPanel(Color background) {
        var panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); panel.setBackground(background);
        return panel;
    }

    public static void setFixedSize(JComponent component, int width, int height) {
        var size = new Dimension(width, height);
        component.setPreferredSize(size); component.setMaximumSize(size);
    }

    public static void addVerticalGap(JPanel panel, int height) {
        panel.add(Box.createRigidArea(new Dimension(0, height)));
    }

    public static void addHorizontalGap(JPanel panel, int width) {
        panel.add(Box.createRigidArea(new Dimension(width, 0)));
    }

    public static JLabel coloredLabel(String text, Color foreground) {
        var label = new JLabel(text);
        label.setForeground(foreground);
        return label;
    }

    public static JPanel formRow(String label, Component control) {
        var panel = transparentPanel(new BorderLayout());
        panel.add(new JLabel(label), BorderLayout.LINE_START);
        panel.add(control, BorderLayout.LINE_END);
        return panel;
    }
}
