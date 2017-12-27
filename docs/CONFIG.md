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
  converted to upper case and dots are replaced with underscores.

## Configuration values

Configuration values should be enclosed in namespaces using dots (`.`). The
values used by Instant are in the `instant` namespace in order to avoid
clashes with (other) environment variables or system properties.

Boolean values are considered true if their value is (case-insensitively) one
of `true`, `yes`, `y`, `1`; all other values (in particular `false`, `no`, `n`,
`0`) count as false (see `Utilities.isTrue` for the current implementation).

### instant.cookies.insecure

Boolean indicating whether to *not* set the `Secure` attribute on cookies set
by the backend. May be used for debugging, but **should not** be used in
production.

### instant.cookies.keyfile

The path of a file where to store the cookie signing key. If this becomes
compromised, the file must be regenerated (and old cookies are necessarily
lost). If no path is specified, a random key is created for the lifetime of
the backend.

### instant.cookies.keyfile.create

Boolean indicating whether to create the cookie key file if it is absent or
invalid. The Instant process must have appropriate privileges for this to
work (otherwise, a fatal error may occur).

### instant.http.maxCacheAge

Integer setting the `max-age` parameter of the `Cache-Control` HTTP header
for static resources, i.e., controlling for how long (compliant) browsers
will cache them.
