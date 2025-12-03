import models.FileInfo;

public class FileNameBuilder {

    public static String build(FileInfo f) {

        return switch (f.getFolder()) {
            case "Daily" ->
                    f.getSerial() + " " + f.getYear() + " " +
                    f.getPeriodNumber() + "_day " + capitalize(f.getMonthName()) + ".csv";

            case "Weekly" ->
                    f.getSerial() + " " + f.getYear() + " " +
                    f.getPeriodNumber() + "_week " + capitalize(f.getMonthName()) + ".csv";

            case "Monthly" ->
                    f.getSerial() + " " + f.getYear() + " " +
                    f.getPeriodNumber() + "_month " + capitalize(f.getMonthName()) + ".csv";

            case "Yearly" ->
                    f.getSerial() + " " + f.getYear() + " 1_year " +
                    capitalize(f.getMonthName()) + ".csv";

            default -> "";
        };
    }

    private static String capitalize(String s) {
        return s.substring(0,1).toUpperCase() + s.substring(1);
    }
}