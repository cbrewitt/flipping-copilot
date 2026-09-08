package copilot.ui;
import java.util.*;
import java.util.regex.*;
final class ReferenceCustomTime {
    private static final Pattern TIME_TOKEN_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(h|hr|hrs|hour|hours|m|min|mins|minute|minutes)?", Pattern.CASE_INSENSITIVE);
    static Integer parse(String input) {
        if (input == null) { return null; }

        String trimmed = input.trim();
        if (trimmed.isEmpty()) { return null; }

        String normalized = trimmed.toLowerCase(Locale.ROOT);

        if (normalized.contains(":")) {
            String[] parts = normalized.split(":");
            if (parts.length != 2) { return null; }

            try {
                int hours = Integer.parseInt(parts[0].trim()), minutes = Integer.parseInt(parts[1].trim());
                if (hours < 0 || minutes < 0 || minutes >= 60) { return null; }
                return hours * 60 + minutes;
            }
            catch (NumberFormatException ex) { return null; }
        }

        Matcher matcher = TIME_TOKEN_PATTERN.matcher(normalized);
        int total = 0, lastEnd = 0;
        boolean matched = false;

        while (matcher.find()) {
            String between = normalized.substring(lastEnd, matcher.start());
            if (!between.trim().isEmpty()) { return null; }

            String numberPart = matcher.group(1), unit = matcher.group(2);

            double value;
            try {
                value = Double.parseDouble(numberPart);
            }
            catch (NumberFormatException ex) { return null; }

            if (unit == null || unit.toLowerCase(Locale.ROOT).startsWith("m")) {
                if (numberPart.contains(".")) { return null; }
                total += (int) value;
            }
            else {
                total += (int) Math.round(value * 60.0);
            }

            matched = true;
            lastEnd = matcher.end();
        }

        if (matched) {
            String trailing = normalized.substring(lastEnd).trim();
            if (!trailing.isEmpty()) { return null; }
            return total > 0 ? total : null;
        }

        try {
            int minutes = Integer.parseInt(normalized);
            return minutes > 0 ? minutes : null;
        }
        catch (NumberFormatException ex) { return null; }
    }

}
