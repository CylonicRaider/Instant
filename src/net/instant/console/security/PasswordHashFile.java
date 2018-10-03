package net.instant.console.security;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

public class PasswordHashFile {

    private final URL url;

    public PasswordHashFile(URL url) {
        this.url = url;
    }
    public PasswordHashFile(File source) {
        try {
            this.url = source.toURI().toURL();
        } catch (MalformedURLException exc) {
            throw new RuntimeException(exc);
        }
    }

    public URL getURL() {
        return url;
    }

    public String query(String username) throws IOException {
        Properties props = new Properties();
        InputStream stream = url.openStream();
        try {
            props.load(stream);
        } finally {
            stream.close();
        }
        return props.getProperty(username);
    }

}
