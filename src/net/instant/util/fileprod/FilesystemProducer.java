package net.instant.util.fileprod;

import java.io.File;
import java.io.IOException;

public class FilesystemProducer extends WhitelistProducer {

    private File chroot;
    private File workdir;

    public FilesystemProducer(File chroot, File workdir) {
        this.chroot = chroot;
        this.workdir = workdir;
    }
    public FilesystemProducer(String chroot, String workdir) {
        this(new File(chroot), new File(workdir));
    }
    public FilesystemProducer() {
        this("/", "");
    }

    public File getChroot() {
        return chroot;
    }
    public File getWorkdir() {
        return workdir;
    }

    public void setChroot(File f) {
        chroot = f;
    }
    public void setWorkdir(File f) {
        workdir = f;
    }

    public File convert(String name) {
        String adjustedPath = new File(workdir, name).toString();
        return new File(chroot, adjustedPath.replaceFirst("^[/\\\\]+", ""));
    }

    public ProducerJob produceInner(String name) {
        final File path = convert(name);
        if (! path.isFile()) return null;
        return new ProducerJob(name) {
            protected FileCell produce() throws IOException {
                return new FilesystemFileCell(getName(), path);
            }
        };
    }

}
