import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import models.FileInfo;
import models.ValidationResult;

public class MissingFileProcessor {

    private final TimePeriodChecker time = new TimePeriodChecker();
    private final SerialGenerator serialGen = new SerialGenerator();

    public ValidationResult processMissing(List<FileInfo> missing,
                                           List<FileInfo> branch,
                                           List<FileInfo> main) {

        if (missing.isEmpty()) return ValidationResult.pass();

        // sort missing files
        missing.sort(Comparator.comparing(FileInfo::getYear)
                .thenComparing(FileInfo::getMonthName)
                .thenComparing(FileInfo::getPeriodNumber));

        int highest = serialGen.highest(branch);

        List<FileInfo> allFiles = new ArrayList<>();
        allFiles.addAll(branch);
        allFiles.addAll(main);

        for (FileInfo f : missing) {

            // time period duplicate
            String key = time.key(f);
            if (time.exists(key, allFiles))
                return ValidationResult.fail("Duplicate time period for missing file");

            // assign serial
            highest++;
            f.setSerial(highest);

            // final name check
            String finalName = FileNameBuilder.build(f);
            for (FileInfo existing : allFiles) {
                if (FileNameBuilder.build(existing).equals(finalName))
                    return ValidationResult.fail("Final filename duplicate for missing file");
            }
            f.setFinalName(FileNameBuilder.build(f));

            allFiles.add(f);
           
        }
        return ValidationResult.pass();
    }
}