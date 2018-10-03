package net.instant.console.security;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Properties;
import net.instant.util.PasswordHasher;

public class PasswordHashFile {

    private final URL url;
    private final PasswordHasher hasher;
    private Properties data;

    public PasswordHashFile(URL url, PasswordHasher hasher) {
        this.url = url;
        this.hasher = hasher;
        this.data = null;
        checkPresence();
    }
    public PasswordHashFile(File source, PasswordHasher hasher) {
        this(convertURL(source), hasher);
    }
    public PasswordHashFile(URL url) {
        this(url, PasswordHasher.getInstance());
    }
    public PasswordHashFile(File source) {
        this(convertURL(source));
    }

    public URL getURL() {
        return url;
    }

    public synchronized void load(boolean force) throws IOException {
        if (! force && data != null) return;
        Properties props = new Properties();
        InputStream stream = url.openStream();
        try {
            props.load(stream);
        } finally {
            stream.close();
        }
        data = props;
    }

    public void load() throws IOException {
        load(true);
    }

    public synchronized String query(String username) {
        if (data == null)
            throw new IllegalStateException("Querying PasswordHashFile " +
                "without having load()ed it");
        return data.getProperty(username);
    }

    public boolean verify(String username, char[] password) {
        if (hasher == null)
            throw new IllegalStateException("Verifying password without " +
                "password hasher");
        String hash = query(username);
        return (hash != null && hasher.verify(password, hash));
    }

    protected void checkPresence() {
        if (! "file".equals(url.getProtocol())) return;
        File f;
        try {
            f = new File(url.toURI());
        } catch (URISyntaxException exc) {
            // If the URL is really invalid, we will notice it when attempting
            // to retrieve the password file.
            return;
        }
        if (! f.exists())
            throw new IllegalStateException("Password file does not exist");
    }

    private static URL convertURL(File source) {
        try {
            return source.toURI().toURL();
        } catch (MalformedURLException exc) {
            throw new RuntimeException(exc);
        }
    }

}
