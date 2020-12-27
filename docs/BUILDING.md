# Building Instant

This document (chiefly) describes how to build the Instant backend.

## Dependencies

A properly working installation of a *Java Development Kit* (Java 7 at least)
is assumed.

The backend has no external dependencies (only the Java standard library);
additional dependencies are included along with the source code and are
bundled into the backend automatically.

**Note**: Backends *before version 1.5.3* depended on the *Java Architecture
for XML Binding* (*JAXB*), which was provided as part of the Java standard
library up to Java 8; when building older backends on newer Javas, an
implementation of JAXB (or at least `javax.xml.bind.DatatypeConverter`) must
be included externally (or the pertinent transition mechanisms of Java 9 and
10 may be used when available). This dependency also applies at runtime, but
is only mentioned here to reduce redundancy.

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
but for the debugging of code that depends on it or for proper support of
client updates.

**Note** that `make` also automatically generates bitmap icons from the SVG's
provided in `src/static/` (should it consider that needed). It assumes that
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
