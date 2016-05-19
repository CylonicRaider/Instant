
SOURCES = $(shell find src/ -name '*.java')
LIBRARIES = $(shell find src/org/)
ASSETS = $(shell find src/static/ src/pages/)

_JAVA_SOURCES = $(patsubst src/%,%,$(SOURCES))

.PHONY: infuse-commit clean

Instant.jar: .build.jar $(LIBRARIES) $(ASSETS)
	cp .build.jar Instant.jar
	cd src && jar uf ../Instant.jar *

.build.jar: $(SOURCES)
	cd src && javac $(_JAVA_SOURCES)
	cd src && jar cfe ../.build.jar Main $(_JAVA_SOURCES) \
	$(patsubst %.java,%.class,$(_JAVA_SOURCES))

infuse-commit: Instant.jar
	(printf "X-Git-Commit: "; git rev-parse HEAD) > .git-commit
	jar ufm Instant.jar .git-commit
	rm .git-commit

clean:
	rm -f .build.jar Instant.jar

run: infuse-commit
	java -jar Instant.jar 8080
