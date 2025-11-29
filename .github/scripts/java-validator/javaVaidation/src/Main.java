import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.*;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * FUNSTOCKMARKET file validator for GitHub Actions.
 * Validates only changed files in daily/weekly/monthly/yearly folders.
 * Uses LocalDate.now() for date rules.
 */
public class Main {

    enum FolderType {
        DAILY, WEEKLY, MONTHLY, YEARLY
    }

    static class FileInfo {
        FolderType folderType;
        String path;        // e.g. daily/01 2025 1_day Jan.csv
        int serial;
        int year;
        int count;
        String event;
        String monthName;
        int monthNumber;    // 1-12
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("ERROR: Expected one argument: path to changed_files.txt");
            System.exit(1);
        }

        Path changedFilesPath = Paths.get(args[0]);
        LocalDate today = LocalDate.now();
        int currentYear = today.getYear();
        int currentMonth = today.getMonthValue();

        List<String> errors = new ArrayList<>();

        // 1. Read changed files from PR
        List<String> changedFiles = readChangedFiles(changedFilesPath, errors);

        // 2. Filter only 4 folders & parse file info
        List<FileInfo> dailyFiles = new ArrayList<>();
        List<FileInfo> weeklyFiles = new ArrayList<>();
        List<FileInfo> monthlyFiles = new ArrayList<>();
        List<FileInfo> yearlyFiles = new ArrayList<>();

        for (String path : changedFiles) {
            FolderType type = getFolderType(path);
            if (type == null) {
                // Not in daily/weekly/monthly/yearly → ignore
                continue;
            }

            if (!path.toLowerCase().endsWith(".csv")) {
                errors.add("ERROR: File '" + path + "' does not have .csv extension.");
                continue;
            }

            String fileName = path.substring(path.lastIndexOf('/') + 1, path.length() - 4); // remove ".csv"
            Optional<FileInfo> infoOpt = parseFileName(type, path, fileName, errors);
            infoOpt.ifPresent(info -> {
                switch (info.folderType) {
                    case DAILY -> dailyFiles.add(info);
                    case WEEKLY -> weeklyFiles.add(info);
                    case MONTHLY -> monthlyFiles.add(info);
                    case YEARLY -> yearlyFiles.add(info);
                }
            });
        }

        // If no relevant files changed, just succeed
        if (dailyFiles.isEmpty() && weeklyFiles.isEmpty() && monthlyFiles.isEmpty() && yearlyFiles.isEmpty()) {
            System.out.println("✅ No files in daily/weekly/monthly/yearly folders changed. Skipping validation.");
            System.exit(0);
        }

        // 3. Load tracker serials for each folder
        Map<FolderType, Integer> lastSerialMap = new EnumMap<>(FolderType.class);
        lastSerialMap.put(FolderType.DAILY, readTracker("serial_daily.txt", errors));
        lastSerialMap.put(FolderType.WEEKLY, readTracker("serial_weekly.txt", errors));
        lastSerialMap.put(FolderType.MONTHLY, readTracker("serial_monthly.txt", errors));
        lastSerialMap.put(FolderType.YEARLY, readTracker("serial_yearly.txt", errors));

        // 4. Global rules (year & month name)
        applyGlobalYearAndMonthRules(dailyFiles, weeklyFiles, monthlyFiles, yearlyFiles, today, errors);

        // 5. Folder specific rules
        validateDaily(dailyFiles, today, errors);
        validateWeekly(weeklyFiles, today, errors);
        validateMonthly(monthlyFiles, today, errors);
        validateYearly(yearlyFiles, today, errors);

        // 6. Serial rules per folder (using tracker)
        validateSerialForFolder(FolderType.DAILY, dailyFiles, lastSerialMap.get(FolderType.DAILY), errors);
        validateSerialForFolder(FolderType.WEEKLY, weeklyFiles, lastSerialMap.get(FolderType.WEEKLY), errors);
        validateSerialForFolder(FolderType.MONTHLY, monthlyFiles, lastSerialMap.get(FolderType.MONTHLY), errors);
        validateSerialForFolder(FolderType.YEARLY, yearlyFiles, lastSerialMap.get(FolderType.YEARLY), errors);

        // 7. Duplicate period rules (only within PR, not whole repo)
        checkDuplicatePeriods(dailyFiles, weeklyFiles, monthlyFiles, yearlyFiles, errors);

