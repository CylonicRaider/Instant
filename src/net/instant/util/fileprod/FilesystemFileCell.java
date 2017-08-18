package net.instant.util.fileprod;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class FilesystemFileCell extends FileCell {

    public File path;

    public FilesystemFileCell(String name, File path) throws IOException {
        super(name, new FileInputStream(path), path.lastModified());
        this.path = path;
    }

    public File getPath() {
        return path;
    }

    public boolean isValid() {
        /* BUG: Assumes that no file was last modified at the Epoch */
        long modified = getPath().lastModified();
        return (modified != 0 && modified <= getCreated());
    }

}
