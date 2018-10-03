package net.instant.console.security;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.logging.Logger;
import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;
import net.instant.api.Utilities;
import net.instant.util.PasswordHasher;

/* FIXME: In order to become a full login module, this should implement the
 *        username/password sharing mechanism, use more generic password
 *        hashing, interoperate with AccessController, and be distributed
 *        as a proper service provider. */
public class PasswordHashLoginModule implements LoginModule {

    private static final Logger LOGGER = Logger.getLogger("PHLoginModule");

    private Subject subject;
    private CallbackHandler callbacks;
    private PasswordHashFile database;

    private String authName;
    private PasswordHashPrincipal principal;

    public void initialize(Subject subject, CallbackHandler callbacks,
            Map<String, ?> sharedState, Map<String, ?> options) {
        this.subject = subject;
        this.callbacks = callbacks;
        for (Map.Entry<String, ?> opt : options.entrySet()) {
            if (opt.getKey().equals("file")) {
                Object v = opt.getValue();
                if (v instanceof String) {
                    URL url;
                    try {
                        url = stringToURL((String) opt.getValue());
                    } catch (MalformedURLException exc) {
                        throw new IllegalArgumentException("Invalid " +
                            "password database URL", exc);
                    }
                    database = new PasswordHashFile(url);
                } else if (v instanceof URL) {
                    database = new PasswordHashFile((URL) v);
                } else if (v instanceof PasswordHashFile) {
                    database = (PasswordHashFile) v;
                } else {
                    throw new ClassCastException("Unrecognized type of " +
                        "\"file\" option value");
                }
            } else {
                LOGGER.warning("Unrecognized option: " + opt.getKey() +
                    " = " + opt.getValue());
            }
        }
        if (database == null)
            throw new IllegalArgumentException("Password database location " +
                "not specified");
        authName = null;
        principal = null;
    }

    public boolean login() throws LoginException {
        if (database == null)
            throw new LoginException("No password database present");
        /* Query data from user */
        NameCallback nameCB = new NameCallback("Username: ");
        PasswordCallback pwCB = new PasswordCallback("Password: ", false);
        try {
            callbacks.handle(new Callback[] { nameCB, pwCB });
        } catch (IOException exc) {
            throw withCause(new LoginException("Could not read credentials"),
                            exc);
        } catch (UnsupportedCallbackException exc) {
            throw withCause(new LoginException("Could not read credentials"),
                            exc);
        }
        /* Verify data */
        String name;
        char[] pwCopy = null;
        try {
            database.load();
            name = nameCB.getName();
            if (name == null)
                throw new NullPointerException("Username is null?!");
            pwCopy = pwCB.getPassword();
            if (pwCopy == null)
                throw new NullPointerException("Password is null?!");
            if (! database.verify(name, pwCopy))
                throw new FailedLoginException("Username or password " +
                    "incorrect");
        } catch (IOException exc) {
            throw withCause(new LoginException("Could not read password " +
                "database"), exc);
        } finally {
            PasswordHasher.clear(pwCopy);
            pwCB.clearPassword();
        }
        /* Save state */
        authName = name;
        return true;
    }

    public boolean commit() throws LoginException {
        if (authName == null)
            return false;
        PasswordHashPrincipal toAdd = new PasswordHashPrincipal(authName);
        if (subject.getPrincipals().add(toAdd))
            principal = toAdd;
        return true;
    }

    public boolean abort() throws LoginException {
        return clearState();
    }

    public boolean logout() throws LoginException {
        clearState();
        return true;
    }

    private boolean clearState() throws LoginException {
        boolean ret = (authName != null);
        authName = null;
        if (principal != null) {
            try {
                subject.getPrincipals().remove(principal);
            } catch (IllegalStateException exc) {
                throw new LoginException("Could  remove principal");
            } finally {
                principal = null;
            }
        }
        return ret;
    }

    private static <T extends Throwable> T withCause(T exc, Throwable cause) {
        exc.initCause(cause);
        return exc;
    }

    private static URL stringToURL(String s) throws MalformedURLException {
        return Utilities.makeURL(s);
    }

}
