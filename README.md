# Instant backend dependency sources

This branch contains (patched) copies of the source code of the dependencies
of the backend of Instant.

## Managing the dependencies

Retrieving and building the dependencies is automated via the `Makefile`
adjacent to this file; adding/removing dependencies and selecting their
versions must be done manually. The processes for storing the changes in VCS
and for integrating them into the master branch are described in the
correspondingly named subsections.

### Adding dependencies

In order to add a dependency `<NAME>`, create a manifest file for it at
`deps/<NAME>/MANIFEST` (see the pertinent section below for details), leaving
the `commit` field set to a dummy value, and fetch and build the dependency
using `make update-<NAME>` (which is an alias for
`make fetch-<NAME> build-<NAME>`). If multiple dependencies are to be added,
the corresponding command lines should be interleaved into
`make fetch-<NAME1> fetch-<NAME2> ... build-<NAME1> build-<NAME2> ...`.

### Removing dependencies

In order to remove the dependency `<NAME>`, delete its directory inside `deps`
as well its patch file inside `deps-patches/` (if any), and perform a full
rebuild by running `make build`. If multiple dependencies are to be removed,
only one full build need be done.

### Changing dependency versions

In order to change the version of dependency `<NAME>`, update its manifest
file at `deps/<NAME>/MANIFEST` to refer to the new version, and refresh the
dependency using `make update-<NAME>`. If multiple dependencies are being
updated, performing a full fetch-and-build cycle via `make update` may be
advisable (_e.g._ to catch incompatibilities).

### Fixating changes

After the dependencies have been retrieved and built without errors or
anomalies, their sources and the compiled files are committed into VCS
similarly to older commits on this branch.

### Integrating changes

After a new set of dependencies is retrieved, built, and committed, it is
integrated into Instant's master branch as follows:

  - On `master`, delete the files belonging to the old dependencies.
  - Transfer the files and directories from `src/` on this branch into `src/`
    on `master`.
  - If the dependencies include Java class files inside `src/net/` (or
    directly inside `src/`), adjust the `Makefile` and `.gitignore` _etc._
    files to deal with that.
  - Verify that the results build and lint correctly (using
    `make pre-commit`), and perform additional correctness _etc._ tests as
    appropriate.
  - Commit the results similarly to previous dependency commits.

## Automation reference

As mentioned above, the `Makefile` automates retrieving and building
dependencies. To do that, it reads `MANIFEST` files from immediate
subdirectories of `deps/`, actually retrieves the dependencies according to
the manifests, applies patches from `deps-patches/`, builds the dependencies,
and stores the resulting files in `src/`.

### Manifests

Each dependency is defined by a file located at `deps/<NAME>/MANIFEST`, where
`<NAME>` is a codename for the dependency, which must match the regular
expression `[a-zA-Z0-9-]+`. The manifest itself is a shell script fragment
that contains variable definitions, with the additional constraints that each
variable must be defined on an individual line, may not span multiple lines,
and the lines may not contain extraneous whitespace.

The manifest defines the following variables (for optional variables, the
default value is the empty string):

  - `url` *(required)*: The URL of a Git repository hosting the source code of
    the dependency. The repository is cloned temporarily but never checked
    out and deleted upon completion of dependency retrieval.

  - `ref` *(optional)*: The name of a tag or branch within the repository at
    `url` specifying which version of the code to retrieve. If not given, the
    `HEAD` of the original repository is used.

  - `commit` *(required)*: The exact commit the local copy of the source code
    was constructed from. *This variable is edited by the build script
    in-place*, and may have a dummy value for a not-retrieved-yet dependency.

  - `extract` *(optional)*: A whitespace-delimited list of files/directories
    to extract from the repository into the dependency's directory (where the
    manifest resides).

    Each entry has either the form `<PREFIX>:<NAME>` or `<NAME>`, where no
    field may contain colons (but may be empty if the first variant is used).
    Each entry transfers the file or directory located at `<PREFIX>/<NAME>`
    inside the repository to the path `<NAME>` inside the dependency's
    directory.

    Directories are extracted recursively. The order of extraction entries is
    significant, with files from later entries replacing those from earlier
    ones. An empty list is equivalent to one with a single `:` entry, which
    extracts the entire repository.

  - `build_depends` *(optional)*: A whitespace-delimited list of names of
    dependencies that should be built before this one. Java source files
    inside this dependency may reference classes from the listed dependencies.

  - `install` *(optional)*: A whitespace-delimited list of auxiliary
    files/directories to copy into the `src/` directory.

    Each entry is either an `<SRC>:<DEST>` pair or a mere `<SRC>`, with
    `<DEST>` defaulting to `<SRC>` if empty or omitted. No field may contain
    colons. `<SRC>` is interpreted relative to the dependency's directory,
    `<DEST>` is interpreted relative to the `src/` directory.

    The order of the entries is significant, with files from later entries
    overriding those from earlier ones. While technically optional, this
    variable should typically at least account for transferring the
    dependency's license to a file called `LICENSE` located adjacent to the
    installed manifest (see `install_manifest`).

  - `install_manifest` *(required)*: The name of a directory relative to
    `src/` whither a redacted copy of the manifest is installed.

    The manifest is installed into a file called `MANIFEST` inside the
    specified directory; it only includes the lines from the original
    manifest defining the `url`, `ref`, and `commit` variables.

### Retrieval, extraction, and patching

By invoking `make fetch`, a round of retrieving and patching the dependency
sources is started. To fetch an individual dependency, run `make fetch-<NAME>`
(where `<NAME>` is the codename of the dependency).

The dependencies are retrieved and extracted as described in the definitions
of the variables `url`, `ref`, `commit`, and `extract` above. For a dependency
called `<NAME>`, after retrieval proper, the build script checks whether there
is a file at `deps-patches/<NAME>.patch` in this repository, and, if so,
applies the file inside the dependency's directory.

### Building

After updating the dependencies' sources, the dependencies can be built by
invoking `make build`; similarily to fetching, `make build-<NAME>` can be used
to build only the dependency `<NAME>`; dissimilarly, this also builds all
dependencies `<NAME>` "depends" on (see `build_depends`) before actually
building `<NAME>`.

The behavior differs slightly between "full" builds and those of individual
dependencies: In the beginning of a full build, the `src/` directory is
cleared, which does not happen for individual builds. The order of building is
not specified except as described in the definition of the `build_depends`
variable above (a dependency is not built before any of its "dependencies" is
finished).

As part of the build, Java class file derived from the dependencies' sources
are stored in the `src/` diretory (in respectively appropriate
subdirectories); additionally, auxiliary files and a redacted copy of the
dependency's manifest are stored as described in the definitions of the
variables `install` and `install_manifest` above.

### All together

Running `make update` is equivalent to running `make fetch build`; this Make
target is mentioned here for completeness and is the default if no targets
are specified (_e.g._ when invoking a bare `make`).
