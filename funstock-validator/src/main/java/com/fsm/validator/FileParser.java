import java.util.regex.Matcher;
import java.util.regex.Pattern;

import models.FileInfo;

public class FileParser {

    private static final String DAILY = "Daily";
    private static final String WEEKLY = "Weekly";
    private static final String MONTHLY = "Monthly";
    private static final String YEARLY = "Yearly";

    public FileInfo parse(String fileName, String folder) {

        // holdings.csv special case
        if (fileName.equalsIgnoreCase("holdings.csv")) {
            FileInfo f = new FileInfo();
            f.setOriginalName(fileName);
            f.setFolder(folder);
            f.setHoldings(true);
            return f;
        }

        String regex = getPattern(folder);
        if (regex == null) return null;

        Matcher m = Pattern.compile(regex).matcher(fileName);
        if (!m.matches()) return null;

        FileInfo info = new FileInfo();
        info.setOriginalName(fileName);
        info.setFolder(folder);

        String serialStr = m.group(1);
        info.setSerial(serialStr == null ? null : Integer.parseInt(serialStr));
        info.setMissing(serialStr == null);

        if (YEARLY.equals(folder)) {
            // YEARLY PATTERN: ^(\d+)?\s*(\d{4})\s*1_year\s+([A-Za-z]+)\.csv$
            // groups: 1=serial, 2=year, 3=monthName
            info.setYear(Integer.parseInt(m.group(2)));
            info.setPeriodNumber(1); // always 1_year
            info.setMonthName(m.group(3).toLowerCase());
        } else {
            // DAILY / WEEKLY / MONTHLY:
            // ^(\d+)? (\d{4}) (\d+)_<event> ([A-Za-z]+).csv
            info.setYear(Integer.parseInt(m.group(2)));
            info.setPeriodNumber(Integer.parseInt(m.group(3))); // day/week/month number
            info.setMonthName(m.group(4).toLowerCase());
        }

        return info;
    }

    private String getPattern(String folder) {
        return switch (folder) {
            case DAILY ->
                    "^(\\d+)?\\s*(\\d{4})\\s*(\\d+)_day\\s+([A-Za-z]+)\\.csv$";
            case WEEKLY ->
                    "^(\\d+)?\\s*(\\d{4})\\s*(\\d+)_week\\s+([A-Za-z]+)\\.csv$";
            case MONTHLY ->
                    "^(\\d+)?\\s*(\\d{4})\\s*(\\d+)_month\\s+([A-Za-z]+)\\.csv$";
            case YEARLY ->
                    "^(\\d+)?\\s*(\\d{4})\\s*1_year\\s+([A-Za-z]+)\\.csv$";
            default -> null;
        };
    }
}