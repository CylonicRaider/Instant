
JAVACFLAGS = -Xlint:all -Xlint:-serial -Werror

SOURCES = $(shell find src/ -name '*.java' 2>/dev/null)
LIBRARIES = $(shell find src/org/ 2>/dev/null)
ASSETS = $(shell find src/static/ src/pages/ 2>/dev/null)
# Specifying those explicitly as they might be absent, and then not listed
# as dependencies.
AUTOASSETS = src/static/logo-static.svg src/static/logo-static_32x32.png \
    src/static/logo-static_128x128.png src/static/logo-static_128x128.ico

_ALL_SOURCES = $(SOURCES) $(shell find tools/ -name '*.java' 2>/dev/null)
_JAVA_SOURCES = $(patsubst src/%,%,$(SOURCES))

.NOTPARALLEL:
.PHONY: default all clean lint lint-ro run _lint-changed pre-commit

default: Instant.jar
all: Instant.jar

Instant.jar: .build.jar $(LIBRARIES) $(ASSETS) $(AUTOASSETS)
	cp .build.jar Instant.jar
	cd src && jar uf ../Instant.jar *

# Avoid recompiling the backend on frontend changes.
.SECONDARY: .build.jar
.build.jar: $(SOURCES)
ifeq ($(strip $(MAKE_NO_PARTIAL_BUILDS)),)
	@cd src && javac $(JAVACFLAGS) $$(../script/importlint.py --no-warn \
	--deps $(_JAVA_SOURCES) | ../script/jbuildcheck.py --report \
	--cleanup --cleandir net/)
else
	find src/net/ -name '*.class' -exec rm {} +
	cd src && javac $(JAVACFLAGS) $(_JAVA_SOURCES)
endif
	cd src && jar cfe ../.build.jar Main $$(find . -name '*.class')

Instant-run.jar: Instant.jar tools/amend-manifest.jar
	java -jar tools/amend-manifest.jar Instant.jar X-Git-Commit \
	$$(git rev-parse HEAD) Instant-run.jar

config:
	mkdir -p $@

config/cookie-key.bin: | config
	head -c64 /dev/random > config/cookie-key.bin
	chmod 0600 config/cookie-key.bin

clean:
	rm -f .build.jar Instant.jar Instant-run.jar

lint:
	script/importlint.py --sort --prune --empty-lines $(_ALL_SOURCES)
lint-ro:
	script/importlint.py $(_ALL_SOURCES)

run: Instant-run.jar config/cookie-key.bin
	cd src && INSTANT_HTTP_MAXCACHEAGE=10 \
	INSTANT_COOKIES_INSECURE=yes \
	INSTANT_COOKIES_KEYFILE=../config/cookie-key.bin \
	java -jar ../Instant-run.jar

src/static/logo-static.svg: src/static/logo.svg
	script/deanimate.py $< $@
src/static/logo-static_32x32.png: src/static/logo-static.svg
	convert -background none $< $@
# HACK: Apparently only way to make ImageMagick scale the SVG up.
src/static/logo-static_128x128.png: src/static/logo-static.svg
	convert -background none -density 288 $< $@
src/static/logo-static_128x128.ico: src/static/logo-static.svg
	convert -background none -density 288 $< $@

_lint-changed:
	@script/importlint.py --sort --prune --empty-lines --report-null \
	$$(git diff --cached --name-only --diff-filter d | grep '\.java$$') \
	| xargs -r0 git add

pre-commit: _lint-changed all
	@git add Instant.jar

include tools/build.mk
