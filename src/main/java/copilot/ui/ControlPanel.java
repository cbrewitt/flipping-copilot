package copilot.ui;
import static javax.swing.JOptionPane.*;

import java.util.regex.*;
import static copilot.ui.UIUtilities.*;
import static javax.swing.BorderFactory.*;
import static net.runelite.client.ui.ColorScheme.*;
import copilot.model.*;
import copilot.rs.*;

import javax.inject.*;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

@Singleton
public class ControlPanel extends JPanel {
    private static final int MIN_MINUTES = 1;
    private static final int MAX_MINUTES = 24 * 60;     // 1440

    private static final int STEPS = 1000;              // internal slider resolution

    private static final int[] PRESET_MINUTES = {5, 30, 120, 480};

    private final Suggestions suggestions;
    private final Preferences preferences;
    private final JPanel timeframePanel;
    private final JToggleButton[] presetButtons = new JToggleButton[PRESET_MINUTES.length];
    private final Map<RiskLevel, JToggleButton> riskButtons = new EnumMap<>(RiskLevel.class);
    private final JToggleButton btnCustom; // "..." button

    private boolean suppressTimeframeSliderEvents, customExplicitlySelected;

    private static final Color RISK_LOW_SELECTED_COLOR = GRAND_EXCHANGE_PRICE;
    private static final Color RISK_HIGH_SELECTED_COLOR = Color.red;
    private static final String RISK_LOW_LABEL = "Low", RISK_MEDIUM_LABEL = "Med", RISK_HIGH_LABEL = "High";

    private final JSlider timeframeSlider;
    private final JLabel valueLabel; // fixed-size text showing selected time
    private final JTextField valueEditor; // temporary editor shown during inline edits
    private final JPanel customPanel; // contains only the slider row (no label)
    private final JPanel sliderRow;
    private boolean editingCustomValue;
    private int editingOriginalMinutes;

    // Sqrt domain precomputed
    private static final double SQRT_MIN = Math.sqrt(MIN_MINUTES), SQRT_MAX = Math.sqrt(MAX_MINUTES);
    private static final double SQRT_RANGE = SQRT_MAX - SQRT_MIN;
    private static final Pattern TIME_TOKEN_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(h|hr|hrs|hour|hours|m|min|mins|minute|minutes)?", Pattern.CASE_INSENSITIVE);

    @Inject
    public ControlPanel(
            Suggestions suggestions,
            Preferences preferences,
            AccountPreferencesRS accountSuggestionPreferencesRS) {
        this.suggestions = suggestions; this.preferences = preferences;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(DARKER_GRAY_COLOR);
        setBorder(createEmptyBorder(10, 15, 10, 15));

        // --- Timeframe buttons ---
        timeframePanel = new JPanel();
        timeframePanel.setLayout(new BoxLayout(timeframePanel, BoxLayout.Y_AXIS)); timeframePanel.setOpaque(false);

        var timeframeLabel = new JLabel("How often do you adjust offers?");
        timeframeLabel.setHorizontalAlignment(SwingConstants.LEFT);
        timeframeLabel.setMaximumSize(timeframeLabel.getPreferredSize());

        var labelPanel = transparentPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        labelPanel.add(timeframeLabel);

        var buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(1, 5, 0, 0)); buttonPanel.setOpaque(false);

        ButtonGroup timeframeButtonGroup = new ButtonGroup();
        for (int i = 0; i < PRESET_MINUTES.length; i++) {
            int minutes = PRESET_MINUTES[i];
            presetButtons[i] = createPresetButton(formatMinutes(minutes), minutes);
            timeframeButtonGroup.add(presetButtons[i]);
            buttonPanel.add(presetButtons[i]);
        }
        btnCustom = createCustomButton("...");
        timeframeButtonGroup.add(btnCustom);
        buttonPanel.add(btnCustom);

        timeframePanel.add(labelPanel);
        addVerticalGap(timeframePanel, 3);
        timeframePanel.add(buttonPanel);

        // --- Custom slider panel (hidden unless "..." selected) ---
        customPanel = new JPanel();
        customPanel.setLayout(new BoxLayout(customPanel, BoxLayout.Y_AXIS)); customPanel.setOpaque(false);

        // small spacing above the slider row
        addVerticalGap(customPanel, 8);

        int initMinutes = clampMinutes(preferences.getTimeframe());
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
        valueLabel.setForeground(LIGHT_GRAY_COLOR);
        valueLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        valueLabel.setToolTipText("Click to enter a custom time interval");

