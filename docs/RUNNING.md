# Running Instant

This document describes how to configure, and run the Instant backend and/or
the log storage bot shipped alongside it.

See also [`BUILDING.md`](BUILDING.md) for instructions on how to build the
backend.

## Running the backend

A properly working installation of a *Java Runtime Environment* (or Java
Development Kit; version 7 of either at least) is assumed.

For running an own instance, you can use the pre-built
[`Instant.jar`](../Instant.jar) file in this repository, which should be
always up-to-date with the latest backend and frontend (unless someone messed
it up).

The backend accepts the following command-line arguments (the `--help` option
can be used to report a summary and single-letter aliases):

- `--host` *host* — *Hostname to bind to*: Can be used on machines with
  multiple network interfaces; `localhost` can be used to accept local
  connections only. Defaults to `*` (the asterisk), which is translated
  to *all interfaces*.

- `--webroot` *path* — *Location of static files*: Location of a directory
  where to look for the `pages/` and `static/` subdirectories for static
  files. If a static file is absent, Instant falls back to a version bundled
  with the backend. Defaults to `.` (the current directory).

- `--tls` *params* — *TLS configuration*: A comma-separated list of
  `<KEY>=<VALUE>` pairs providing TLS configuration. HTTPS is served on the
  port given below if-and-only-if this option is passed. See
  [the *HTTPS* section](#https) for details.

- `--http-log` *file* — *HTTP log location*: Either a file name where to
  append the HTTP log to, or the `-` character to write to standard error.
  Output is entry-buffered (i.e. flushed after every entry). Defaults to `-`.

- `--debug-log` *file* — *Debugging log location*: Similarly to `--http-log`,
  this is either a file name or a `-`. This is used for logging anything that
  does not belong into the HTTP log. The default is, again, `-`.

- `--log-level` *str* — *Logging level*: The string representation of a level
  as recognized by `java.util.loogging`; used for the debugging log. Defaults
  to `INFO`.

- `--plugin-path` *paths* — *Plugin path*: A list of directory or file paths
  where to search for plugins. Separated by the platform-specific path list
  separator (i.e. `:` on UNIX-like OS-es, and `;` on Windows).

- `--plugins` *list* — *List of plugins to load*: A comma-separated list of
  plugin names (or paths) to be fetched (from the aforementioned path) and
  linked into the backend on startup. Dependencies are fetched automatically.
  Plugin names including commata cannot be specified (why should you name one
  like that?). To be interpreted as a path, an entry must contain a slash
  (`/`) character (regardless of platform).

- `--startup-cmd` *str* — *Command to run at startup*: A file to be executed
  with certain parameters just before the backend attempts to listen for
  requests. The command is *not* interpreted by the shell; rather, it is
  split on spaces and then executed as-is (in particular, neither the program
  path nor the arguments may contain spaces themselves). The backend waits
  for the command to finish before it proceeds. Can be used to reduce
  downtime during updates by starting a new backend in the background and
  letting it shut down the “old” one before taking over.

- `--option` *key*=*value* — *Configuration value*: Explicitly sets a
  configuration value (overriding environment variables and system
  properties). See [`CONFIG.md`](CONFIG.md) for details.

- `--config` *file* — *Configuration file*: A Java properties file holding
  additional configuration values. See [`CONFIG.md`](CONFIG.md) for details.

- *port* — *Port to bind to*: As a single optional positional argument, this
  specifies the TCP port to listen on. Defaults to `8080`.

**TL;DR**: To run the stock backend on port 8080, run `java -jar
Instant.jar`, and point your browser to
[localhost:8080](http://localhost:8080).

### External dependencies

The sole “external” dependency of the backend is the Java standard library
(along with an underlying Java Virtual Machine).

See [the *Dependencies* section of `BUILDING.md`](BUILDING.md#dependencies)
for a note on older backends.

### HTTPS

The backend can be configured to serve plain HTTP or HTTPS. In either case,
it is strongly recommended to locate the backend behind a reverse proxy.

TLS configuration is provided in a uniform manner for both the backend and the
Scribe bot (whose interface is documented [below](#running-scribe)). It is
encoded as comma-separated `<KEY>=<VALUE>` lists, with the following keys
defined:

- `cert`=*file*: The certificate to present to the other side, as the path of
  a text file containing PEM-encoded data. If any intermediate certificates
  are required, those must be located inside the file (after the end-entity
  certificate). Required for the backend, optional for Scribe.
- `key`=*file*: The private key corresponding to `cert`, as the path of a text
  file containing PEM-encoded data. If `cert` is provided but this is not, the
  private key is assumed to be located inside the certificate file.
- `ca`=*file*: A set of certification authority certificates to exclusively
  trust, as the path of a text file containing PEM-encoded data. If this is
  omitted, no client certificates are required (for the backend), or a default
  set of CA-s is used (for Scribe).

## Orchestrator script

On UNIX platforms, a powerful (and extremely overengineered) Python program
for automating the management of an Instant backend and any amount of bots
(and any other processes as well) is available at `script/run.py`.

`run.py` aims to be usable as an init script; however, it includes some
relative file paths, which might require setting a particular working
directory (or patching the initial sections of the script).

On every invocation, a subcommand must be specified that defines what action
should be performed. A full listing can be obtained by invoking the script
with a `--help` option.

- `status`: Lists processes and displays whether they are running or not.
- `start`: Launches those processes that are not running.
- `stop`: Terminates those processes that are running.
- `restart`: Terminates and re-starts processes.
- `bg-restart`: Performs a “fast restart”: In the background, new instances
  of those processes that support it are pre-loaded, then the “old” instances
  of (all named) processes are stopped, and finally the background processes
  are swapped in (or new instances are started). Requires a master process
  (see below) to be effective; falls back to an equivalent of `restart`
  otherwise.

Each subcommand listed here accepts a list of process names (as defined in the
configuration file; see below) to act upon as positional arguments. The
subcommands produce *status reports* for each of the processes affected; these
are accumulated and displayed in the order given on the command line (or the
configuration file, if no processes are named on the command line) as default;
to display them as they arrive, pass a `--sort=no` option to the subcommands;
to disable the status reports entirely, pass a `--verbose=no`.

**TL;DR**: Skip to the [example subsection](#example-configuration) below,
copy the configuration file to `config/run.ini` in the Instant directory,
modify it to your needs, and use `script/run.py COMMAND` for `COMMAND`-s
listed just above.

### Master process mode

In this mode of operation, a central background instance of the orchestrator
script — the *master process* — performs the actual process management and the
actions are submitted to it via IPC.

Aside from running a master process in the foreground using the `run-master`
subcommand, the actions listed above can be instructed to interact with a
master process in various ways using the `--master` command-line switch (which
must be specified before the subcommand); its possible values are listed
below:

- `never` — *Never use master*. This may interfere with an already-running
  master process, but may be useful for recovery nonetheless. All of the
  following options use an already-master process if available.
- `auto` — *Use master whenever available*: If no master process is running,
  this performs the action locally.
- `spawn` — *Spawn master in background*: If no master process is running,
  this spawns one and submits the action to it. Useful together with `start`,
  `restart`, `bg-restart`.
- `fg` — *Spawn master in foreground*: If no master process is running, the
  local process assumes its role and performs the action. The process keeps
  running until shut down explicitly. Useful together with `start` _etc._ when
  running under `systemd`.
- `always` — *Always use master*: If no master process is running, this exits
  with an error message.
- `restart` — *Restart master*: If no master process is running, spawns a new
  one in the background; if there *is* one, it is restarted in-place. In
  either case, the configuration file is (re-)read and the action is performed
  by a master process. Useful together with `restart` or `bg-restart` for
  performing live updates.
- `stop` — *Shut down master*: If no master process is running, executes the
  action locally; if there *is* a master process, it executes the action and
  is shut down afterwards.
- `action` — *Action-dependent selection*: Use one of the modes above
  depending on the chosen action: The `start` action uses the `spawn` mode,
  `restart` and `bg-restart` use `restart`, `stop` uses `stop`, all others use
  `auto`.

The existence and responsiveness of a master process can be queried using the
`ping` action, which takes no further arguments and produces a report similar
to those of the `status` action.

### Configuration file format

`run.py` employs a powerful configuration mechanism in order to offset its
very generic nature. The configuration file format is introduced below.

**Basic structure**: Configuration files are based upon the well-known `.ini`
format as implemented by Python's `configparser` module. Files contain
`key=value` *pairs* (each on an own line) grouped into *sections* introduced
by section names enclosed in square brackets (on own lines as well), like
`[section]`; comments may be introduced by semicolons (`;`). Section names are
restricted to not have leading forward slashes (`/`) and not to contain double
forward slashes (`//`). Keys starting and ending with double underscores
(`__`) are reserved; do not use them unless indicated otherwise.

**Template sections**: A section whose name ends with a slash is called a
*template section*. It is functionally equivalent to a non-template section,
but ignored when listing sections (which becomes relevant when enumerating the
sections defining processes to run).

**Inheritance**: A section *inherits* the values of its “parent” section (if
any), whose name is derived from the current section's name as follows: If the
section name ends with a slash (`/`), the parent section name is the section
name without that slash; otherwise, if the section name contains a slash, the
parent section name is the section name up to and including the last slash;
otherwise, there is no parent section. Pairs defined in the current section
take precedence over those of the parent section. Additionally, if the current
section defines a pair with a key of `__import__`, the pairs of the section
named by the value of the `__import__` pair (if any) are included in the
current section, taking precendence over the parent section but being
overridden by pairs defined directly in the current section. Inheritance and
importing are performed recursively: the values of a parent of a parent are
inherited as well (even if the parent is not present in the file).

**Interpolation**: *After* a section's pairs have been aggregated as described
above, they are *interpolated*: References to other pairs in the section's
pair set consisting of dollar signs (`$`) followed by pair keys enclosed in
curly braces (like `${key}`) are replaced by the corresponding pairs' values.
Only keys consisting of alphanumerics, dashes (`-`), and underscores (`_`) may
be referenced. The braces may be omitted if none of the mentioned characters
follow the reference. References are resolved recursively; circular references
are not allowed and (presently) crash the parser. Some *special names* are
defined (which override any other pairs and should consequently not be
defined in the file):

- `__name__`: The final component of the section name, i.e. everything after
  the last slash. In a template section, this is (consequently) the empty
  string.
- `__fullname__`: The full name of the section.

Consider the following example for illustration:

    ; This is a comment.

    [secA]
    ; This (trivially) resolves to "valueA".
    a=valueA
    ; This resolves to the empty string.
    y=${test1}
    ; This resolves to "secA".
    z=${__name__}

    [secA/]
    ; The following pairs are inherited from secA:
    ;a=valueA      ; Resolves to "valueA".
    ;y=${test1}    ; Resolves to the empty string in here.
    ;z=${__name__} ; Resolves to the empty string in here.

    [secA/subsecA]
    ; The following pairs are inherited from secA via secA/:
    ;a=valueA      ; Resolves to "valueA".
    ;y=${test1}    ; Resolves to "valueA valueB" in here.
    ;z=${__name__} ; Resolves to "subsecA" in here.
    ; This resolves (trivially as well) to "valueB".
    b=valueB
    ; This resolves to "valueA valueB".
    test1=${a} $b
    ; This resolves to "valueA valueBX".
    test2=${y}X
    ; This would be an error if it were not a comment.
    ;test3=${test3}
    ; This resolves to "I am in subsecA a.k.a. secA/subsecA.".
    test4=I am in ${z} a.k.a. ${__fullname__}.

### Configuration files

This subsection describes which values `run.py` extracts from its
configuration file and how it interprets them.

**Meta-configuration**: Some aspects of the configuration file's
interpretation can be customized by the configuration file itself via the
`[meta]` section. The following key is defined:

- `proc-prefixes` — *Process section prefixes*: A space-delimited list of
  strings that characterize sections which define processes (see below). For
  a section to define a process, its name must start with any of the strings
  given here. The built-in default is `proc`.

**Individual processes** are defined by non-template sections whose names
start with one of the *process section prefixes* (see above). The following
keys configure the process:

- `name` — *Process name*: The name of the process used on the command line.
  Must be unique and not contain whitespace or equals signs (`=`). Required.
- `cmdline` — *Command line*: Used to invoke the process. Parsed using
  shell-like syntax (as implemented by the `shlex` Python module); that
  happens **after** interpolation has been performed on the value. Required.
- `env` — *Environment variables*: The value is, similarly to `cmdline`, split
  into individual “words” using shell-like syntax; those are then split at
  the first occurrences of equals signs (`=`; each word must contain at least
  one) and added to a new process' environment. **Remark** that the syntax is
  more lax than an actual shell's: `"abc=def"` is valid inside `env`, but
  would not be a valid variable assignment in a shell.
- `work-dir` — *Working directory*: A path of a directory to invoke the
  process in. If the command name in `cmdline` is a relative path, it is
  interpreted relatively to this directory.
- `stdin` — *Standard input redirection*: If specified, standard input of the
  process is redirected according to this; see below for possible values.
- `stdout` — *Standard output redirection*: Like `stdin`, but for standard
  output.
- `stderr` — *Standard error redirection*: Like `stdin`, but for standard
  error.

The following keys configure the orchestrator's handling of the process:

- `mkdirs` — *Create directories*: A list — similarly to `env` — of
  directories to create before starting the process. Parent directories are
  created as necessary.
- `pid-file` — *PID file*: The path of a file to write the PID of the process
  to. Used in standalone mode to locate the process. A default value (which
  varies according to the process name) is provided.
- `pid-file-warmup` — *Secondary PID file*: During a “fast restart”, if
  supported by the process, the PID of its background copy is written here.
  Defaults to the value of `pid-file` with a `.new` suffix appended.
- `startup-notify` — *Fast restart support*: Setting this key enables “fast
  restart” support for this process. When performing a fast restart, the value
  of this key is appended to the process' command line (as a single argument),
  followed by a shell command (as a single argument which is internally
  delimited by spaces) to invoke (and wait for) when the process is done
  initializing. The command communicates with the master process (which must
  be present for fast restarts to be performed) and exits when it is time for
  the background copy of the process to be swap itself into the foreground.
- `stop-wait` — *Slow shutdown support*: When stopping the process and if the
  orchestrator cannot wait for the process to finish (e.g. when not using a
  master process), this specifies a fixed delay (in seconds; perhaps
  fractional) to be applied. This is useful when the process holds some
  exclusive resource (such as a bound network socket) and might take some time
  to release it.
- `restart-delay` — *Automatic restarting*: Setting this enables automatic
  restarting of the process by a master process (which is, again, required)
  should the process exit with a non-zero status code. The value is a (perhaps
  fractional) amount of seconds to wait between the old copy's death and
  starting the new copy.
- `restart-min-alive` — *Automatic restart eligibility*: In order to be
  eligible for an automated restart, the process must exit with a nonzero
  code, and have been running for the amount of time specifieed by this key
  (in perhaps fractional seconds), which defaults to zero. This is useful to
  prevent a process that crashes during start-up from being restarted
  indefinitely.

**Redirections** are specified in a way similar to shell redirections, with
some special values:

- *(empty string)*: No redirection; the process inherits the corresponding
  file descriptor of its parent.
- `devnull`: Opens `/dev/null` for reading/writing.
- `stdout`: (Only valid when redirecting `stderr`.) Make standard error point
  whereever standard output goes.
- `<PATH`: Open `PATH` for reading.
- `>PATH`: Open `PATH` for writing (creating a new file or truncating an
  existing file).
- `>>PATH`: Open `PATH` for appending (creating a new file if necessary).
- `<>PATH`: Open `PATH` for reading and writing.
- All other values result in errors.

**The master process** and some miscellanea are configured using the `master`
section, in which the following keys are significant:

- `work-dir` — *Orchestrator working directory*: If present, changes
  `run.py`'s working directory to the given path, which is interpreted
  relatively to the directory containing the configuration file. If not
  present, the working directory in place when `run.py` is invoked is used.
- `mode` — *Master process spawning mode*: This provides a default value for
  the `--master` option for invocations of the orchestrator.
- `comm-path` — *Socket path*: Where the UNIX domain socket used for
  communication with the master process is located.
- `comm-mkdirs` — *Create intermediate directories for socket*: Whether
  intermediate directories in `comm-path` should be created. Defaults to yes.
- `pid-file` — *Master PID file*: Where the master process should write a PID
  file representing itself. If omitted, the master process writes no PID file.
- `log-file` — *Path of logging file*: Where the master process should send
  logs. If not given or empty, standard error is used.
- `log-level` — *Logging level*: Messages at least as severe as this level
  will be logged. Defaults to `INFO`.
- `log-timestamps` — *Timestamps in logs*: Whether logging messages should
  include timestamps. Defaults to `yes`. Disabling this might be useful when
  logging to `systemd`.

**Note** that all filesystem paths (with the exception of those in
`cmdline`-s) are relative to the *orchestrator*'s working directory; see the
`work-dir` key in the `master` section for configuring that. For PID files,
redirections, and the master process' log file, intermediate directories are
created as needed.

### Example configuration

The following configuration file demonstrates the techniques documented above
and provides an approximate replacement for the `run.bash` script:

    ; General parameters stored in a separate section; [instant] and
    ; [scribe/] import these.
    [config/instant]
    ; The address the backend listens on.
    instant-host=localhost
    ; The port the backend listens on.
    instant-port=8080
    ; Additional options for the backend.
    instant-options=
    ; Batch size for room log delivery.
    scribe-maxlen=100
    ; Additional options for Scribe bots.
    scribe-options=

    ; Master process configuration.
    [master]
    ; Uncomment the following line to use a central manager process and to
    ; make bg-restart work.
    ;mode=action
    ; Location of the "main" directory relative to the directory containing
    ; the configuration file (which, in turn, is expected to be located at
    ; config/run.ini). All other paths (except process command names) are
    ; relative to this.
    work-dir=..
    ; Logging configuration.
    log-file=log/master.log
    ; Let the master process write a PID file.
    pid-file=run/master.pid

    ; Meta-configuration.
    [meta]
    ; Selection of sections that define processes.
    proc-prefixes=instant scribe

    ; Backend configuration.
    [instant]
    __import__=config/instant
    ; Process name.
    name=instant
    ; Command line.
    cmdline=java -jar Instant.jar -h ${instant-host} ${instant-port}
        --http-log=log/Instant.log ${instant-options}
    ; Environment (sets the cookie key location). After the cookie key file is
    ; created, its access mode should be manually set to 600.
    env=INSTANT_COOKIES_KEYFILE=config/cookie-key.bin
        INSTANT_COOKIES_KEYFILE_CREATE=yes
    ; Redirections.
    stdout=>>log/Instant.dbg.log
    stderr=stdout
    ; Fast restart support.
    startup-notify=--startup-cmd

    ; The section name ends with a slash, so no actual process is instantiated
    ; from it.
    [scribe/]
    __import__=config/instant
    ; The (final part of the) section name is used as the room name.
    room=${__name__}
    ; Process name.
    name=scribe-${room}
    ; Command line.
    cmdline=script/scribe.py --cookies config/${name}-cookies.txt
        --msgdb db/messages-${room}.sqlite --maxlen "${scribe-maxlen}"
        ${scribe-options} ws://${instant-host}:${instant-port}/room/${room}/ws
    ; Environment (needed to accept Secure cookies over HTTP).
    env=INSTABOT_RELAXED_COOKIES=y
    ; Redirections.
    stdout=>>log/${name}.log
    stderr=>>log/${name}.err.log

    ; This section creates a Scribe bot in &welcome; it does not need to
    ; contain values of its own.
    [scribe/welcome]

## Running Scribe

There is a bot shipped alongside Instant, *Scribe*, which stores room logs
independently of browser-based clients; it is found in the `script/`
subdirectory of the repository.

It depends on a recent version of *Python* (Python 2.7 is confirmed to work,
and Python 3.4 was extensively tested “in the field”); aside from that,
the `instabot` library found alongside the source file, and a WebSocket
client library (i.e., most aptly called,
[`websocket_server`](https://github.com/CylonicRaider/websocket-server/)) are
required.

Scribe supports the following features, each controlled by a command-line
option. (Refer to the `--help` message for a listing.)

- `--maxlen` *num* — *Maximum log chunk length*: As default (and as the
  browser-based client does), Scribe delivers the entirety of its log
  database if asked to (and the Web client does that); this option caps the
  length of a single log response to the given length to allow incremental
  loading.

    If logs are stored in memory, their amount is additionally capped to the
    given length to avoid memory leaks in long-running instances.

- `--msgdb` *file* — *Message database*: Stores messages in an SQLite
  database instead of RAM, allowing for potentially unbounded storage.

    Use `:memory:` to create an in-memory message database that does not
    discard messages if there are more than *maxlen* ones.

- `--no-msgdb` — *No message database*: Does not store messages at all.

- `--read-file` *file* — *Scrape messages from logfile*: Scribe formats its
  logfile in a machine-readable way, and indeed supports restoring messages
  from it. Since this may be time-consuming, users are encouraged to use
  `--msgdb` instead.

    Ancient versions (without `--msgdb`) used this to persist messages across
    restarts; the name of the bot is derived from the very first version only
    noting down messages (which the second version would deliver to clients).

- `--read-rotate` *pattern* — *Read-back file rotation*: Makes Scribe assume
  that the `--read-file` file has been subject to log rotation (see
  `--logrotate`), and extract historical messages from rotated-out versions of
  the file.

- `--push-logs` *client-ID* — *Push logs*: Can be used to (crudely) transfer
  logs between instances. When specified, Scribe pushes all of its logs to
  the participant with the specified ID unconditionally after initializing
  the message database and reading logfiles (if any). Because of the
  asynchronous nature of the protocol (and the peer-to-peer-based log
  system), clients happily accept any piece of logs offered to them, whether
  it was requested or not. This option may be repeated.

- `--dont-stay` — *Do not remain in room*: Lets Scribe exit once it has
  finished updating its message database. Can be useful to grab a snapshot of
  the logs in one room for, e.g., transferring them somewhere else.

- `--dont-pull` — *Do not pull logs*: Prevents Scribe from fetching messages
  from the room as it would normally do.

    If used in conjunction with `--dont-stay`, this will let the current
    instance exit as soon as another instance indicates it is done with
    updating its logs. Intended to provide short-term coverage when the
    “main” instance of Scribe is being restarted.

- `--nick` *name* — *Nickname*: Allows setting a custom nickname for the
  bot. An empty nickname will make the bot invisible to users, although it
  will still respond with the empty nickname upon request and contribute to
  the user count.

- `--no-nick` — *No nickname*: Disables sending of a nickname altogether;
  this renders the bot truly invisible (from the user list's perspective).

- `--cookies` *file* — *Cookie file*: Stores cookies in the given file. Allows
  the bot to maintain a consistent identity across reconnects. If the empty
  string is passed as the file, cookies will be recorded in memory only and
  forgotten when the bot exits.

- `--no-cookies` — *No cookies*: Disables the storage of cookies (as is the
  default).

- `--tls` *params* — *TLS configuration*: A comma-separated list of
  `<KEY>=<VALUE>` pairs providing TLS configuration (if a `wss` URL is used).
  See [the *HTTPS* section](#https) for details.

- `--logfile` *file* — *Logging file*: A file to write logs to. If this is
  `-`, messages are written to standard output (as is the default).

- `--logrotate` *pattern* — *Logfile rotation*: Lets Scribe regularly stow
  away old logs and optionally compress them.

    The *pattern* consists of a *period*, optionally followed by a
    *compression name*. The period is one of `X` (never), `Y` (yearly),
    `M` (monthly), `D` (daily), `H` (hourly); the logfile will be rotated at
    the beginning of each corresponding interval (_e.g._, at midnight for
    `D`). The compression specification is one of `none`, `gz`, `bz2`, or
    `lzma`, and specifies that rotated-out logfiles be compressed using the
    named algorithm.

    For example, the rotation pattern `D:gz` rotates logs daily and
    GZip-compresses rotated-out logfiles.

- *url* — *WebSocket URL to connect to*: The single optional positional
  argument specifies (indeed) where to connect to. It is the resource `ws`
  relative to the (slash-terminated) room URL to use, with a `ws` or `wss`
  scheme; for example, to connect to the upstream *&test*
  (<https://instant.leet.nu/room/test/>), one would specify
  `wss://instant.leet.nu/room/test/ws`.

    If *url* is not specified, the bot exits after initializing the message
    database and reading logfiles without connecting to any server; this can
    be used to “convert” log files to databases without the need to run a
    backend.

**TL;DR**: Installing the `websocket_server` library mentioned in the second
paragraph and running

    script/scribe.py --msgdb messages.sqlite --maxlen 100 \
        ws://localhost:8080/room/test/ws

should start a Scribe instance in the *&test* room on a local backend.