        // 8. Print all errors or success
        if (!errors.isEmpty()) {
            System.out.println("❌ FUNSTOCKMARKET VALIDATION FAILED");
            errors.forEach(System.out::println);
            System.exit(1);
        } else {
            System.out.println("✅ FUNSTOCKMARKET validation passed for all changed files.");
            System.exit(0);
        }
    }

    // --------- UTIL: read changed files ---------

    private static List<String> readChangedFiles(Path path, List<String> errors) {
        List<String> files = new ArrayList<>();
        if (!Files.exists(path)) {
            errors.add("ERROR: changed_files.txt not found at " + path.toAbsolutePath());
            return files;
        }
        try (BufferedReader br = Files.newBufferedReader(path)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    files.add(line);
                }
            }
        } catch (IOException e) {
            errors.add("ERROR: Unable to read changed_files.txt: " + e.getMessage());
        }
        return files;
    }

    // --------- UTIL: folder detection ---------

    private static FolderType getFolderType(String path) {
        if (path.startsWith("daily/")) return FolderType.DAILY;
        if (path.startsWith("weekly/")) return FolderType.WEEKLY;
        if (path.startsWith("monthly/")) return FolderType.MONTHLY;
        if (path.startsWith("yearly/")) return FolderType.YEARLY;
        return null;
    }

    // --------- UTIL: parse file name ---------

    private static Optional<FileInfo> parseFileName(FolderType folderType, String path, String fileName, List<String> errors) {
        // Expected: <serial> <year> <count>_<event> <monthName>
        // Example: 01 2025 1_day Jan
        String[] parts = fileName.split(" ");
        if (parts.length != 4) {
            errors.add("ERROR: File '" + path + "' does not match pattern '<serial> <year> <count>_<event> <monthName>.csv'");
            return Optional.empty();
        }

        FileInfo info = new FileInfo();
        info.folderType = folderType;
        info.path = path;

        // serial
        try {
            info.serial = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            errors.add("ERROR: Invalid serial in '" + path + "'. Found '" + parts[0] + "'");
            return Optional.empty();
        }

        // year
        try {
            info.year = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            errors.add("ERROR: Invalid year in '" + path + "'. Found '" + parts[1] + "'");
            return Optional.empty();
        }

        // count_event
        String countEvent = parts[2];
        int underscoreIdx = countEvent.indexOf('_');
        if (underscoreIdx == -1 || underscoreIdx == countEvent.length() - 1) {
            errors.add("ERROR: Invalid count_event in '" + path + "'. Expected '<count>_<event>'");
            return Optional.empty();
        }
        String countStr = countEvent.substring(0, underscoreIdx);
        String event = countEvent.substring(underscoreIdx + 1);

        try {
            info.count = Integer.parseInt(countStr);
        } catch (NumberFormatException e) {
            errors.add("ERROR: Invalid count in '" + path + "'. Found '" + countStr + "'");
            return Optional.empty();
        }
        info.event = event;

        // monthName
        info.monthName = parts[3];
        int month = monthFromName(info.monthName);
        if (month == -1) {
            errors.add("ERROR: Invalid month name '" + info.monthName + "' in file '" + path + "'");
        } else {
            info.monthNumber = month;
        }

        // Event vs folder validation
        String expectedEvent = switch (folderType) {
            case DAILY -> "day";
            case WEEKLY -> "week";
            case MONTHLY -> "month";
            case YEARLY -> "year";
        };
        if (!event.equalsIgnoreCase(expectedEvent)) {
            errors.add("ERROR: Event '" + event + "' does not match folder '" + folderType.name().toLowerCase() +
                    "' in file '" + path + "'. Expected '" + expectedEvent + "'.");
        }

        return Optional.of(info);
    }

    // --------- UTIL: month mapping ---------

    private static int monthFromName(String name) {
        if (name == null) return -1;
        String n = name.trim().toLowerCase();

        return switch (n) {
            case "jan", "january" -> 1;
            case "feb", "february" -> 2;
            case "mar", "march" -> 3;
            case "apr", "april" -> 4;
            case "may" -> 5;
            case "jun", "june" -> 6;
            case "jul", "july" -> 7;
            case "aug", "august" -> 8;
            case "sep", "sept", "september" -> 9;
            case "oct", "october" -> 10;
            case "nov", "november" -> 11;
            case "dec", "december" -> 12;
            default -> -1;
        };
    }

    // --------- UTIL: read tracker ---------

    private static int readTracker(String name, List<String> errors) {
        Path p = Paths.get(name);
        if (!Files.exists(p)) {
            errors.add("ERROR: Tracker file '" + name + "' not found in repo root.");
            return 0;
        }
        try (BufferedReader br = Files.newBufferedReader(p)) {
            String line = br.readLine();
            if (line == null) return 0;
            line = line.trim();
            if (line.isEmpty()) return 0;
            return Integer.parseInt(line);
        } catch (Exception e) {
            errors.add("ERROR: Cannot read tracker '" + name + "': " + e.getMessage());
            return 0;
        }
    }

    // --------- GLOBAL YEAR & MONTH RULES ---------

    private static void applyGlobalYearAndMonthRules(
            List<FileInfo> daily,
            List<FileInfo> weekly,
            List<FileInfo> monthly,
            List<FileInfo> yearly,
            LocalDate today,
            List<String> errors
    ) {
        int currentYear = today.getYear();
        int currentMonth = today.getMonthValue();

        for (FileInfo info : allOf(daily, weekly, monthly, yearly)) {
            // Year rule
            if (info.year < 2000) {
                errors.add("ERROR: Year '" + info.year + "' is less than 2000 in file '" + info.path + "'.");
            } else if (info.year > currentYear) {
                errors.add("ERROR: Year '" + info.year + "' is in the future in file '" + info.path + "'.");
            }

            // Month name validity already checked; now future month rule (same year)
            if (info.monthNumber != -1 && info.year == currentYear && info.monthNumber > currentMonth) {
                String mName = Month.of(info.monthNumber).getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                errors.add("ERROR: '" + info.monthName + " " + info.year + "' is a future month in file '" + info.path + "'.");
            }
        }
    }

    // --------- DAILY RULES ---------

    private static void validateDaily(List<FileInfo> dailyFiles, LocalDate today, List<String> errors) {
        for (FileInfo info : dailyFiles) {
            if (info.monthNumber == -1) {
                // month error already reported
                continue;
            }
            try {
                LocalDate date = LocalDate.of(info.year, info.monthNumber, info.count);
                if (date.isAfter(today)) {
                    errors.add("ERROR: '" + date + "' is in the future for daily file '" + info.path + "'.");
                }
                DayOfWeek dow = date.getDayOfWeek();
                if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                    errors.add("ERROR: '" + date + "' falls on a weekend for daily file '" + info.path + "'.");
                }
            } catch (DateTimeException e) {
                errors.add("ERROR: '" + info.year + "-" + info.monthNumber + "-" + info.count +
                        "' is not a valid calendar date for daily file '" + info.path + "'.");
            }
        }
    }

    // --------- WEEKLY RULES ---------

    private static void validateWeekly(List<FileInfo> weeklyFiles, LocalDate today, List<String> errors) {
        int currentYear = today.getYear();
        int currentMonth = today.getMonthValue();

        for (FileInfo info : weeklyFiles) {
            if (info.count < 1 || info.count > 5) {
                errors.add("ERROR: Week number '" + info.count + "' is invalid in '" + info.path + "'. Only 1–5 are allowed.");
                continue;
            }
            if (info.monthNumber == -1) continue;

            // Does this week exist?
            try {
                LocalDate firstOfMonth = LocalDate.of(info.year, info.monthNumber, 1);
                LocalDate firstMonday = firstOfMonth;
                while (firstMonday.getDayOfWeek() != DayOfWeek.MONDAY) {
                    firstMonday = firstMonday.plusDays(1);
                }
                LocalDate weekMonday = firstMonday.plusWeeks(info.count - 1);
                if (weekMonday.getMonthValue() != info.monthNumber) {
                    errors.add("ERROR: Week " + info.count + " does not exist in " +
                            info.monthName + " " + info.year + " for file '" + info.path + "'.");
                }

                // Future period check (year + month)
                if (info.year > currentYear ||
                        (info.year == currentYear && info.monthNumber > currentMonth)) {
                    errors.add("ERROR: '" + info.monthName + " " + info.year +
                            "' is a future period for weekly file '" + info.path + "'.");
                }

                // Future week (same month & year)
                if (info.year == currentYear && info.monthNumber == currentMonth) {
                    // Determine current week number based on Mondays
                    LocalDate todayDate = today;
                    int currentWeekNumber = 0;
                    if (!todayDate.isBefore(firstMonday)) {
                        long diffDays = ChronoUnit.DAYS.between(firstMonday, todayDate);
                        currentWeekNumber = (int) (diffDays / 7) + 1;
                        if (currentWeekNumber > 5) currentWeekNumber = 5;
                    }
                    if (currentWeekNumber == 0 || info.count > currentWeekNumber) {
                        errors.add("ERROR: Week '" + info.count + "' of '" + info.monthName + " " + info.year +
                                "' is a future week for file '" + info.path + "'.");
                    }
                }

            } catch (DateTimeException e) {
                errors.add("ERROR: Invalid month/year combination for weekly file '" + info.path + "'.");
            }
        }
    }

    // --------- MONTHLY RULES ---------

    private static void validateMonthly(List<FileInfo> monthlyFiles, LocalDate today, List<String> errors) {
        int currentYear = today.getYear();
        int currentMonth = today.getMonthValue();

        for (FileInfo info : monthlyFiles) {
            if (info.count < 1 || info.count > 12) {
                errors.add("ERROR: Invalid month number '" + info.count +
                        "' in monthly file '" + info.path + "'. Only 1–12 allowed.");
                continue;
            }
            if (info.monthNumber == -1) continue;

            if (info.count != info.monthNumber) {
                errors.add("ERROR: Month number '" + info.count + "' does not match month '" +
                        info.monthName + "' in file '" + info.path + "'.");
            }

            if (info.year == currentYear && info.monthNumber > currentMonth) {
                errors.add("ERROR: '" + info.monthName + " " + info.year +
                        "' is in the future for monthly file '" + info.path + "'.");
            }
        }
    }

    // --------- YEARLY RULES ---------

    private static void validateYearly(List<FileInfo> yearlyFiles, LocalDate today, List<String> errors) {
        int currentYear = today.getYear();
        Month currentMonthEnum = today.getMonth();

        for (FileInfo info : yearlyFiles) {
            // count must always be 1
            if (info.count != 1) {
                errors.add("ERROR: Invalid yearly count '" + info.count +
                        "' in file '" + info.path + "'. Only '1_year' is allowed.");
            }

            // month must be December
            if (info.monthNumber != 12) {
                errors.add("ERROR: Yearly file '" + info.path +
                        "' must use December as month (e.g. 'Dec' or 'December').");
            }

            // year boundaries
            if (info.year < 2000) {
                errors.add("ERROR: Year '" + info.year + "' is less than 2000 for yearly file '" + info.path + "'.");
            } else if (info.year > currentYear) {
                errors.add("ERROR: Year '" + info.year + "' is in the future for yearly file '" + info.path + "'.");
            }

            // Upload rule: current year allowed ONLY in December
            if (info.year == currentYear && currentMonthEnum != Month.DECEMBER) {
                errors.add("ERROR: Yearly data for '" + info.year +
                        "' can only be uploaded during December for file '" + info.path + "'.");
            }
        }
    }

    // --------- SERIAL RULES PER FOLDER ---------

    private static void validateSerialForFolder(
            FolderType folderType,
            List<FileInfo> files,
            int lastSerial,
            List<String> errors
    ) {
        if (files.isEmpty()) return;

        files.sort(Comparator.comparingInt(f -> f.serial));

        int expected = lastSerial + 1;
        int prevSerial = -1;
        for (FileInfo info : files) {
            if (info.serial <= lastSerial) {
                errors.add("ERROR: Serial '" + info.serial + "' in file '" + info.path +
                        "' is not greater than last tracked serial '" + lastSerial +
                        "' for folder '" + folderType.name().toLowerCase() + "'.");
            }
            if (info.serial != expected) {
                errors.add("ERROR: Serial '" + info.serial + "' in file '" + info.path +
                        "' is not in correct sequence. Expected '" + expected + "'.");
                expected = info.serial + 1; // resync expected after error
            } else {
                expected++;
            }

            if (prevSerial == info.serial) {
                errors.add("ERROR: Duplicate serial '" + info.serial +
                        "' detected in folder '" + folderType.name().toLowerCase() +
                        "' for file '" + info.path + "'.");
            }
            prevSerial = info.serial;
        }
    }

    // --------- DUPLICATE PERIODS (inside PR only) ---------

    private static void checkDuplicatePeriods(
            List<FileInfo> daily,
            List<FileInfo> weekly,
            List<FileInfo> monthly,
            List<FileInfo> yearly,
            List<String> errors
    ) {
        // daily: year+month+day
        Map<String, String> seen = new HashMap<>();
        for (FileInfo info : daily) {
            String key = info.year + "-" + info.monthNumber + "-" + info.count;
            addOrError(seen, key, info.path, "daily date", errors);
        }

        // weekly: year+month+weekNumber
        seen.clear();
        for (FileInfo info : weekly) {
            String key = info.year + "-" + info.monthNumber + "-W" + info.count;
            addOrError(seen, key, info.path, "weekly period", errors);
        }

        // monthly: year+month
        seen.clear();
        for (FileInfo info : monthly) {
            String key = info.year + "-" + info.monthNumber;
            addOrError(seen, key, info.path, "monthly period", errors);
        }

        // yearly: year
        seen.clear();
        for (FileInfo info : yearly) {
            String key = String.valueOf(info.year);
            addOrError(seen, key, info.path, "yearly period", errors);
        }
    }

    private static void addOrError(Map<String, String> seen, String key, String newPath, String label, List<String> errors) {
        if (seen.containsKey(key)) {
            errors.add("ERROR: Duplicate " + label + " for key '" + key +
                    "' in files '" + seen.get(key) + "' and '" + newPath + "'.");
        } else {
            seen.put(key, newPath);
        }
    }

    // --------- small helper ---------

    @SafeVarargs
    private static List<FileInfo> allOf(List<FileInfo>... lists) {
        List<FileInfo> result = new ArrayList<>();
        for (List<FileInfo> l : lists) result.addAll(l);
        return result;
    }
}