        // Fixed label size based on widest expected text
        String widest = "24h 00m";
        var fm = valueLabel.getFontMetrics(valueLabel.getFont());
        int labelWidth = fm.stringWidth(widest), labelHeight = fm.getHeight();
        var fixed = new Dimension(labelWidth, labelHeight);
        valueLabel.setMinimumSize(fixed); valueLabel.setPreferredSize(fixed); valueLabel.setMaximumSize(fixed);

        valueEditor = new JTextField();
        valueEditor.setHorizontalAlignment(JTextField.RIGHT); valueEditor.setMinimumSize(fixed);
        valueEditor.setPreferredSize(fixed); valueEditor.setMaximumSize(fixed);
        valueEditor.setBackground(DARK_GRAY_COLOR); valueEditor.setForeground(LIGHT_GRAY_COLOR);
        valueEditor.setCaretColor(BRAND_ORANGE);
        valueEditor.setBorder(createCompoundBorder(createLineBorder(DARKER_GRAY_COLOR), createEmptyBorder(0, 4, 0, 4)));

        valueLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() != 1) { return; }
                beginCustomTimeEditing();
            }
        });

        valueEditor.addActionListener(e -> commitCustomTime(true));
        valueEditor.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (!editingCustomValue) { return; }
                if (!commitCustomTime(false)) { cancelCustomTimeEditing(); }
            }
        });

        var inputMap = valueEditor.getInputMap(JComponent.WHEN_FOCUSED);
        var actionMap = valueEditor.getActionMap();
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel-edit");
        actionMap.put("cancel-edit", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { cancelCustomTimeEditing(); }
        });

        sliderRow = transparentPanel(new BorderLayout(8, 0));
        sliderRow.add(timeframeSlider, BorderLayout.CENTER);
        sliderRow.add(valueLabel, BorderLayout.EAST);

        timeframeSlider.addChangeListener((ChangeEvent e) -> {
            int minutesPreview = posToMinutes(timeframeSlider.getValue());
            updateValueLabel(minutesPreview);
            if (suppressTimeframeSliderEvents) { return; }
            if (!timeframeSlider.getValueIsAdjusting()) {
                applyTimeframe(minutesPreview, /*updateSlider*/ false);
                // Keep "..." selected for non-preset custom values
                if (!isPreset(minutesPreview)) { btnCustom.setSelected(true); }
            }
        });

        customPanel.add(sliderRow);

        timeframePanel.add(customPanel);

        addVerticalGap(timeframePanel, 10);

        var riskLabel = new JLabel("Risk level: ");
        riskLabel.setHorizontalAlignment(SwingConstants.LEFT); riskLabel.setMaximumSize(riskLabel.getPreferredSize());

        var riskButtonPanel = new JPanel();
        riskButtonPanel.setLayout(new GridLayout(1, 3, 0, 0)); riskButtonPanel.setOpaque(false);

        var riskButtonGroup = new ButtonGroup();
        var initialRiskLevel = preferences.getRiskLevel();
        if (initialRiskLevel == null) {
            initialRiskLevel = RiskLevel.MEDIUM;
            preferences.setRiskLevel(initialRiskLevel);
        }

        String[] riskLabels = {RISK_LOW_LABEL, RISK_MEDIUM_LABEL, RISK_HIGH_LABEL};
        RiskLevel[] riskLevels = {RiskLevel.LOW, RiskLevel.MEDIUM, RiskLevel.HIGH};
        for (int i = 0; i < riskLevels.length; i++) {
            var button = createRiskButton(riskLabels[i], riskLevels[i]);
            riskButtons.put(riskLevels[i], button);
            riskButtonGroup.add(button);
            riskButtonPanel.add(button);
        }

        var riskRow = new JPanel();
        riskRow.setLayout(new BoxLayout(riskRow, BoxLayout.X_AXIS)); riskRow.setOpaque(false);
        riskRow.add(riskLabel);
        addHorizontalGap(riskRow, 10);
        riskRow.add(riskButtonPanel);
        riskRow.add(Box.createHorizontalGlue());

        timeframePanel.add(riskRow);

        updateRiskButtons(initialRiskLevel);
        add(timeframePanel);

        // Initial sync & visibility
        refresh();

        accountSuggestionPreferencesRS.registerListener(ignored -> refresh());
    }

    // ---------- Mapping between slider position (0..STEPS) and minutes (1..1440) using √t ----------
    private static int minutesToPos(int minutes) {
        int m = clampMinutes(minutes);
        double root = Math.sqrt(m);
        double t = (root - SQRT_MIN) / SQRT_RANGE; // 0..1 uniform in sqrt space
        int pos = (int) Math.round(t * STEPS);
        return Math.max(0, Math.min(STEPS, pos));
    }

    private static int posToMinutes(int pos) {
        int p = Math.max(0, Math.min(STEPS, pos));
        double t = (double) p / (double) STEPS;   // 0..1
        double root = SQRT_MIN + t * SQRT_RANGE;
        int m = (int) Math.round(root * root);
        return clampMinutes(m);
    }

    private static int clampMinutes(int m) { return Math.max(MIN_MINUTES, Math.min(MAX_MINUTES, m)); }

    private static boolean isPreset(int minutes) {
        return Arrays.stream(PRESET_MINUTES).anyMatch(value -> value == minutes);
    }

    private String formatMinutes(int m) {
        if (m < 60) return m + "m";
        if (m % 60 == 0) return (m / 60) + "h";
        int mins = m % 60;
        String mm = mins < 10 ? ("0" + mins) : String.valueOf(mins);
        return (m / 60) + "h " + mm + "m";
    }

    private void updateValueLabel(int minutes) { valueLabel.setText(formatMinutes(minutes)); }

    private void updateCustomVisibility() {
        boolean show = btnCustom.isSelected();
        customPanel.setVisible(show);
        customPanel.revalidate();
        customPanel.repaint();
        timeframePanel.revalidate();
        timeframePanel.repaint();
    }

    private void beginCustomTimeEditing() {
        if (editingCustomValue) { return; }

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

    private boolean commitCustomTime(boolean showError) {
        Integer minutes = parseCustomTimeMinutes(valueEditor.getText());
        if (minutes == null || minutes < MIN_MINUTES || minutes > MAX_MINUTES) {
            if (showError) {
                String error = minutes == null
                        ? "Couldn't understand that time. Try 90m, 1h 30m, or 1:30."
                        : String.format(Locale.ROOT, "Time must be between %dm and %dh 00m.", MIN_MINUTES, MAX_MINUTES / 60);
                showMessageDialog(this, error, "Invalid custom time", ERROR_MESSAGE);
                valueEditor.requestFocusInWindow();
                valueEditor.selectAll();
            }
            return false;
        }

        applyTimeframe(minutes, /*updateSlider*/ true);
        restoreValueLabelComponent();
        return true;
    }

    private void cancelCustomTimeEditing() {
        if (!editingCustomValue) { return; }

        updateValueLabel(posToMinutes(timeframeSlider.getValue()));
        restoreValueLabelComponent();
    }

    private void restoreValueLabelComponent() {
        sliderRow.remove(valueEditor);
        sliderRow.add(valueLabel, BorderLayout.EAST);
        sliderRow.revalidate();
        sliderRow.repaint();
        editingCustomValue = false;
    }

    private static Integer parseCustomTimeMinutes(String input) {
        if (input == null) return null;
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return null;
        try {
            if (normalized.contains(":")) {
                String[] parts = normalized.split(":");
                if (parts.length != 2) return null;
                int hours = Integer.parseInt(parts[0].trim()), minutes = Integer.parseInt(parts[1].trim());
                return hours < 0 || minutes < 0 || minutes >= 60 ? null : hours * 60 + minutes;
            }

            Matcher matcher = TIME_TOKEN_PATTERN.matcher(normalized);
            int total = 0, lastEnd = 0;
            while (matcher.find()) {
                if (!normalized.substring(lastEnd, matcher.start()).trim().isEmpty()) return null;
                String number = matcher.group(1), unit = matcher.group(2);
                double value = Double.parseDouble(number);
                if (unit == null || unit.startsWith("m")) {
                    if (number.contains(".")) return null;
                    total += (int) value;
                } else {
                    total += (int) Math.round(value * 60.0);
                }
                lastEnd = matcher.end();
            }
            if (lastEnd == 0) total = Integer.parseInt(normalized);
            else if (!normalized.substring(lastEnd).trim().isEmpty()) return null;
            return total > 0 ? total : null;
        } catch (NumberFormatException ex) { return null; }
    }

    private void updateRiskButtons(RiskLevel level) {
        RiskLevel effective = level != null ? level : RiskLevel.MEDIUM;
        riskButtons.forEach((risk, button) -> button.setSelected(effective == risk));
    }

    private void applyRiskButtonStyle(JToggleButton button, String label, RiskLevel level, boolean selected) {
        Color background, textColor;

        if (selected) {
            switch (level) {
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
                    background = BRAND_ORANGE;
                    textColor = Color.BLACK;
                    break;
            }
        }
        else {
            background = DARKER_GRAY_COLOR;
            textColor = TEXT_COLOR;
        }

        button.setBackground(background);
        button.setText(String.format("<html><font color='%s'>%s</font></html>", colorHex(textColor), label));
    }

    // ---------- UI wiring ----------
    private void applyRiskLevel(RiskLevel level) {
        RiskLevel effective = level != null ? level : RiskLevel.MEDIUM;
        preferences.setRiskLevel(effective);
        suggestions.setSuggestionNeeded(true);
        updateRiskButtons(effective);
    }

    private void applyTimeframe(int minutes, boolean updateSlider) {
        preferences.setTimeframe(minutes);
        suggestions.setSuggestionNeeded(true);

        if (updateSlider) {
            try {
                suppressTimeframeSliderEvents = true;
                timeframeSlider.setValue(minutesToPos(minutes));
            }
            finally {
                suppressTimeframeSliderEvents = false;
            }
            updateValueLabel(minutes);
        }
        syncTimeframeButtons(minutes);
        updateCustomVisibility();
    }

    private void syncTimeframeButtons(int minutes) {
        if (!isPreset(minutes)) { customExplicitlySelected = true; }

        if (customExplicitlySelected) {
            for (JToggleButton button : presetButtons) { button.setSelected(false); }
            btnCustom.setSelected(true);
        }
        else {
            boolean matched = false;
            for (int i = 0; i < presetButtons.length && !matched; i++) {
                presetButtons[i].setSelected(matched = (minutes == PRESET_MINUTES[i]));
            }
            btnCustom.setSelected(!matched);
            customExplicitlySelected = !matched;
        }
    }

    private JToggleButton createPresetButton(String label, int value) {
        return createTimeframeButton(label, () -> {
            customExplicitlySelected = false;
            applyTimeframe(value, /*updateSlider*/ true);
        });
    }

    private JToggleButton createCustomButton(String label) {
        return createTimeframeButton(label, () -> {
            customExplicitlySelected = true;
            // Selecting "..." reveals the slider and shows current value
            int current = clampMinutes(preferences.getTimeframe());
            suppressTimeframeSliderEvents = true;
            timeframeSlider.setValue(minutesToPos(current));
            suppressTimeframeSliderEvents = false;
            updateValueLabel(current);
            updateCustomVisibility(); // shows slider
        });
    }

    private JToggleButton createTimeframeButton(String label, Runnable action) {
        var button = createToggleButton();
        button.addActionListener(e -> action.run());
        button.addChangeListener(e -> applyTimeframeButtonStyle(button, label));
        applyTimeframeButtonStyle(button, label);
        return button;
    }

    private JToggleButton createToggleButton() {
        var button = new JToggleButton();
        button.setMargin(new Insets(2, 4, 2, 4)); button.setFocusPainted(false); button.setOpaque(true);
        button.setBackground(DARKER_GRAY_COLOR); button.setForeground(TEXT_COLOR);
        return button;
    }

    private void applyTimeframeButtonStyle(JToggleButton button, String label) {
        boolean selected = button.isSelected();
        button.setBackground(selected ? BRAND_ORANGE : DARKER_GRAY_COLOR);
        button.setText(String.format("<html><font color='%s'>%s</font></html>", selected ? "black" : "rgb(198, 198, 198)", label));
    }

    private JToggleButton createRiskButton(String label, RiskLevel level) {
        var button = createToggleButton();
        button.addActionListener(e -> applyRiskLevel(level));
        button.addChangeListener(e2 -> applyRiskButtonStyle(button, label, level, button.isSelected()));
        applyRiskButtonStyle(button, label, level, false);
        return button;
    }

    public void refresh() {
        if (!ensureEdt(this::refresh)) return;

        int tf = clampMinutes(preferences.getTimeframe());
        syncTimeframeButtons(tf);

        // Sync slider & label
        try {
            suppressTimeframeSliderEvents = true;
            timeframeSlider.setValue(minutesToPos(tf));
        }
        finally {
            suppressTimeframeSliderEvents = false;
        }
        updateValueLabel(tf);

        // Sync risk level buttons
        updateRiskButtons(preferences.getRiskLevel());

        // Show/hide custom area
        updateCustomVisibility();
    }
}
