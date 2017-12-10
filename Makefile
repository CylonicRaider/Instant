
JAVACFLAGS = -Xlint:all -Xlint:-serial -Werror

SOURCES = $(shell find src/ -name '*.java' 2>/dev/null)
LIBRARIES = $(shell find src/org/ 2>/dev/null)
ASSETS = $(shell find src/static/ src/pages/ 2>/dev/null)
# Specifying those explicitly as they might be absent, and then not listed
# as dependencies.
AUTOASSETS = src/static/logo-static.svg src/static/logo-static_32x32.png \
    src/static/logo-static_128x128.png src/static/logo-static_128x128.ico

_JAVA_SOURCES = $(patsubst src/%,%,$(SOURCES))

.NOTPARALLEL:
.PHONY: clean lint lint-ro run pre-commit

Instant.jar: .build.jar $(LIBRARIES) $(ASSETS) $(AUTOASSETS)
	cp .build.jar Instant.jar
	cd src && jar uf ../Instant.jar *

# Avoid recompiling the backend on frontend changes.
.INTERMEDIATE: .build.jar
.SECONDARY: .build.jar
.build.jar: $(SOURCES)
	find src/net/ -name '*.class' -exec rm {} +
	cd src && javac $(JAVACFLAGS) $(_JAVA_SOURCES)
	cd src && jar cfe ../.build.jar Main $$(find . -name '*.class')

Instant-run.jar: Instant.jar
	cp Instant.jar Instant-run.jar
	(printf "X-Git-Commit: "; git rev-parse HEAD) > .git-commit
	jar ufm Instant-run.jar .git-commit
	rm .git-commit

cookie-key.bin:
	head -c64 /dev/random > cookie-key.bin
	chmod 0600 cookie-key.bin

clean:
	rm -f .build.jar Instant.jar Instant-run.jar

lint:
	script/importlint.py --sort --prune --empty-lines $(SOURCES)
lint-ro:
	script/importlint.py $(SOURCES)

run: Instant-run.jar cookie-key.bin
	cd src && INSTANT_COOKIES_INSECURE=yes \
	INSTANT_COOKIES_KEYFILE=../cookie-key.bin \
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

pre-commit: _lint-changed Instant.jar
	@git add Instant.jar
