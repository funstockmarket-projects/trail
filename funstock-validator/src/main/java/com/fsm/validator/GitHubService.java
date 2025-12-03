import java.io.File;
import java.util.ArrayList;
import java.util.List;

import models.FileInfo;

public class GitHubService {

    public List<String> listLocalFiles(String folder) {
        File dir = new File(folder);
        String[] files = dir.list((d, name) -> name.endsWith(".csv"));
        return files == null ? new ArrayList<>() : List.of(files);
    }

    public List<FileInfo> loadMainBranchFiles(String folder) {
        // To implement using GitHub API
        return new ArrayList<>();
    }

    public boolean prExists(String folder) {
        return false; // stub for now
    }

    public boolean existsInMain(FileInfo f, List<FileInfo> main) {
        return main.stream().anyMatch(x -> x.getOriginalName().equals(f.getOriginalName()));
    }
}