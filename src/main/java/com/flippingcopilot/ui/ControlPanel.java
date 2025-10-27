package com.flippingcopilot.ui;

import com.flippingcopilot.model.SuggestionManager;
import com.flippingcopilot.model.SuggestionPreferencesManager;
import net.runelite.client.ui.ColorScheme;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;

@Singleton
public class ControlPanel extends JPanel
{
    private static final int MIN_MINUTES = 1;
    private static final int MAX_MINUTES = 24 * 60;     // 1440

    private static final int STEPS = 1000;              // internal slider resolution

    // Presets
    private static final int PRESET_5M  = 5;
    private static final int PRESET_30M = 30;
    private static final int PRESET_2H  = 2 * 60;
    private static final int PRESET_8H  = 8 * 60;

    private final SuggestionPreferencesManager preferencesManager;
    private final JPanel timeframePanel;
    private final JToggleButton btn5m;
    private final JToggleButton btn30m;
    private final JToggleButton btn2h;
    private final JToggleButton btn8h;
    private final JToggleButton btnCustom; // "..." button

    private final JSlider timeframeSlider;
    private final JLabel valueLabel; // fixed-size text showing selected time
    private final JPanel customPanel; // contains only the slider row (no label)

    // Sqrt domain precomputed
    private static final double SQRT_MIN = Math.sqrt(MIN_MINUTES);
    private static final double SQRT_MAX = Math.sqrt(MAX_MINUTES);
    private static final double SQRT_RANGE = SQRT_MAX - SQRT_MIN;

    @Inject
    public ControlPanel(
            SuggestionManager suggestionManager,
            SuggestionPreferencesManager preferencesManager)
    {
        this.preferencesManager = preferencesManager;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        setBounds(0, 0, MainPanel.CONTENT_WIDTH, 200);

        // --- Timeframe buttons ---
        timeframePanel = new JPanel();
        timeframePanel.setLayout(new BoxLayout(timeframePanel, BoxLayout.Y_AXIS));
        timeframePanel.setOpaque(false);

        JLabel timeframeLabel = new JLabel("How often do you adjust offers?");
        timeframeLabel.setHorizontalAlignment(SwingConstants.LEFT);
        timeframeLabel.setMaximumSize(timeframeLabel.getPreferredSize());

        JPanel labelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        labelPanel.setOpaque(false);
        labelPanel.add(timeframeLabel);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(1, 5, 0, 0));
        buttonPanel.setOpaque(false);

        ButtonGroup timeframeButtonGroup = new ButtonGroup();

        btn5m     = createPresetButton("5m",   PRESET_5M,  suggestionManager);
        btn30m    = createPresetButton("30m",  PRESET_30M, suggestionManager);
        btn2h     = createPresetButton("2h",   PRESET_2H,  suggestionManager);
        btn8h     = createPresetButton("8h",   PRESET_8H,  suggestionManager);
        btnCustom = createCustomButton("...", suggestionManager);

        timeframeButtonGroup.add(btn5m);
        timeframeButtonGroup.add(btn30m);
        timeframeButtonGroup.add(btn2h);
        timeframeButtonGroup.add(btn8h);
        timeframeButtonGroup.add(btnCustom);

        buttonPanel.add(btn5m);
        buttonPanel.add(btn30m);
        buttonPanel.add(btn2h);
        buttonPanel.add(btn8h);
        buttonPanel.add(btnCustom);

        timeframePanel.add(labelPanel);
        timeframePanel.add(Box.createRigidArea(new Dimension(0, 3)));
        timeframePanel.add(buttonPanel);

        // --- Custom slider panel (hidden unless "..." selected) ---
        customPanel = new JPanel();
        customPanel.setLayout(new BoxLayout(customPanel, BoxLayout.Y_AXIS));
        customPanel.setOpaque(false);

        // small spacing above the slider row
        customPanel.add(Box.createRigidArea(new Dimension(0, 8)));

        int initMinutes = clampMinutes(preferencesManager.getTimeframe());
        timeframeSlider = new JSlider(JSlider.HORIZONTAL, 0, STEPS, minutesToPos(initMinutes));
        timeframeSlider.setOpaque(false);
        timeframeSlider.setPaintTicks(false);   // NO TICKS
        timeframeSlider.setPaintLabels(false);  // NO LABELS
        timeframeSlider.setSnapToTicks(false);

        // Stable slider height/width
        timeframeSlider.setPreferredSize(new Dimension(MainPanel.CONTENT_WIDTH - 100, 24));
        timeframeSlider.setMinimumSize(new Dimension(100, 24));
        timeframeSlider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        valueLabel = new JLabel(formatMinutes(initMinutes), SwingConstants.RIGHT);
        valueLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        // Fixed label size based on widest expected text
        String widest = "24h 00m";
        FontMetrics fm = valueLabel.getFontMetrics(valueLabel.getFont());
        int labelWidth = fm.stringWidth(widest);
        int labelHeight = fm.getHeight();
        Dimension fixed = new Dimension(labelWidth, labelHeight);
        valueLabel.setMinimumSize(fixed);
        valueLabel.setPreferredSize(fixed);
        valueLabel.setMaximumSize(fixed);

        JPanel sliderRow = new JPanel(new BorderLayout(8, 0));
        sliderRow.setOpaque(false);
        sliderRow.add(timeframeSlider, BorderLayout.CENTER);
        sliderRow.add(valueLabel, BorderLayout.EAST);

        timeframeSlider.addChangeListener((ChangeEvent e) -> {
            int minutesPreview = posToMinutes(timeframeSlider.getValue());
            updateValueLabel(minutesPreview);
            if (!timeframeSlider.getValueIsAdjusting())
            {
                applyTimeframe(minutesPreview, suggestionManager, /*updateSlider*/ false, /*updateButtons*/ true);
                // Keep "..." selected for non-preset custom values
                if (!isPreset(minutesPreview)) {
                    btnCustom.setSelected(true);
                }
            }
        });

