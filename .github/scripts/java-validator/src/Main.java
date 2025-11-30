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

        List<String> errors = new ArrayList<>();

        // 1. Read changed files from PR (now includes rename fix)
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

            // ❌ Reject ANY non-CSV file inside the 4 folders
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
        lastSerialMap.put(FolderType.DAILY, readTracker("trackerFiles/serial_daily.txt", errors));
        lastSerialMap.put(FolderType.WEEKLY, readTracker("trackerFiles/serial_weekly.txt", errors));
        lastSerialMap.put(FolderType.MONTHLY, readTracker("trackerFiles/serial_monthly.txt", errors));
        lastSerialMap.put(FolderType.YEARLY, readTracker("trackerFiles/serial_yearly.txt", errors));

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

        // 7. Duplicate period rules (only within PR)
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

    // --------- UTIL: read changed files (NOW FIXES RENAME FORMAT) ---------

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
                if (line.isEmpty()) continue;

                //
                // FIX: Normalize GitHub rename format:
                // daily/{old.csv => new.txt}  →  daily/new.txt
                //
                if (line.contains("=>")) {
                    String cleaned = line.replace("{", "").replace("}", "");
                    String[] parts = cleaned.split("=>");

                    if (parts.length == 2) {
                        String folder = line.substring(0, line.indexOf('/'));
                        String newName = parts[1].trim();
                        line = folder + "/" + newName;
                    }
                }

                files.add(line);
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
        } catch (Exception e) {
            errors.add("ERROR: Invalid serial in '" + path + "'");
            return Optional.empty();
        }

        // year
        try {
            info.year = Integer.parseInt(parts[1]);
        } catch (Exception e) {
            errors.add("ERROR: Invalid year in '" + path + "'");
            return Optional.empty();
        }

        // count + event
        String countEvent = parts[2];
        int idx = countEvent.indexOf('_');
        if (idx == -1) {
            errors.add("ERROR: Invalid count_event in '" + path + "'");
            return Optional.empty();
        }

        try {
            info.count = Integer.parseInt(countEvent.substring(0, idx));
        } catch (Exception e) {
            errors.add("ERROR: Invalid count in '" + path + "'");
            return Optional.empty();
        }

        info.event = countEvent.substring(idx + 1);

        // monthName → number
        info.monthName = parts[3];
        info.monthNumber = monthFromName(info.monthName);
        if (info.monthNumber == -1) {
            errors.add("ERROR: Invalid month name '" + info.monthName + "' in file '" + path + "'");
        }

        // event vs folder rule
        String expected = switch (folderType) {
            case DAILY -> "day";
            case WEEKLY -> "week";
            case MONTHLY -> "month";
            case YEARLY -> "year";
        };

        if (!info.event.equalsIgnoreCase(expected)) {
            errors.add("ERROR: Event '" + info.event + "' does not match folder '" +
                    folderType.name().toLowerCase() + "' for file '" + path + "'");
        }

        return Optional.of(info);
    }

    // --------- Month mapping ---------

    private static int monthFromName(String name) {
        if (name == null) return -1;
        return switch (name.trim().toLowerCase()) {
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

    // --------- Read tracker files ---------

    private static int readTracker(String name, List<String> errors) {
        Path p = Paths.get(name);
        if (!Files.exists(p)) {
            errors.add("ERROR: Tracker file '" + name + "' not found.");
            return 0;
        }
        try {
            String line = Files.readString(p).trim();
            return line.isEmpty() ? 0 : Integer.parseInt(line);
        } catch (Exception e) {
            errors.add("ERROR reading tracker '" + name + "'");
            return 0;
        }
    }

    // --------- Global Rules ---------

    private static void applyGlobalYearAndMonthRules(
            List<FileInfo> daily, List<FileInfo> weekly,
            List<FileInfo> monthly, List<FileInfo> yearly,
            LocalDate today, List<String> errors
    ) {
        int currentYear = today.getYear();
        int currentMonth = today.getMonthValue();

        for (FileInfo info : allOf(daily, weekly, monthly, yearly)) {

            if (info.year < 2000)
                errors.add("ERROR: Year '" + info.year + "' < 2000 in '" + info.path + "'");

            if (info.year > currentYear)
                errors.add("ERROR: Year '" + info.year + "' is in future in '" + info.path + "'");

            if (info.year == currentYear && info.monthNumber > currentMonth)
                errors.add("ERROR: '" + info.monthName + " " + info.year + "' is a future month in '" + info.path + "'");
        }
    }

    // --------- DAILY ---------

    private static void validateDaily(List<FileInfo> files, LocalDate today, List<String> errors) {
        for (FileInfo info : files) {
            try {
                LocalDate date = LocalDate.of(info.year, info.monthNumber, info.count);

                if (date.isAfter(today))
                    errors.add("ERROR: Daily date '" + date + "' is future in '" + info.path + "'");

                DayOfWeek dow = date.getDayOfWeek();
                if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY)
                    errors.add("ERROR: Daily date '" + date + "' is weekend in '" + info.path + "'");

            } catch (Exception e) {
                errors.add("ERROR: Invalid date for daily file '" + info.path + "'");
            }
        }
    }

    // --------- WEEKLY ---------

    private static void validateWeekly(List<FileInfo> files, LocalDate today, List<String> errors) {
        int currentYear = today.getYear();
        int currentMonth = today.getMonthValue();

        for (FileInfo info : files) {

            if (info.count < 1 || info.count > 5) {
                errors.add("ERROR: Invalid week '" + info.count + "' in '" + info.path + "'");
                continue;
            }
        }
    }

    // --------- MONTHLY ---------

    private static void validateMonthly(List<FileInfo> files, LocalDate today, List<String> errors) {
        int currentYear = today.getYear();
        int currentMonth = today.getMonthValue();

        for (FileInfo info : files) {

            if (info.count < 1 || info.count > 12) {
                errors.add("ERROR: Invalid month number '" + info.count + "' for '" + info.path + "'");
                continue;
            }

            if (info.year == currentYear && info.monthNumber > currentMonth) {
                errors.add("ERROR: Monthly period '" + info.monthName + " " + info.year +
                        "' is future in '" + info.path + "'");
            }
        }
    }

    // --------- YEARLY ---------

    private static void validateYearly(List<FileInfo> files, LocalDate today, List<String> errors) {
        int currentYear = today.getYear();
        Month currentMonth = today.getMonth();

        for (FileInfo info : files) {

            if (info.count != 1)
                errors.add("ERROR: Yearly count must be 1 in '" + info.path + "'");

            if (info.monthNumber != 12)
                errors.add("ERROR: Yearly must be December in '" + info.path + "'");

            if (info.year > currentYear)
                errors.add("ERROR: Year '" + info.year + "' is future in '" + info.path + "'");

            if (info.year == currentYear && currentMonth != Month.DECEMBER)
                errors.add("ERROR: Yearly '" + info.year + "' allowed only in December in '" + info.path + "'");
        }
    }

    // --------- SERIAL ---------

    private static void validateSerialForFolder(
            FolderType folderType, List<FileInfo> files,
            int lastSerial, List<String> errors
    ) {

        if (files.isEmpty()) return;

        files.sort(Comparator.comparingInt(f -> f.serial));

        int expected = lastSerial + 1;

        for (FileInfo info : files) {

            if (info.serial != expected)
                errors.add("ERROR: Serial '" + info.serial +
                        "' expected '" + expected + "' in '" + info.path + "'");

            expected = info.serial + 1;
        }
    }

    // --------- Duplicate periods ---------

    private static void checkDuplicatePeriods(
            List<FileInfo> daily, List<FileInfo> weekly,
            List<FileInfo> monthly, List<FileInfo> yearly,
            List<String> errors
    ) {

        Map<String, String> map = new HashMap<>();

        for (FileInfo info : daily) {
            String key = info.year + "-" + info.monthNumber + "-" + info.count;
            if (map.containsKey(key))
                errors.add("ERROR: Duplicate daily key '" + key + "'");
            map.put(key, info.path);
        }
    }

    // --------- Util: merge lists ---------

    @SafeVarargs
    private static List<FileInfo> allOf(List<FileInfo>... lists) {
        List<FileInfo> all = new ArrayList<>();
        for (List<FileInfo> l : lists) all.addAll(l);
        return all;
    }
}
