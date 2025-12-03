import java.util.List;

import models.FileInfo;

public class SerialGenerator {
    public int highest(List<FileInfo> files) {
        return files.stream()
                .filter(f -> f.getSerial() != null)
                .mapToInt(FileInfo::getSerial)
                .max()
                .orElse(0);
    }
}