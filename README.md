# Instant backend dependency sources

This branch contains (patched) copies of the source code of the dependencies
of the backend of Instant.

## Managing the dependencies

Retrieving and building the dependencies is automated via the `Makefile`
adjacent to this file. The `Makefile` reads `MANIFEST` files from immediate
subdirectories of `deps/`, retrieves the dependencies according to them,
applies patches from `deps-patches/`, builds the dependencies, and stores the
resulting files in `src/`.

Adding/removing dependencies and changing their versions, as well as
integrating them into the master branch is done manually.

### Manifests

Each dependency is defined by a file located at `deps/<NAME>/MANIFEST`, where
`<NAME>` is a codename for the dependency. The name must match the regular
expression `[a-zA-Z0-9-]+`. The manifest itself is a shell script fragment
that contains variable definitions, with the additional constraint that each
variable must be defined on an individual line, may not span multiple lines,
and the lines may not contain extraneous whitespace.

The manifest defines the following variables (for optional variables, the
default value is the empty string):

  - `url` *(required)*: The URL of a Git repository hosting the source code of
    the dependency. The repository is cloned temporarily but never checked
    out and deleted upon completion of dependency retrieval.

  - `ref` *(optional)*: The name of a tag or branch within the repository at
    `url` specifying which version of the code to retrieve. If not specified,
    the `HEAD` of the original repository is used.

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

  - `install_manifest` *(required)*: The name of a directory inside `src/`
    whither a redacted copy of the manifest is installed.

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
to build only the dependency `<NAME>`, along with and after all dependencies
it depends on.

The behavior differs slightly between "full" builds and those of individual
dependencies: In the beginning of a full build, the `src/` directory is
cleared. The order of building is not specified except as described in the
definition of the `build_depends` variable above (a dependency is built not
before any of its "dependencies" is finished).

As part of the build, Java class file derived from the dependencies' sources
are stored in the `src/` diretory (in respectively appropriate
subdirectories); additionally, auxiliary files and a redacted copy of the
dependency's manifest are stored as described in the definitions of the
variables `install` and `install_manifest` above.

### Fixating

After the dependencies have been retrieved and built without errors or
anomalies, their sources and the compiled files are committed into VCS
similarly to older commits on this branch.

### Integrating

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

### Adding and removing dependencies

In order to add a dependency, create a manifest file for it (see the pertinent
section above), leaving the `commit` field set to a dummy value, and fetch and
build the dependency as described above.

In order to remove a dependency, delete its subdirectory in `deps/` as well
as its patch file in `deps-patches/`, and perform a full rebuild (using
`make build`).

In either case, after the dependencies have been built, the steps **Fixating**
and **Integrating** from above apply.
