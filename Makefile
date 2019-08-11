
DEPS_NAMES = $(patsubst deps/%,%,$(wildcard deps/*))
DEPS_MANIFESTS = $(patsubst %,deps/%/MANIFEST,$(DEPS_NAMES))

_DEPS_CLEANS = $(patsubst %,clean-%,$(DEPS_NAMES))
_DEPS_FETCHES = $(patsubst %,fetch-%,$(DEPS_NAMES))
_DEPS_BUILDS = $(patsubst %,build-%,$(DEPS_NAMES))

.PHONY: update fetch build pre-build $(_DEPS_CLEANS) $(_DEPS_FETCHES) \
    $(_DEPS_BUILDS)
.NOTPARALLEL:

update: fetch build

fetch: $(_DEPS_FETCHES)
build: pre-build $(_DEPS_BUILDS)

$(_DEPS_CLEANS): clean-%:
	cd deps/$* && find . -mindepth 1 -maxdepth 1 ! -name MANIFEST \
	-exec rm -rf '{}' +

$(_DEPS_FETCHES): fetch-%: deps/%/MANIFEST clean-%
	@echo "Fetching dependency $* ..."
	./build.sh fetch $*
	@echo "Fetching dependency $* done."

pre-build:
	find src -mindepth 1 -maxdepth 1 -exec rm -rf '{}' +

$(_DEPS_BUILDS): build-%: deps/%/MANIFEST | src
	@echo "Building dependency $* ..."
	./build.sh build $*
	@echo "Building dependency $* done."

.deps.mk: $(DEPS_MANIFESTS)
	@set -e; exec >$@; for name in $(DEPS_NAMES); do build_depends=""; \
	. deps/$$name/MANIFEST; [ -z "$$build_depends" ] && continue; \
	echo "build-$$name: $$(echo "$$build_depends" | \
	sed -re 's/[a-zA-Z0-9-]+/build-&/g')"; done

-include .deps.mk
