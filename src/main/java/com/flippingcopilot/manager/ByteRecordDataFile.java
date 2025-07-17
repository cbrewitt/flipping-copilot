package com.flippingcopilot.manager;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.runelite.client.RuneLite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@EqualsAndHashCode
@AllArgsConstructor
@Getter
public class ByteRecordDataFile {

    public static Path DATA_DIR = Paths.get(RuneLite.RUNELITE_DIR.getPath(), "flipping-copilot", "data");

    static {
        try {
            Files.createDirectories(DATA_DIR);
        } catch (IOException e) {
            throw new RuntimeException("failed to create copilot data directory: " + DATA_DIR, e);
        }
    }

    public Path filePath;
    public int accountId;
    public int startTime; // inclusive
    public int endTime; // exclusive

    public static ByteRecordDataFile fromAccountAndTime(String template, int accountId, int time) {
        LocalDateTime dateTime = LocalDateTime.ofEpochSecond(time, 0, ZoneOffset.UTC);
        int year = dateTime.getYear();
        int quarter = (dateTime.getMonthValue() - 1) / 3 + 1;
        String fileName = template
                .replace("{acc_id}", String.valueOf(accountId))
                .replace("{year}", String.valueOf(year))
                .replace("{quarter}", String.valueOf(quarter));

        // Calculate quarter boundaries
        LocalDateTime quarterStart = LocalDateTime.of(year, (quarter - 1) * 3 + 1, 1, 0, 0);
        LocalDateTime quarterEnd = quarterStart.plusMonths(3);

        int startTime = (int) quarterStart.toEpochSecond(ZoneOffset.UTC);
        int endTime = (int) quarterEnd.toEpochSecond(ZoneOffset.UTC);

        return new ByteRecordDataFile(
                DATA_DIR.resolve(fileName),
                accountId,
                startTime,
                endTime
        );
    }

    public static List<ByteRecordDataFile> listFiles(String template) throws IOException {

        String regexPattern = template
                .replace(".", "\\.")  // Escape the dot
                .replace("{acc_id}", "(\\d+)")
                .replace("{year}", "(\\d{4})")
                .replace("{quarter}", "([1-4])");

        Pattern pattern = Pattern.compile(regexPattern);
        List<ByteRecordDataFile> result = new ArrayList<>();

        if (!Files.exists(DATA_DIR)) {
            return result;
        }

        try (Stream<Path> paths = Files.list(DATA_DIR)) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();
                        Matcher matcher = pattern.matcher(fileName);
                        if (matcher.matches()) {
                            try {
                                int accountId = Integer.parseInt(matcher.group(1));
                                int year = Integer.parseInt(matcher.group(2));
                                int quarter = Integer.parseInt(matcher.group(3));

                                // Calculate quarter boundaries
                                LocalDateTime quarterStart = LocalDateTime.of(year, (quarter - 1) * 3 + 1, 1, 0, 0);
                                LocalDateTime quarterEnd = quarterStart.plusMonths(3);

                                int startTime = (int) quarterStart.toEpochSecond(ZoneOffset.UTC);
                                int endTime = (int) quarterEnd.toEpochSecond(ZoneOffset.UTC);

                                result.add(new ByteRecordDataFile(
                                        path,
                                        accountId,
                                        startTime,
                                        endTime
                                ));
                            } catch (NumberFormatException e) {
                                // Skip invalid files
                            }
                        }
                    });
        }

        return result;
    }

    public static void clearAccountFiles(String template, int accountId) throws IOException {
        List<ByteRecordDataFile> files = listFiles(template);
        for (ByteRecordDataFile f: files) {
            if(f.accountId == accountId) {
                Files.delete(f.filePath);
            }
        }
    }
}
