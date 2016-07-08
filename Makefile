
SOURCES = $(shell find src/ -name '*.java' 2>/dev/null)
LIBRARIES = $(shell find src/org/ 2>/dev/null)
ASSETS = $(shell find src/static/ src/pages/ 2>/dev/null)

_JAVA_SOURCES = $(patsubst src/%,%,$(SOURCES))

.PHONY: clean run

Instant.jar: .build.jar $(LIBRARIES) $(ASSETS)
	cp .build.jar Instant.jar
	cd src && jar uf ../Instant.jar *

.INTERMEDIATE: .build.jar
.SECONDARY: .build.jar
.build.jar: $(SOURCES)
	cd src && javac $(_JAVA_SOURCES)
	cd src && jar cfe ../.build.jar Main $(_JAVA_SOURCES) \
	$(patsubst %.java,%.class,$(_JAVA_SOURCES))

Instant-run.jar: Instant.jar
	cp Instant.jar Instant-run.jar
	(printf "X-Git-Commit: "; git rev-parse HEAD) > .git-commit
	jar ufm Instant-run.jar .git-commit
	rm .git-commit

cookie-key.bin:
	head -c64 /dev/random > cookie-key.bin

clean:
	rm -f .build.jar Instant.jar Instant-run.jar

run: Instant-run.jar cookie-key.bin
	cd src && INSTANT_COOKIES_INSECURE=yes \
	INSTANT_COOKIES_KEYFILE=../cookie-key.bin \
	java -jar ../Instant-run.jar 8080
