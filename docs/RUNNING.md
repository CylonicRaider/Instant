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
