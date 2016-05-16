
SOURCES = $(shell find src/ -name '*.java')
ASSETS = $(shell find src/static/ src/pages/)

.PHONY: build clean

Instant.jar: $(SOURCES) $(ASSETS)
	cd src && javac $(patsubst src/%,%,$(SOURCES))
	cd src && jar cfe ../Instant.jar Main *

clean:
	rm -f Instant.jar

run: Instant.jar
	java -jar Instant.jar 8080
