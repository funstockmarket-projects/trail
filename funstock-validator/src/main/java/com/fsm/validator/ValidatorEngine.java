import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import models.FileInfo;
import models.ValidationResult;

public class ValidatorEngine {

    private final FileParser parser = new FileParser();
    // private final TimePeriodChecker timeChecker = new TimePeriodChecker();
    private final MissingFileProcessor missingProcessor = new MissingFileProcessor();
    private final HoldingsProcessor holdingsProcessor = new HoldingsProcessor();
    private final GitHubService git = new GitHubService();

    public void run() {

        // folders to validate
        List<String> folders = Arrays.asList("Daily", "Weekly", "Monthly", "Yearly");

        for (String folder : folders) {
            ValidationResult result = validateFolder(folder);

            if (!result.isValid()) {
                System.out.println("❌ DELETE COMMIT — " + result.getMessage());
                System.exit(1);
            }
        }

        System.out.println("✅ VALID COMMIT — All folders passed validation");
    }

    private ValidationResult validateFolder(String folder) {

        List<String> rawFiles = git.listLocalFiles(folder);
        List<FileInfo> mainBranchFiles = git.loadMainBranchFiles(folder);

        List<FileInfo> branchFiles = new ArrayList<>();
        List<FileInfo> missingFiles = new ArrayList<>();
        FileInfo holdings = null;

        // PR Check
        if (git.prExists(folder))
            return ValidationResult.fail("PR exists for the folder" + folder);

        // Parse
        for (String file : rawFiles) {
            FileInfo info = parser.parse(file, folder);

            if (info == null)
                return ValidationResult.fail("Invalid file format: " + file);

            // existing file in main branch → ignore
            if (git.existsInMain(info, mainBranchFiles))
                continue;

            if (info.isHoldings()) {
                if (holdings != null)
                    return ValidationResult.fail("Multiple holdings.csv in " + folder);
                holdings = info;
                continue;
            }

            if (info.isMissing())
                missingFiles.add(info);
            else
                branchFiles.add(info);
        }

        // weekend rule
        if (holdings != null && Utils.isWeekend())
            return ValidationResult.fail("Weekend file upload in The: " + folder);

        // process missing first
        ValidationResult missingRes = missingProcessor.processMissing(missingFiles, branchFiles, mainBranchFiles);

        if (!missingRes.isValid())
            return missingRes;

        // process holdings next
        if (holdings != null) {
            ValidationResult holdRes = holdingsProcessor.processHoldings(
                    holdings, missingFiles, branchFiles, mainBranchFiles
            );
            if (!holdRes.isValid())
                return holdRes;
        }
        // APPLY RENAME (missing files + holdings)
        for (FileInfo f : missingFiles) {
            FileRenamer.rename(f);
        }

        if (holdings != null) {
            FileRenamer.rename(holdings);
        }        
        return ValidationResult.pass();
    }
}