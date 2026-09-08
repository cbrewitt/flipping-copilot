package copilot.ui.components;
import static javax.swing.JOptionPane.*;

import java.util.regex.*;
import java.util.*;
import copilot.model.*;
import lombok.*;
import lombok.extern.slf4j.*;

import javax.swing.*;
import javax.swing.plaf.basic.*;
import java.awt.*;
import java.time.*;
import java.time.temporal.*;
import java.util.function.*;

@Slf4j
public class IntervalDropdown extends JComboBox<String> {

    public static final String SESSION = "Session", ALL_TIME = "All time";
    public static final String PICK_START_DATE = "Pick start date";
    private static final String[] TIME_INTERVAL_STRINGS = {
            "-1h (Past Hour)",
            "-4h (Past 4 Hours)",
            "-12h (Past 12 Hours)",
            "-1d (Past Day)",
            "-1w (Past Week)",
            "-1m (Past Month)",
            SESSION,
            ALL_TIME,
            PICK_START_DATE
    };

    private static final Pattern INTERVAL_PATTERN = Pattern.compile("^-?(\\d+(?:\\.\\d+)?)([hdwmy])[()\\w\\s]*");

    // state
    private final BiConsumer<IntervalTimeUnit, Integer> onIntervalChanged;
    @Getter
    private IntervalTimeUnit selectedIntervalTimeUnit = IntervalTimeUnit.ALL;
    @Getter
    private int selectedIntervalValue = -1;

    public IntervalDropdown(BiConsumer<IntervalTimeUnit, Integer> onIntervalChanged,
                            String initialValue,
                            boolean includeSessionOption) {
        super(Arrays.stream(TIME_INTERVAL_STRINGS).filter(i -> !i.equals(SESSION) || includeSessionOption).toArray(String[]::new));
        this.onIntervalChanged = onIntervalChanged;

        setEditable(true);
        setSelectedItem(initialValue);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
        setBorder(BorderFactory.createEmptyBorder());
        extractAndUpdateTimeInterval(initialValue);
        setEditor(new BasicComboBoxEditor());

        // Add action listener for selection changes and manual edits
        addActionListener(e -> {
            String value = (String) getSelectedItem();
            if (PICK_START_DATE.equals(value)) { showDatePicker(); } else if (extractAndUpdateTimeInterval(value)) {
                onIntervalChanged.accept(selectedIntervalTimeUnit, selectedIntervalValue);
            }
        });
    }

    private void showDatePicker() {
        // Create a date spinner
        var dateModel = new SpinnerDateModel();
        var dateSpinner = new JSpinner(dateModel);
        JSpinner.DateEditor dateEditor = new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd");
        dateSpinner.setEditor(dateEditor);

        dateSpinner.setValue(new Date());
        int result = showConfirmDialog(
                SwingUtilities.getWindowAncestor(this),
                dateSpinner,
                PICK_START_DATE,
                OK_CANCEL_OPTION,
                PLAIN_MESSAGE
        );

        if (result == OK_OPTION) {
            Date selectedDate = (Date) dateSpinner.getValue();
            String intervalString = convertDateToIntervalString(selectedDate);
            setSelectedItem(intervalString);
            extractAndUpdateTimeInterval(intervalString);
            onIntervalChanged.accept(selectedIntervalTimeUnit, selectedIntervalValue);
        }
    }

    /**
     * Converts a selected date to an interval string like "3.5w" or "2d"
     */
    private String convertDateToIntervalString(Date selectedDate) {
        var selected = selectedDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        var today = LocalDate.now();

        long daysDiff = ChronoUnit.DAYS.between(selected, today);

        if (daysDiff < 0) {
            // Future date, return as days
            return Math.abs(daysDiff) + "d (Future)";
        }

        if (daysDiff == 0) return "-0h";
        if (daysDiff < 7) return "-" + daysDiff + "d";
        if (daysDiff < 30) return formatInterval(daysDiff / 7.0, "w");
        if (daysDiff < 365) return formatInterval(daysDiff / 30.0, "m");
        return formatInterval(daysDiff / 365.0, "y");
    }

    private static String formatInterval(double value, String unit) {
        return value == Math.floor(value)
                ? "-" + (int) value + unit
                : String.format("-%.1f%s", value, unit);
    }

    private boolean extractAndUpdateTimeInterval(String value) {
        if (value != null) {
            switch (value) {
                case "Session":
                    selectedIntervalTimeUnit = IntervalTimeUnit.SESSION;
                    selectedIntervalValue = -1;
                    return true;
                case ALL_TIME:
                    selectedIntervalTimeUnit = IntervalTimeUnit.ALL;
                    selectedIntervalValue = -1;
                    return true;
                case PICK_START_DATE:
                    // Don't update here, handled in action listener
                    return false;
                default:
                    Matcher matcher = INTERVAL_PATTERN.matcher(value);
                    if (matcher.matches()) {
                        String numberStr = matcher.group(1);
                        // Handle decimal values by rounding
                        double doubleValue = Double.parseDouble(numberStr);
                        selectedIntervalValue = (int) Math.round(doubleValue);
                        selectedIntervalTimeUnit = IntervalTimeUnit.fromString(matcher.group(2));
                        return true;
                    }
                    break;
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
                return Instant.now().getEpochSecond() - (long) selectedValue * selectedUnits.seconds;
        }
    }
}