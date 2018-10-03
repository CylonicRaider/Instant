package net.instant.console.security;

import java.security.Principal;

public class PasswordHashPrincipal implements Principal {

    private final String name;

    public PasswordHashPrincipal(String name) {
        if (name == null)
            throw new NullPointerException("PasswordHashPrincipal name may " +
                "not be null");
        this.name = name;
    }

    public String toString() {
        return "PasswordHashPrincipal: " + getName();
    }

    public boolean equals(Object other) {
        return ((other instanceof PasswordHashPrincipal) &&
            name.equals(((PasswordHashPrincipal) other).name));
    }

    public int hashCode() {
        // instant console security password hash principal
        return name.hashCode() ^ 0x1C5242;
    }

    public String getName() {
        return name;
    }

}
