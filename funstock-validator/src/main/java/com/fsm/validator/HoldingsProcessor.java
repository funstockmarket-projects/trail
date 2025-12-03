import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import models.FileInfo;
import models.ValidationResult;

public class HoldingsProcessor {

    private final TimePeriodChecker time = new TimePeriodChecker();
    private final SerialGenerator serialGen = new SerialGenerator();

    public ValidationResult processHoldings(FileInfo f,
                                            List<FileInfo> missing,
                                            List<FileInfo> branch,
                                            List<FileInfo> main) {

        if (f == null) return ValidationResult.pass();

        // Get today's date
        LocalDate now = LocalDate.now();

        // Use lowercase folder name for safety
        String folder = f.getFolder().toLowerCase();

        // Assign period based on folder type
        switch (folder) {
            case "daily" -> f.setPeriodNumber(now.getDayOfMonth());           // day number
            case "weekly" -> f.setPeriodNumber((now.getDayOfMonth() - 1) / 7 + 1);   // week count
            case "monthly" -> f.setPeriodNumber(now.getMonthValue());         // month number
            case "yearly" -> f.setPeriodNumber(1);                            // always 1_year
        }

        // Assign common fields
        f.setYear(now.getYear());
        f.setMonthName(now.getMonth().name().toLowerCase());

        // Collect all files (branch + missing + main)
        List<FileInfo> all = new ArrayList<>();
        all.addAll(branch);
        all.addAll(missing);
        all.addAll(main);

        // Check duplicate time period BEFORE assigning final name
        String key = time.key(f);
        if (time.exists(key, all)) {
            return ValidationResult.fail("Duplicate time period (holdings)"+key+" "+all.get(0));
        }

        // Assign serial number
        int serial = serialGen.highest(all) + 1;
        f.setSerial(serial);

        // Build final file name using file builder
        String finalName = FileNameBuilder.build(f);

        // check duplicate final filename
        for (FileInfo e : all) {
            if (FileNameBuilder.build(e).equals(finalName)) {
                return ValidationResult.fail("Holdings final filename conflict");
            }
        }

        //Assign final name
        f.setFinalName(finalName);

        return ValidationResult.pass();
    }
}