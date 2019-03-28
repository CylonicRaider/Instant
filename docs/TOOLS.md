# Instant auxiliary tools

This document describes the Java helper programs shipped along with Instant.

Each tool's source code is located in a directory in the `tools/` subdirectory
of the source tree, with a pre-compiled `.jar` file of the code being located
in `tools/` as well (so, a tool `X` has its source code in `tools/X` and a
pre-compiled archive at `tools/X.jar`).

## amend-manifest

    amend-manifest filename key value [outfile]

This tool takes a JAR file at `filename`, adds a `key`-`value` pair to the
main section of its manifest, and writes the result to `outfile`. The latter
may be omitted (or the same as `filename`) for the tool to operate in-place.

## console-client

    console-client [--help|-?] [--interactive|-I] [--batch|-B] [--gui|-G]
                   [--login|-l USERNAME] [<address>]

This tool provides a counterpart to Instant's
[backend console](CONFIG.md#instantconsoleaddr).

The `--interactive`, `--batch`, and `--gui` flags are used to select the
tool's mode of operation (see below). `--login`, if given, indicates that the
tool should authenticate itself to the console; the user name is provided as
an argument after the option while the password is read interactively.
`address` is the address (a `HOST:PORT` pair or a JMX service URL) to connect
to; this argument is mandatory in batch mode.

The tool provides three modes of operation:

- **Interactive CLI mode** (`--interactive`): The tool prompts the user for
  commands (or credentials) and displays the console's output. If `address`
  is not given, the tool prompts for the address to connect to. If `--login`
  is not given, an attempt is made to connect without authentication; if that
  fails, the user is asked for credentials and a second (and final) connection
  attempt is made.
- **Batch CLI mode** (`--batch`): The tool processes input deterministically:
  The `address` command-line argument is connected to; if-and-only-if
  `--login` is given, the first line of input is interpreted as a password;
  all other input lines are interpreted as commands. There is exactly one
  attempt to connect; whether to use authentication is decided based on
  whether `--login` is provided.
- **GUI mode** (`--gui`): A graphical interface for connecting to the console
  and interacting with it is shown. `address` and `--login` pre-fill fields
  of the connection form.

The CLI modes communicate via the terminal they are invoked at, or, failing
that, the standard input/output streams; (only) when using batch mode without
a TTY, no prompts are written. If it is explicitly specified, the GUI mode
requires, self-evidently, an appropriate graphical environment to be present
(and accessible by the tool).

The default mode depends on whether the machine is deemed headless (i.e.
lacking a graphical environment suitable for the GUI; interactive CLI mode is
used on headless systems, GUI mode elsewhere). Defaulting to a GUI when
started with no arguments allows the tool to be invoked from, e.g., a
graphical file manager.
