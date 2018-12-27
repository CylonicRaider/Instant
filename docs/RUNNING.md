# Running Instant

This document describes how to compile, configure, and run the Instant
backend and/or the bot shipped alongside it.

## Running the backend

A properly working installation of *Java* (Java 7 at least) is assumed.

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
  letting it shut down the "old" one before taking over.

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

### Orchestrator script

On UNIX platforms, a powerful (and extremely overengineered) Python program
for automating the management of an Instant backend and any amount of bots
(and any other processes as well) is available at `script/run.py`.

`run.py` aims to be usable as an init script; however, it includes some
relative file paths, which might require setting a particular working
directory (or patching the initial sections of the script).

On every invocation, a subcommand must be specified that defines what action
should be performed. A full listing can be obtained by invoking the script
with a `--help` option. Unless otherwise noted, each subcommand accepts a list
of process names (as defined in the configuration file; see below) to act
upon as positional arguments.

- `status`: Lists processes and displays whether they are running or not.
- `start`: Launches those processes that are not running.
- `stop`: Terminates those processes that are running.
- `restart`: Terminates and re-starts processes.
- `bg-restart`: Performs a "fast restart": In the background, new instances
  of those processes that support it are pre-loaded, then the "old" instances
  of (all named) processes are stopped, and finally the background processes
  are swapped in (or new instances are started). Requires a master process
  (see below) to be effective; falls back to an equivalent of `restart`
  otherwise.

**TL;DR**: Skip to the example subsection below, copy the configuration file
to `config/instant.ini` in the Instant directory, modify it to your needs, and
use `script/run.py COMMAND` for `COMMAND`-s listed just above.

#### Master process mode

In this mode of operation, a central background instance of the orchestrator
script — the *master process* — performs the actual process management and the
actions are submitted to it via IPC.

Aside from running a master process in the foreground using the `run-master`
subcommand, the actions listed above can be instructed to interact with a
master process in various ways using the `--master` command-line switch (which
must be specified before the subcommand); its possible values are listed
below:

- `off` — *Never use master*. This may interfere with an already-running
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
- `on` — *Always use master*: If no master process is running, this exits with
  an error message.
- `restart` — *Restart master*: If no master process is running, spawns a new
  one in the background; if there *is* one, it is restarted in-place. In
  either case, the configuration file is (re-)read and the action is performed
  by a master process. Useful together with `restart` or `bg-restart` for
  performing live updates.
- `stop` — *Shut down master*: If no master process is running, executes the
  action locally; if there *is* a master process, it executes the action and
  is shut down afterwards.

#### Configuration file format

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

