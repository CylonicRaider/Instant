package net.instant.util.fileprod;

import java.io.File;
import java.io.IOException;

public class FilesystemProducer implements Producer {

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
        return new File(chroot, adjustedPath.replaceFirst("^[/\\\\]+", ""));
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
