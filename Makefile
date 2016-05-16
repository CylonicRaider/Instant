
SOURCES = $(shell find src/ -name '*.java')

Instant.jar: $(SOURCES)
	cd src && javac $(patsubst src/%,%,$(SOURCES))
	cd src && jar cfe ../Instant.jar Main *