        customPanel.add(sliderRow);

        timeframePanel.add(customPanel);
        add(timeframePanel);

        // Initial sync & visibility
        refresh();
        updateCustomVisibility();
    }

    // ---------- Mapping between slider position (0..STEPS) and minutes (1..1440) using √t ----------
    private static int minutesToPos(int minutes)
    {
        int m = clampMinutes(minutes);
        double root = Math.sqrt(m);
        double t = (root - SQRT_MIN) / SQRT_RANGE; // 0..1 uniform in sqrt space
        int pos = (int) Math.round(t * STEPS);
        return Math.max(0, Math.min(STEPS, pos));
    }

    private static int posToMinutes(int pos)
    {
        int p = Math.max(0, Math.min(STEPS, pos));
        double t = (double) p / (double) STEPS;   // 0..1
        double root = SQRT_MIN + t * SQRT_RANGE;
        int m = (int) Math.round(root * root);
        return clampMinutes(m);
    }

    private static int clampMinutes(int m)
    {
        return Math.max(MIN_MINUTES, Math.min(MAX_MINUTES, m));
    }

    private static boolean isPreset(int minutes)
    {
        return minutes == PRESET_5M || minutes == PRESET_30M || minutes == PRESET_2H || minutes == PRESET_8H;
    }

    private String formatMinutes(int m)
    {
        if (m < 60) return m + "m";
        if (m % 60 == 0) return (m / 60) + "h";
        int mins = m % 60;
        String mm = mins < 10 ? ("0" + mins) : String.valueOf(mins);
        return (m / 60) + "h " + mm + "m";
    }

    private void updateValueLabel(int minutes)
    {
        valueLabel.setText(formatMinutes(minutes));
    }

    private void updateCustomVisibility()
    {
        boolean show = btnCustom.isSelected();
        customPanel.setVisible(show);
        customPanel.revalidate();
        customPanel.repaint();
        timeframePanel.revalidate();
        timeframePanel.repaint();
    }

    // ---------- UI wiring ----------
    private void applyTimeframe(int minutes, SuggestionManager suggestionManager, boolean updateSlider, boolean updateButtons)
    {
        preferencesManager.setTimeframe(minutes);
        suggestionManager.setSuggestionNeeded(true);

        if (updateSlider)
        {
            timeframeSlider.setValue(minutesToPos(minutes));
            updateValueLabel(minutes);
        }

        if (updateButtons)
        {
            boolean matched = false;
            btn5m.setSelected(matched = (minutes == PRESET_5M));
            if (!matched) btn30m.setSelected(matched = (minutes == PRESET_30M));
            if (!matched) btn2h.setSelected(matched = (minutes == PRESET_2H));
            if (!matched) btn8h.setSelected(matched = (minutes == PRESET_8H));
            btnCustom.setSelected(!matched);
            updateCustomVisibility();
        }
    }

    private JToggleButton createPresetButton(String label, int value, SuggestionManager suggestionManager)
    {
        JToggleButton button = new JToggleButton();
        button.addActionListener(e -> {
            applyTimeframe(value, suggestionManager, /*updateSlider*/ true, /*updateButtons*/ true);
            updateCustomVisibility(); // hides slider
        });
        button.setMargin(new Insets(2, 4, 2, 4));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        button.setForeground(ColorScheme.TEXT_COLOR);

        button.setText("<html><font color='rgb(198, 198, 198)'>" + label + "</font></html>");

        button.addChangeListener(e2 -> {
            if (button.isSelected())
            {
                button.setBackground(ColorScheme.BRAND_ORANGE);
                button.setText("<html><font color='black'>" + label + "</font></html>");
            }
            else
            {
                button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                button.setText("<html><font color='rgb(198, 198, 198)'>" + label + "</font></html>");
            }
        });

        return button;
    }

    private JToggleButton createCustomButton(String label, SuggestionManager suggestionManager)
    {
        JToggleButton button = new JToggleButton();
        button.addActionListener(e -> {
            // Selecting "..." reveals the slider and shows current value
            int current = clampMinutes(preferencesManager.getTimeframe());
            timeframeSlider.setValue(minutesToPos(current));
            updateValueLabel(current);
            updateCustomVisibility(); // shows slider
        });
        button.setMargin(new Insets(2, 4, 2, 4));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        button.setForeground(ColorScheme.TEXT_COLOR);

        button.setText("<html><font color='rgb(198, 198, 198)'>" + label + "</font></html>");

        button.addChangeListener(e2 -> {
            if (button.isSelected())
            {
                button.setBackground(ColorScheme.BRAND_ORANGE);
                button.setText("<html><font color='black'>" + label + "</font></html>");
            }
            else
            {
                button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                button.setText("<html><font color='rgb(198, 198, 198)'>" + label + "</font></html>");
            }
        });

        return button;
    }

    public void refresh()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::refresh);
            return;
        }

        int tf = clampMinutes(preferencesManager.getTimeframe());

        // Sync buttons based on current preference
        boolean matched = false;
        btn5m.setSelected(matched = (tf == PRESET_5M));
        if (!matched) btn30m.setSelected(matched = (tf == PRESET_30M));
        if (!matched) btn2h.setSelected(matched = (tf == PRESET_2H));
        if (!matched) btn8h.setSelected(matched = (tf == PRESET_8H));
        btnCustom.setSelected(!matched);

        // Sync slider & label
        timeframeSlider.setValue(minutesToPos(tf));
        updateValueLabel(tf);

        // Show/hide custom area
        updateCustomVisibility();
    }
}
