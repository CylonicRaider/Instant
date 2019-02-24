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

    public static void amend(File path, String key, String value)
            throws IOException {
        /* Read input */
        JarFile source = new JarFile(path);
        /* Apply changes */
        Manifest mf = source.getManifest();
        if (mf == null) mf = new Manifest();
        mf.getMainAttributes().put(new Attributes.Name(key), value);
        /* Locate output file */
        File newFile = new File(path + ".new");
        for (int i = 1; newFile.exists(); i++) {
            newFile = new File(path + ".new." + i);
        }
        /* Write output */
        JarOutputStream output = new JarOutputStream(
            new FileOutputStream(newFile), mf);
        Enumeration<JarEntry> entries = source.entries();
        while (entries.hasMoreElements()) {
            JarEntry ent = entries.nextElement();
            if (ent.getName().equals("META-INF/MANIFEST.MF")) continue;
            output.putNextEntry(ent);
            pump(source.getInputStream(ent), output);
            output.closeEntry();
        }
        output.close();
        /* Replace original file */
        if (! newFile.renameTo(path))
            throw new IOException("Could not replace original file");
    }

    public static void main(String[] args) {
        /* Parse command line */
        if (args.length != 3) {
            System.err.println("USAGE: AmendManifest filename key value");
            System.exit(1);
        }
        String filename = args[0], key = args[1], value = args[2];
        try {
            amend(new File(filename), key, value);
        } catch (IOException exc) {
            System.err.println(exc);
            System.exit(2);
        }
    }

}
