package com.flippingcopilot.ui.components;

import com.flippingcopilot.model.IntervalTimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import java.awt.*;
import java.time.Instant;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class IntervalDropdown extends JComboBox<String> {

    private static final String[] TIME_INTERVAL_STRINGS = {
            "-1h (Past Hour)",
            "-4h (Past 4 Hours)",
            "-12h (Past 12 Hours)",
            "-1d (Past Day)",
            "-1w (Past Week)",
            "-1m (Past Month)",
            "Session",
            "All"
    };

    private static final Pattern INTERVAL_PATTERN = Pattern.compile("^-?(\\d+)([hdwmy])[()\\w\\s]*");

    // state
    private final BiConsumer<IntervalTimeUnit, Integer> onIntervalChanged;
    @Getter
    private IntervalTimeUnit selectedIntervalTimeUnit = IntervalTimeUnit.SESSION;
    private int selectedIntervalValue = -1;

    public IntervalDropdown(BiConsumer<IntervalTimeUnit, Integer> onIntervalChanged) {
        super(TIME_INTERVAL_STRINGS);
        this.onIntervalChanged = onIntervalChanged;
        setupDropdown();
    }

    private void setupDropdown() {
        setEditable(true);
        setSelectedItem("Session");
        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
        setBorder(BorderFactory.createEmptyBorder());

        // Create a custom editor to handle both selection and manual input
        ComboBoxEditor editor = new BasicComboBoxEditor() {
            @Override
            public void setItem(Object item) {
                super.setItem(item);
                if (item != null) {
                    String value = item.toString();
                    if (extractAndUpdateTimeInterval(value)) {
                        onIntervalChanged.accept(selectedIntervalTimeUnit, selectedIntervalValue);
                    }
                }
            }
        };
        setEditor(editor);

        // Add action listener for selection changes and manual edits
        addActionListener(e -> {
            String value = (String) getSelectedItem();
            if (extractAndUpdateTimeInterval(value)) {
                onIntervalChanged.accept(selectedIntervalTimeUnit, selectedIntervalValue);
            }
        });
    }

    private boolean extractAndUpdateTimeInterval(String value) {
        if (value != null) {
            if ("Session".equals(value)) {
                selectedIntervalTimeUnit = IntervalTimeUnit.SESSION;
                selectedIntervalValue = -1;
                return true;
            } else if ("All".equals(value)) {
                selectedIntervalTimeUnit = IntervalTimeUnit.ALL;
                selectedIntervalValue = -1;
                return true;
            } else {
                Matcher matcher = INTERVAL_PATTERN.matcher(value);
                if (matcher.matches()) {
                    selectedIntervalValue = Integer.parseInt(matcher.group(1));
                    selectedIntervalTimeUnit = IntervalTimeUnit.fromString(matcher.group(2));
                    return true;
                }
            }
        }
        return false;
    }

     /**
     * Resets the dropdown to "Session" selection.
     */
    public void resetToSession() {
        setSelectedItem("Session");
        selectedIntervalTimeUnit = IntervalTimeUnit.SESSION;
        selectedIntervalValue = -1;
        onIntervalChanged.accept(selectedIntervalTimeUnit, selectedIntervalValue);
    }

    public static long calculateStartTime(IntervalTimeUnit selectedUnits, Integer selectedValue, Integer sessionStartTime) {
        switch (selectedUnits) {
            case ALL:
                return 1;
            case SESSION:
                return sessionStartTime;
            default:
                return Instant.now().getEpochSecond() - (long) selectedValue * selectedUnits.getSeconds();
        }
    }
}