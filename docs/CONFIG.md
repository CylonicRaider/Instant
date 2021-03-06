# Instant configuration values

This document describes the configuration values available for Instant and
how to set them.

## Setting configuration values

When looking for a configuration value, Instant checks the following sources
(returning the first result available):

- Values specified on the command line via the `--option` (a.k.a. `-o`)
  option.
- Values specified via Java system properties. The name and value of the
  property map directly to the configuration value's name and value.
- Values specified via environment variables. In this mode, the name is
  converted to upper case and dots are replaced with underscores before
  retrieval.
- Values specified in a configuration file (which itself is specified using
  the `--config` (a.k.a. `-C`) command-line option).
- Values specified in plugin manifests, in attribute sets for
  `Instant-Configuration`.

## Configuration values

Configuration values should be enclosed in namespaces using dots (`.`). The
values used by Instant are in the `instant` namespace in order to avoid
clashes with (other) environment variables or system properties.

Boolean values are considered true if their value is (case-insensitively) one
of `true`, `yes`, `y`, `1`; all other values (in particular `false`, `no`,
`n`, `0`) count as false (see `net.instant.api.Utilities.isTrue` for the
current implementation).

### instant.console.addr

A `HOST:PORT` string indicating that the backend management console should be
exported on the given address. Standard JMX client applications (such as
`jconsole`) can connect to this address in order to perform management
operations on the VM the backend runs in and to access the backend console.

If you do not specify a password file via `instant.console.pwfile`, this will
allow full access to the management interface to **anyone** who can connect to
it.

This uses unencrypted connections; to enable SSL, employ the VM's native
management capabilities instead (and set `instant.console.enabled` if
desired).

**Note**: Communication with the console is based on the RMI technology; in
particular, the `HOST` and `PORT` as specified above are sent to clients,
which then *attempt to connect there directly*. This may cause problems if
`HOST` or `PORT` do not mean the same thing to the backend as they do to the
client; if communication is to happen via proxies or tunnels, special care
needs to be taken.

### instant.console.enabled

A Boolean indicating whether the backend console should be actually enabled.
If this configuration value is not set, the console is enabled if-and-only-if
`instant.console.addr` is set.

### instant.console.pwfile

A string indicating the path (or, if it contains a colon, URL) of a Java
properties file storing pairs of usernames and hashed passwords for access to
the backend management interface, if the latter is enabled via
`instant.console.addr`. If `instant.console.addr` is set and this
configuration value is not, **anyone** can access the management interface, as
long as they can connect to its address.

In order to generate an appropriate password hash, run

    java -cp Instant.jar net.instant.util.PasswordHasher

(where a stock `Instant.jar` is provided in the Git repository); it will read
a password from standard input and output its hash on standard output. To add
a new user/password pair to the password file, add a line with the following
contents to it:

    USERNAME = HASH

(where `USERNAME` is the name of the user and `HASH` is the hash of the user's
password as computed above).

### instant.cookies.insecure

A Boolean indicating whether to *not* set the `Secure` attribute on cookies
set by the backend. May be used for debugging, but **should not** be used in
production.

### instant.cookies.keyfile

The path of a file where to store the cookie signing key. If this becomes
compromised, the file must be regenerated (and old cookies are necessarily
lost). If no path is specified, a random key is created for the lifetime of
the backend.

### instant.cookies.keyfile.create

A Boolean indicating whether to create the cookie key file if it is absent or
invalid. The Instant process must have appropriate privileges for this to
work (otherwise, a fatal error may occur).

### instant.http.maxCacheAge

An integer setting the `max-age` parameter of the `Cache-Control` HTTP header
for static resources, i.e., controlling for how long (compliant) browsers
will cache them.

### instant.server.noReuseAddr

A Boolean indicating whether the server should *disable* the `SO_REUSEADDR`
option on its main listening socket. The default is to enable `SO_REUSEADDR`,
allowing the server to be restarted quickly without getting “address in use”
errors.
