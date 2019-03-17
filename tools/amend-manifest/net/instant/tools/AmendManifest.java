package net.instant.tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class AmendManifest {

    private static final int BUFFER_SIZE = 65536;

    private static void pump(InputStream in, OutputStream out)
            throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        for (;;) {
            int len = in.read(buffer);
            if (len < 0) break;
            out.write(buffer, 0, len);
        }
    }

    public static File chooseTempPath(File path) {
        File ret = new File(path + ".new");
        for (int i = 1; ret.exists(); i++) {
            ret = new File(path + ".new." + i);
        }
        return ret;
    }

    public static void amend(File sourcePath, String key, String value,
                             File drainPath) throws IOException {
        /* Read input */
        JarFile source = new JarFile(sourcePath);
        /* Apply changes */
        Manifest mf = source.getManifest();
        if (mf == null) mf = new Manifest();
        mf.getMainAttributes().put(new Attributes.Name(key), value);
        /* Choose output file */
        boolean inPlace = sourcePath.equals(drainPath);
        if (inPlace) drainPath = chooseTempPath(sourcePath);
        /* Write output */
        JarOutputStream output = new JarOutputStream(
            new FileOutputStream(drainPath), mf);
        Enumeration<JarEntry> entries = source.entries();
        while (entries.hasMoreElements()) {
            JarEntry ent = entries.nextElement();
            if (ent.getName().equals("META-INF/MANIFEST.MF")) continue;
            output.putNextEntry(ent);
            pump(source.getInputStream(ent), output);
            output.closeEntry();
        }
        output.close();
        /* Replace original file (if necessary) */
        if (inPlace && ! drainPath.renameTo(sourcePath))
            throw new IOException("Could not replace original file");
    }

    public static void main(String[] args) {
        /* Parse command line */
        if (args.length < 3 || args.length > 4) {
            System.err.println("USAGE: amend-manifest filename key value " +
                "[outfile]");
            System.exit(1);
        }
        String filename = args[0], key = args[1], value = args[2];
        String outFilename = (args.length == 3) ? filename : args[3];
        try {

            amend(new File(filename), key, value, new File(outFilename));
        } catch (IOException exc) {
            System.err.println(exc);
            System.exit(2);
        }
    }

}