**Inheritance**: A section *inherits* the values of its "parent" section (if
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

#### Configuration files

**Individual processes** are defined by non-template sections whose names up
to the first slash (`/`; if any) are `instant`, `bot`, or `proc`. The
following keys configure the process:

- `name` — *Process name*: The name of the process used on the command line.
  Must be unique and not contain equals signs (`=`). Required.
- `cmdline` — *Command line*: Used to invoke the process. Parsed using
  shell-like syntax (as implemented by the `shlex` Python module); that
  happens **after** interpolation has been performed on the value. Required.
- `env` — *Environment variables*: The value is, similarly to `cmdline`, split
  into individual "words" using shell-like syntax; those are then split at
  the first occurrences of equals signs (`=`; each word must contain at least
  one) and added to a new process' environment. **Remark** that the syntax is
  more lax than an actual shell's: `"abc=def"` is valid inside `env`, but
  would not be a valid variable assignment in a shell.
- `work-dir` — *Working directory*: A path of a directory to invoke the
  process in (if the command name in `cmdline` is a relative path, it is
  interpreted relative to this directory).
- `stdin` — *Standard input redirection*: If specified, standard input of the
  process is redirected according to this; see below for possible values.
- `stdout` — *Standard output redirection*: Like `stdin`, but for standard
  output.
- `stderr` — *Standard error redirection*: Like `stdin`, but for standard
  error.

The following keys configure the orchestrator's handling of the process:

- `mkdirs` — *Create directories*: A list — similarly to `end` — of
  directories to create before starting the process. Parent directories are
  created as necessary.
- `pid-file` — *PID file*: The path of a file to write the PID of the process
  to. Used in standalone mode to locate the process. A default value (which
  includes the process name) is provided.
- `pid-file-warmup` — *Secondary PID file*: During a "fast restart", if
  supported by the process, write the PID of its background copy here.
  Defaults to `pid-file` with a `.new` suffix appended.
- `startup-notify` — *Fast restart support*: Setting this key enables "fast
  restart" support for this process. When performing a fast restart, the value
  of this key is appended to the process' command line (as a single word),
  followed by a shell command (as a single word which is internally delimited
  by spaces) to invoke (and wait for) when the process is done initializing.
  The command communicates with the master process (which must be present for
  fast restarts to be performed) and exits when it is time for the background
  copy of the process to be swapped into the foreground.
- `stop-wait` — *Slow shutdown support*: When stopping the process and if the
  orchestrator cannot wait for the process to finish (e.g. when not using a
  master process), this specifies a fixed delay (in seconds) to be applied.
  This is usedful when the process holds some exclusive resource (such as a
  bound network socket) and might take some time to release it.

**Redirections** are specified in a way similar to shell redirections, with
some special values:

- *(empty string)*: No redirection.
- `devnull`: Opens `/dev/null` for reading/writing.
- `stdout`: (Only valid when redirecting `stderr`.) Make standard error point
  whereever standard output goes.
- `<PATH`: Open `PATH` for reading.
- `>PATH`: Open `PATH` for writing (creating a new file or truncating an
  existing file).
- `>>PATH`: Open `PATH` for appending (creating a new file if necessary).
- `<>PATH`: Open `PATH` for reading and writing.
- All other values result in errors.

**The master process** is configured using the `master` section, in which the
following keys are significant:

- `mode` — *Master process spawning mode*: This provides a default value for
  the `--master` option for invocations of the orchestrator.
- `path` — *Socket path*: Where the UNIX domain socket used for communication
  with the master process is located.
- `pid-file` — *Master PID file*: Where the master process should write a PID
  file representing itself.
- `log-file` — *Path of logging file*: Where the master process should send
  logs. If not given or empty, standard error is used.
- `log-level` — *Logging level*: Messages at least severe as this level will
  be logged. Defaults to `INFO`.
- `log-timestamps` — *Timestamps in logs*: Whether logging messages should
  include timestamps. Defaults to `yes`. Disabling this might be useful when
  logging to `systemd`.

**Note** that all filesystem paths (with the exception of those in `cmdline`)
are relative to the *orchestrator*'s working directory. For PID files,
redirections, and the master process' log file, intermediate directories are
created as needed.

#### Example configuration

The following configuration file demonstrates the techniques documented above
and provides an approximate replacement for the `run.bash` script:

    ; General parameters stored in a separate section; [instant] and
    ; [bot/scribe/] import these.
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
    ;mode=spawn
    ; Logging configuration.
    log-file=log/master.log
    ; Let the master process write a PID file.
    pid-file=run/master.pid

    ; Backend configuration.
    [instant]
    __import__=config/instant
    ; Process name.
    name=instant
    ; Command line.
    cmdline=java -jar Instant.jar -h ${instant-host} ${instant-port}
        --http-log=log/Instant.log ${instant-options}
    ; Environment (sets the cookie key location).
    env=INSTANT_COOKIES_KEYFILE=config/cookie-key.bin
    ; Redirections.
    stdout=>>log/Instant.dbg.log
    stderr=stdout
    ; Fast restart support.
    startup-notify=--startup-cmd

    ; The section name ends with a slash, so no actual process is instantiated
    ; from it.
    [bot/scribe/]
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

    ; This section screates a Scribe bot in &welcome; it does not need to
    ; contain values of its own.
    [bot/scribe/welcome]

### HTTPS

The backend supports plain HTTP exclusively; to provide HTTPS, you have to
use a reverse proxy.

## Building the backend

A pre-built backend should be provided as `Instant.jar` with every commit; if
you do not need to run modified versions of the backend, using the pre-built
one should be fine (**note**, however, that it does not include commit
information).

### UNIX-like OS-es

For re-compiling the backend, a simple `make` suffices. Note that, since Git
does (intentionally) not preserve file timestamps, you may have to use
`make -B`.

For assembling a backend with correct current commit information, you can use
`make Instant-run.jar`; to run it, launch `make run`, which takes care of all
that — including generating a key for cookie signing —, and spawns an
instance on the default port. Embedding the commit number is not necessary
but for debugging code that depends on it or for proper support of client
updates.

**Note** that `make` also automatically generates bitmap icons from the SVG's
provided in `src/static/` (should it regard that as needed). It assumes that
*ImageMagick* is available for that; if you are unable to install the latter,
you may comment the section out.

**TL;DR**: Install GNU Make and run `make run` to (re-)compile and run a
properly configured backend on port 8080.

### Windows

If you have a sufficiently sophisticated UNIX compatibility layer (like
Cygwin) installed, the instructions above apply.

For compiling the backend manually, you can perform the following steps
(as necessary); **note** that the result files are outside the source
tree to avoid polluting it.

1. Compile all backend source files in `src\`. **Note** that the source
   tree also contains the static frontend files (so naïvely compiling
   every file in the tree will fail).

     Console commands are not provided for this step since it is bound to
     be messy.

2. Assemble the results into a JAR file.

         src\> jar cvfe ..\Instant.jar Main *

3. *(Only for quickly updating frontend files — Steps 1 and 2 are not
   necessary if the backend itself is unchanged:)*

     Update the JAR with the frontend files.

         src\> jar uvf ..\Instant.jar *

4. Embed the current commit into the JAR file if desired. These commands
   produce the file `Instant-run.jar` to avoid polluting `Instant.jar`
   (which may be checked into a repository) with a random commit hash,
   may be omitted if that is not necessary, and are run in the parent
   directory of the source tree.

         > copy Instant.jar Instant-run.jar
         > echo X-Git-Commit: <COMMIT-HASH> > .git-commit
         > jar ufm Instant-run.jar .git-commit

     (Replace `<COMMIT-HASH>` with the hash of the current commit.)

     Embedding the commit number is not necessary but for debugging code that
     depends on it or for proper support of client updates.

5. Run the JAR (replace `Instant-run.jar` with `Instant.jar` if you have
   skipped Step 4):

         > java -jar Instant-run.jar 8080

     Instant is now available under
     [`http://localhost:8080`](http://localhost:8080/).

**TL;DR**: See above for running a stock backend if this is too messy for
you.

### Other automated build environments

To configure the automated build system _du jour_, consider the following
points:

- Source files are provided in the `src/net/instant/` subdirectory, with the
  exception of `src/Main.java` (the latter can be safely skipped if you write
  an own launcher, though).

- Pre-compiled libraries (with custom modifications **(!)**) are stored in
  the `src/org/` subdirectory.

- Static files are located in the `src/pages/` and `src/static/`
  subdirectories; the code expects them to be available as resources in the
  `/pages` and `/static` packages.

- The (default) main class is `Main` in the default package; it is a thin
  wrapper around `net.instant.Main`.

- The code expects (but does not depend on) the manifest variable
  `X-Git-Commit` to store the Git commit of the current build, or some
  alternative fine-grained version indicator. Additional semantics may be
  defined in the future.

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

- `--read-file` *file* — *Scrape messages from logfile*: Scribe formats its
  logfile in a machine-readable way, and indeed supports restoring messages
  from it. Since this may be time-consuming, users are encouraged to use
  `--msgdb` instead. There may be multiple instances of this option.

    Ancient versions (without `--msgdb`) used this to persist messages across
    restarts; the name of the bot is derived from the very first version only
    noting down messages (which the second version would deliver to clients).

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
    "main" instance of Scribe is being restarted.

- `--nick` *name* — *Nickname*: Allows setting a custom nickname for the
  bot. An empty nickname will make the bot invisible to users, although it
  will still respond with the empty nickname upon request and contribute to
  the user count.

- `--no-nick` — *No nickname*: Disables sending of a nickname altogether;
  this renders the bot truly invisible (from the user list's perspective).

- `--cookies` *file*: Stores cookies in the given file. Allows the bot to
  maintain a consistent identity across reconnects. If the empty string is
  passed as the file, cookies will be recorded in memory only and forgotten
  when the bot exits.

- `--no-cookies` — *No cookies*: Disables the storage of cookies (as is the
  default).

- *url* — *WebSocket URL to connect to*: The single optional positional
  argument specifies (indeed) where to connect to. It is the resource `ws`
  relative to the (slash-terminated) room URL to use, with a `ws` or `wss`
  scheme; for example, to connect to the upstream *&test*
  (<https://instant.leet.nu/room/test/>), one would specify
  `wss://instant.leet.nu/room/test/ws`.

    If *url* is not specified, the bot exits after initializing the message
    database and reading logfiles without connecting to any server; this can
    be used to "convert" log files to databases without the need to run a
    backend.
