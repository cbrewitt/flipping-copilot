package com.flippingcopilot.ui;

import com.flippingcopilot.model.RiskLevel;
import com.flippingcopilot.model.SuggestionManager;
import com.flippingcopilot.model.SuggestionPreferencesManager;
import com.flippingcopilot.rs.AccountSuggestionPreferencesRS;
import net.runelite.client.ui.ColorScheme;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private final SuggestionManager suggestionManager;
    private final SuggestionPreferencesManager preferencesManager;
    private final JPanel timeframePanel;
    private final JToggleButton btn5m;
    private final JToggleButton btn30m;
    private final JToggleButton btn2h;
    private final JToggleButton btn8h;
    private final JToggleButton btnCustom; // "..." button
    private final JToggleButton btnRiskLow;
    private final JToggleButton btnRiskMedium;
    private final JToggleButton btnRiskHigh;

    private boolean suppressTimeframeSliderEvents;
    private boolean customExplicitlySelected;

    private static final Color RISK_LOW_SELECTED_COLOR = ColorScheme.GRAND_EXCHANGE_PRICE;
    private static final Color RISK_HIGH_SELECTED_COLOR = Color.red;
    private static final String RISK_LOW_LABEL = "Low";
    private static final String RISK_MEDIUM_LABEL = "Med";
    private static final String RISK_HIGH_LABEL = "High";

    private final JSlider timeframeSlider;
    private final JLabel valueLabel; // fixed-size text showing selected time
    private final JTextField valueEditor; // temporary editor shown during inline edits
    private final JPanel customPanel; // contains only the slider row (no label)
    private final JPanel sliderRow;
    private boolean editingCustomValue;
    private int editingOriginalMinutes;

    // Sqrt domain precomputed
    private static final double SQRT_MIN = Math.sqrt(MIN_MINUTES);
    private static final double SQRT_MAX = Math.sqrt(MAX_MINUTES);
    private static final double SQRT_RANGE = SQRT_MAX - SQRT_MIN;
    private static final Pattern TIME_TOKEN_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(h|hr|hrs|hour|hours|m|min|mins|minute|minutes)?", Pattern.CASE_INSENSITIVE);

    @Inject
    public ControlPanel(
            SuggestionManager suggestionManager,
            SuggestionPreferencesManager preferencesManager,
            AccountSuggestionPreferencesRS accountSuggestionPreferencesRS)
    {
        this.suggestionManager = suggestionManager;
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
        customExplicitlySelected = !isPreset(initMinutes);
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
        valueLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        valueLabel.setToolTipText("Click to enter a custom time interval");

        // Fixed label size based on widest expected text
        String widest = "24h 00m";
        FontMetrics fm = valueLabel.getFontMetrics(valueLabel.getFont());
        int labelWidth = fm.stringWidth(widest);
        int labelHeight = fm.getHeight();
        Dimension fixed = new Dimension(labelWidth, labelHeight);
        valueLabel.setMinimumSize(fixed);
        valueLabel.setPreferredSize(fixed);
        valueLabel.setMaximumSize(fixed);

        valueEditor = new JTextField();
        valueEditor.setHorizontalAlignment(JTextField.RIGHT);
        valueEditor.setMinimumSize(fixed);
        valueEditor.setPreferredSize(fixed);
        valueEditor.setMaximumSize(fixed);
        valueEditor.setBackground(ColorScheme.DARK_GRAY_COLOR);
        valueEditor.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        valueEditor.setCaretColor(ColorScheme.BRAND_ORANGE);
        valueEditor.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR),
                BorderFactory.createEmptyBorder(0, 4, 0, 4)));

        valueLabel.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() != 1)
                {
                    return;
                }
                beginCustomTimeEditing();
            }
        });

        valueEditor.addActionListener(e -> commitCustomTime(true));
        valueEditor.addFocusListener(new FocusAdapter()
        {
            @Override
            public void focusLost(FocusEvent e)
            {
                if (!editingCustomValue)
                {
                    return;
                }
                if (!commitCustomTime(false))
                {
                    cancelCustomTimeEditing();
                }
            }
        });

        InputMap inputMap = valueEditor.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = valueEditor.getActionMap();
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel-edit");
        actionMap.put("cancel-edit", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                cancelCustomTimeEditing();
            }
        });

        sliderRow = new JPanel(new BorderLayout(8, 0));
        sliderRow.setOpaque(false);
        sliderRow.add(timeframeSlider, BorderLayout.CENTER);
        sliderRow.add(valueLabel, BorderLayout.EAST);

        timeframeSlider.addChangeListener((ChangeEvent e) -> {
            int minutesPreview = posToMinutes(timeframeSlider.getValue());
            updateValueLabel(minutesPreview);
            if (suppressTimeframeSliderEvents)
            {
                return;
            }
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

        timeframePanel.add(Box.createRigidArea(new Dimension(0, 10)));

        JLabel riskLabel = new JLabel("Risk level: ");
        riskLabel.setHorizontalAlignment(SwingConstants.LEFT);
        riskLabel.setMaximumSize(riskLabel.getPreferredSize());

        JPanel riskButtonPanel = new JPanel();
        riskButtonPanel.setLayout(new GridLayout(1, 3, 0, 0));
        riskButtonPanel.setOpaque(false);

        ButtonGroup riskButtonGroup = new ButtonGroup();
        RiskLevel initialRiskLevel = preferencesManager.getRiskLevel();
        if (initialRiskLevel == null)
        {
            initialRiskLevel = RiskLevel.MEDIUM;
            preferencesManager.setRiskLevel(initialRiskLevel);
        }

        btnRiskLow = createRiskButton(RISK_LOW_LABEL, RiskLevel.LOW, suggestionManager);
        btnRiskMedium = createRiskButton(RISK_MEDIUM_LABEL, RiskLevel.MEDIUM, suggestionManager);
        btnRiskHigh = createRiskButton(RISK_HIGH_LABEL, RiskLevel.HIGH, suggestionManager);

        riskButtonGroup.add(btnRiskLow);
        riskButtonGroup.add(btnRiskMedium);
        riskButtonGroup.add(btnRiskHigh);

        riskButtonPanel.add(btnRiskLow);
        riskButtonPanel.add(btnRiskMedium);
        riskButtonPanel.add(btnRiskHigh);

        JPanel riskRow = new JPanel();
        riskRow.setLayout(new BoxLayout(riskRow, BoxLayout.X_AXIS));
        riskRow.setOpaque(false);
        riskRow.add(riskLabel);
        riskRow.add(Box.createRigidArea(new Dimension(10, 0)));
        riskRow.add(riskButtonPanel);
        riskRow.add(Box.createHorizontalGlue());

        timeframePanel.add(riskRow);

        updateRiskButtons(initialRiskLevel);
        add(timeframePanel);

        // Initial sync & visibility
        refresh();
        updateCustomVisibility();

        accountSuggestionPreferencesRS.registerListener(ignored -> refresh());
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

    private void beginCustomTimeEditing()
    {
        if (editingCustomValue)
        {
            return;
        }

        editingCustomValue = true;
        editingOriginalMinutes = posToMinutes(timeframeSlider.getValue());

        sliderRow.remove(valueLabel);
        sliderRow.add(valueEditor, BorderLayout.EAST);
        sliderRow.revalidate();
        sliderRow.repaint();

        valueEditor.setText(formatMinutes(editingOriginalMinutes));
        valueEditor.selectAll();
        valueEditor.requestFocusInWindow();
    }

    private boolean commitCustomTime(boolean showError)
    {
        Integer minutes = parseCustomTimeMinutes(valueEditor.getText());
        if (minutes == null)
        {
            if (showError)
            {
                JOptionPane.showMessageDialog(this,
                        "Couldn't understand that time. Try 90m, 1h 30m, or 1:30.",
                        "Invalid custom time",
                        JOptionPane.ERROR_MESSAGE);
                valueEditor.requestFocusInWindow();
                valueEditor.selectAll();
            }
            return false;
        }

        if (minutes < MIN_MINUTES || minutes > MAX_MINUTES)
        {
            if (showError)
            {
                JOptionPane.showMessageDialog(this,
                        String.format(Locale.ROOT, "Time must be between %dm and %dh 00m.", MIN_MINUTES, MAX_MINUTES / 60),
                        "Invalid custom time",
                        JOptionPane.ERROR_MESSAGE);
                valueEditor.requestFocusInWindow();
                valueEditor.selectAll();
            }
            return false;
        }

        applyTimeframe(minutes, suggestionManager, /*updateSlider*/ true, /*updateButtons*/ true);
        restoreValueLabelComponent();
        return true;
    }

    private void cancelCustomTimeEditing()
    {
        if (!editingCustomValue)
        {
            return;
        }

        updateValueLabel(posToMinutes(timeframeSlider.getValue()));
        restoreValueLabelComponent();
    }

    private void restoreValueLabelComponent()
    {
        sliderRow.remove(valueEditor);
        sliderRow.add(valueLabel, BorderLayout.EAST);
        sliderRow.revalidate();
        sliderRow.repaint();
        editingCustomValue = false;
    }

    private Integer parseCustomTimeMinutes(String input)
    {
        if (input == null)
        {
            return null;
        }

        String trimmed = input.trim();
        if (trimmed.isEmpty())
        {
            return null;
        }

        String normalized = trimmed.toLowerCase(Locale.ROOT);

        if (normalized.contains(":"))
        {
            String[] parts = normalized.split(":");
            if (parts.length != 2)
            {
                return null;
            }

            try
            {
                int hours = Integer.parseInt(parts[0].trim());
                int minutes = Integer.parseInt(parts[1].trim());
                if (hours < 0 || minutes < 0 || minutes >= 60)
                {
                    return null;
                }
                return hours * 60 + minutes;
            }
            catch (NumberFormatException ex)
            {
                return null;
            }
        }

        Matcher matcher = TIME_TOKEN_PATTERN.matcher(normalized);
        int total = 0;
        int lastEnd = 0;
        boolean matched = false;

        while (matcher.find())
        {
            String between = normalized.substring(lastEnd, matcher.start());
            if (!between.trim().isEmpty())
            {
                return null;
            }

            String numberPart = matcher.group(1);
            String unit = matcher.group(2);

            double value;
            try
            {
                value = Double.parseDouble(numberPart);
            }
            catch (NumberFormatException ex)
            {
                return null;
            }

            if (unit == null || unit.toLowerCase(Locale.ROOT).startsWith("m"))
            {
                if (numberPart.contains("."))
                {
                    return null;
                }
                total += (int) value;
            }
            else
            {
                total += (int) Math.round(value * 60.0);
            }

            matched = true;
            lastEnd = matcher.end();
        }

        if (matched)
        {
            String trailing = normalized.substring(lastEnd).trim();
            if (!trailing.isEmpty())
            {
                return null;
            }
            return total > 0 ? total : null;
        }

        try
        {
            int minutes = Integer.parseInt(normalized);
            return minutes > 0 ? minutes : null;
        }
        catch (NumberFormatException ex)
        {
            return null;
        }
    }

    private void updateRiskButtons(RiskLevel level)
    {
        RiskLevel effective = level != null ? level : RiskLevel.MEDIUM;
        btnRiskLow.setSelected(effective == RiskLevel.LOW);
        btnRiskMedium.setSelected(effective == RiskLevel.MEDIUM);
        btnRiskHigh.setSelected(effective == RiskLevel.HIGH);
    }

    private void applyRiskButtonStyle(JToggleButton button, String label, RiskLevel level, boolean selected)
    {
        Color background;
        Color textColor;

        if (selected)
        {
            switch (level)
            {
                case LOW:
                    background = RISK_LOW_SELECTED_COLOR;
                    textColor = Color.BLACK;
                    break;
                case HIGH:
                    background = RISK_HIGH_SELECTED_COLOR;
                    textColor = Color.WHITE;
                    break;
                case MEDIUM:
                default:
                    background = ColorScheme.BRAND_ORANGE;
                    textColor = Color.BLACK;
                    break;
            }
        }
        else
        {
            background = ColorScheme.DARKER_GRAY_COLOR;
            textColor = ColorScheme.TEXT_COLOR;
        }

        button.setBackground(background);
        button.setText(String.format("<html><font color='%s'>%s</font></html>", toHtmlColor(textColor), label));
    }

    private static String toHtmlColor(Color color)
    {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    // ---------- UI wiring ----------
    private void applyRiskLevel(RiskLevel level, SuggestionManager suggestionManager, boolean updateButtons)
    {
        RiskLevel effective = level != null ? level : RiskLevel.MEDIUM;
        preferencesManager.setRiskLevel(effective);
        suggestionManager.setSuggestionNeeded(true);

        if (updateButtons)
        {
            updateRiskButtons(effective);
        }
    }

    private void applyTimeframe(int minutes, SuggestionManager suggestionManager, boolean updateSlider, boolean updateButtons)
    {
        preferencesManager.setTimeframe(minutes);
        suggestionManager.setSuggestionNeeded(true);

        if (updateSlider)
        {
            try
            {
                suppressTimeframeSliderEvents = true;
                timeframeSlider.setValue(minutesToPos(minutes));
            }
            finally
            {
                suppressTimeframeSliderEvents = false;
            }
            updateValueLabel(minutes);
        }

        if (updateButtons)
        {
            boolean isPreset = isPreset(minutes);
            if (!isPreset)
            {
                customExplicitlySelected = true;
            }

            if (customExplicitlySelected)
            {
                btn5m.setSelected(false);
                btn30m.setSelected(false);
                btn2h.setSelected(false);
                btn8h.setSelected(false);
                btnCustom.setSelected(true);
            }
            else
            {
                boolean matched = false;
                btn5m.setSelected(matched = (minutes == PRESET_5M));
                if (!matched) btn30m.setSelected(matched = (minutes == PRESET_30M));
                if (!matched) btn2h.setSelected(matched = (minutes == PRESET_2H));
                if (!matched) btn8h.setSelected(matched = (minutes == PRESET_8H));
                btnCustom.setSelected(!matched);
                customExplicitlySelected = !matched;
            }
            updateCustomVisibility();
        }
    }

    private JToggleButton createPresetButton(String label, int value, SuggestionManager suggestionManager)
    {
        JToggleButton button = new JToggleButton();
        button.addActionListener(e -> {
            customExplicitlySelected = false;
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
            customExplicitlySelected = true;
            // Selecting "..." reveals the slider and shows current value
            int current = clampMinutes(preferencesManager.getTimeframe());
            suppressTimeframeSliderEvents = true;
            timeframeSlider.setValue(minutesToPos(current));
            suppressTimeframeSliderEvents = false;
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

    private JToggleButton createRiskButton(String label, RiskLevel level, SuggestionManager suggestionManager)
    {
        JToggleButton button = new JToggleButton();
        button.addActionListener(e -> applyRiskLevel(level, suggestionManager, true));
        button.setMargin(new Insets(2, 4, 2, 4));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        button.addChangeListener(e2 -> applyRiskButtonStyle(button, label, level, button.isSelected()));
        applyRiskButtonStyle(button, label, level, false);

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

        if (!customExplicitlySelected && !isPreset(tf))
        {
            customExplicitlySelected = true;
        }

        if (customExplicitlySelected)
        {
            btn5m.setSelected(false);
            btn30m.setSelected(false);
            btn2h.setSelected(false);
            btn8h.setSelected(false);
            btnCustom.setSelected(true);
        }
        else
        {
            boolean matched = false;
            btn5m.setSelected(matched = (tf == PRESET_5M));
            if (!matched) btn30m.setSelected(matched = (tf == PRESET_30M));
            if (!matched) btn2h.setSelected(matched = (tf == PRESET_2H));
            if (!matched) btn8h.setSelected(matched = (tf == PRESET_8H));
            btnCustom.setSelected(!matched);
            customExplicitlySelected = !matched;
        }

        // Sync slider & label
        try
        {
            suppressTimeframeSliderEvents = true;
            timeframeSlider.setValue(minutesToPos(tf));
        }
        finally
        {
            suppressTimeframeSliderEvents = false;
        }
        updateValueLabel(tf);

        // Sync risk level buttons
        updateRiskButtons(preferencesManager.getRiskLevel());

        // Show/hide custom area
        updateCustomVisibility();
    }
}
