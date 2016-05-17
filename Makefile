
SOURCES = $(shell find src/ -name '*.java')
ASSETS = $(shell find src/static/ src/pages/)

.PHONY: build infuse-commit clean

Instant.jar: $(SOURCES) $(ASSETS)
	cd src && javac $(patsubst src/%,%,$(SOURCES))
	cd src && jar cfe ../Instant.jar Main *

infuse-commit: Instant.jar
	(printf "X-Git-Commit: "; git rev-parse HEAD) > .git-commit
	jar ufm Instant.jar .git-commit
	rm .git-commit

clean:
	rm -f Instant.jar

run: infuse-commit
	java -jar Instant.jar 8080
