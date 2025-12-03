import java.io.File;

import models.FileInfo;

public class FileRenamer {

    public static boolean rename(FileInfo file) {

        String folder = file.getFolder();
        String oldPath = folder + "/" + file.getOriginalName();
        String newPath = folder + "/" + file.getFinalName();

        File oldFile = new File(oldPath);
        File newFile = new File(newPath);

        // safety: avoid overwriting
        if (newFile.exists()) {
            System.out.println("❌ Cannot rename: " + newPath + " already exists!");
            return false;
        }

        boolean ok = oldFile.renameTo(newFile);

        if (!ok) {
            System.out.println("❌ Rename failed: " + oldPath + " → " + newPath);
        } else {
            System.out.println("✔ Renamed: " + oldFile.getName() + " → " + newFile.getName());
        }

        return ok;
    }
}