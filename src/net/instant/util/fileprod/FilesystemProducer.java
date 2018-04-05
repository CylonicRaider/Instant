package net.instant.util.fileprod;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class FilesystemProducer implements Producer {

    public static class FilesystemFileCell extends FileCell {

        private final File path;

        public FilesystemFileCell(String name, File path)
                throws IOException {
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

    private File chroot;
    private File workdir;

    public FilesystemProducer(File chroot, File workdir) {
        this.chroot = chroot;
        this.workdir = workdir;
    }
    public FilesystemProducer() {
        this(new File("/"), new File(""));
    }

    public File getChroot() {
        return chroot;
    }
    public void setChroot(File f) {
        chroot = f;
    }

    public File getWorkdir() {
        return workdir;
    }
    public void setWorkdir(File f) {
        workdir = f;
    }

    public File convert(String name) {
        String adjustedPath = new File(workdir, name).toString();
        // HACK: Force adjustedPath to be relative.
        while (adjustedPath.startsWith("/") ||
               adjustedPath.startsWith(File.separator))
            adjustedPath = adjustedPath.substring(1);
        return new File(chroot, adjustedPath);
    }

    public ProducerJob produce(String name) {
        final File path = convert(name);
        if (! path.isFile()) return null;
        return new ProducerJob(name) {
            protected FileCell produce() throws IOException {
                return new FilesystemFileCell(getName(), path);
            }
        };
    }

}
