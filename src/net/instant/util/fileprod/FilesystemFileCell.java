package net.instant.util.fileprod;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import net.instant.util.Util;

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
        return (getPath().lastModified() <= getCreated());
    }

}
