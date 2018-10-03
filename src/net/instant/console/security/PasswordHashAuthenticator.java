package net.instant.console.security;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import javax.management.remote.JMXAuthenticator;
import javax.management.remote.JMXPrincipal;
import javax.security.auth.Subject;

public class PasswordHashAuthenticator implements JMXAuthenticator {

    private final PasswordHashFile database;

    public PasswordHashAuthenticator(PasswordHashFile database)
            throws IOException {
        this.database = database;
        database.load();
    }
    public PasswordHashAuthenticator(URL source) throws IOException {
        this(new PasswordHashFile(source));
    }
    public PasswordHashAuthenticator(File source) throws IOException {
        this(new PasswordHashFile(source));
    }

    public Subject authenticate(Object credentials) {
        if (! (credentials instanceof String[]))
            throw new SecurityException("JMX credentials must be a String " +
                "array");
        String[] credArray = (String[]) credentials;
        if (credArray.length != 2 || credArray[0] == null ||
                credArray[1] == null)
            throw new SecurityException("JMX credentials must be an array " +
                "of two non-null strings");
        try {
            database.load();
            // So much about password erasing.
            if (! database.verify(credArray[0], credArray[1].toCharArray()))
                throw new SecurityException("Incorrect username or password");
        } catch (IOException exc) {
            throw new SecurityException("Could not reload password database",
                exc);
        }
        Subject ret = new Subject();
        ret.getPrincipals().add(new JMXPrincipal(credArray[0]));
        return ret;
    }

}
