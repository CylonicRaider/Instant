
SOURCES = $(shell find src/ -name '*.java')

.PHONY: clean

Instant.jar: $(SOURCES)
	cd src && javac $(patsubst src/%,%,$(SOURCES))
	cd src && jar cfe ../Instant.jar Main *

clean:
	rm -f Instant.jar

run: Instant.jar
	java -jar Instant.jar 8080
