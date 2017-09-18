package net.instant.util.fileprod;

import java.io.File;

public class FSResourceProducer extends AbstractWhitelistProducer {

    private FilesystemProducer fs;
    private ResourceProducer rs;

    public FSResourceProducer(File chroot, File workdir, ClassLoader cl) {
        fs = new FilesystemProducer(chroot, workdir);
        rs = new ResourceProducer(cl);
    }
    public FSResourceProducer() {
        fs = null;
        rs = null;
    }

    public FilesystemProducer getFSProducer() {
        return fs;
    }
    public void setFSProducer(FilesystemProducer p) {
        fs = p;
    }

    public ResourceProducer getResourceProducer() {
        return rs;
    }
    public void setResourceProducer(ResourceProducer p) {
        rs = p;
    }

    protected ProducerJob produceInner(String name) {
        ProducerJob ret = null;
        if (fs != null) ret = fs.produce(name);
        if (rs != null && ret != null) ret = rs.produce(name);
        return ret;
    }

}
