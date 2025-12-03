import java.util.List;

import models.FileInfo;

public class TimePeriodChecker {

    public String key(FileInfo f) {
        return switch (f.getFolder()) {
            case "Daily" -> f.getYear() + "_day_" + f.getPeriodNumber() + "_" + f.getMonthName();
            case "Weekly" -> f.getYear() + "_week_" + f.getPeriodNumber() + "_" + f.getMonthName();
            case "Monthly" -> f.getYear() + "_month_" + f.getPeriodNumber();
            case "Yearly" -> f.getYear() + "_year";
            default -> "";
        };
    }

    public boolean exists(String key, List<FileInfo> files) {
        return files.stream().anyMatch(f -> key(f).equals(key));
    }
}
